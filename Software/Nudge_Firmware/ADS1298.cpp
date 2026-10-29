#include "ADS1298.h"
#include "EmgFilter.h"
#include <SPI.h>

namespace ADS1298
{
    namespace
    {
        // Opcodes (datasheet table 15)
        constexpr uint8_t CMD_RESET = 0x06;
        constexpr uint8_t CMD_START = 0x08;
        constexpr uint8_t CMD_STOP = 0x0A;
        constexpr uint8_t CMD_RDATAC = 0x10;
        constexpr uint8_t CMD_SDATAC = 0x11;
        constexpr uint8_t CMD_RREG = 0x20;
        constexpr uint8_t CMD_WREG = 0x40;

        // Registers
        constexpr uint8_t REG_ID = 0x00;
        constexpr uint8_t REG_CONFIG1 = 0x01;
        constexpr uint8_t REG_CONFIG2 = 0x02;
        constexpr uint8_t REG_CONFIG3 = 0x03;
        constexpr uint8_t REG_LOFF = 0x04;
        constexpr uint8_t REG_CH1SET = 0x05;
        constexpr uint8_t REG_RLD_SENSP = 0x0D;
        constexpr uint8_t REG_RLD_SENSN = 0x0E;
        constexpr uint8_t REG_LOFF_SENSP = 0x0F;
        constexpr uint8_t REG_LOFF_SENSN = 0x10;
        constexpr uint8_t REG_GPIO = 0x14;
        constexpr uint8_t REG_CONFIG4 = 0x17;

        // CONFIG3: internal reference buffer on, reserved bit, internal RLD reference (mid-supply),
        // RLD amplifier on. The RLDREF pin is unconnected on the strip, so the internal one is required.
        constexpr uint8_t CONFIG3_VALUE = 0x80 | 0x40 | 0x08 | 0x04;  // 0xCC

        // Timing: tCLK = 1 / 2.048 MHz ~ 0.49 us
        constexpr uint32_t T_DECODE_US = 2;  // 4 tCLK between command bytes, and before CS high

        const SPISettings spiSettings(SPI_HZ, MSBFIRST, SPI_MODE1);  // CPOL 0, CPHA 1

        SemaphoreHandle_t spiLock;
        TaskHandle_t readerTask;
        QueueHandle_t envelopeQueue;
        volatile bool streaming = false;
        InputMode mode = InputMode::Electrodes;
        uint8_t deviceId = 0;
        bool diagnostics = false;

        EmgFilter::Envelope<CHANNELS> envelope;

        // Stats, guarded by statsLock
        portMUX_TYPE statsLock = portMUX_INITIALIZER_UNLOCKED;
        Stats stats;
        float rawMin[CHANNELS], rawMax[CHANNELS], sqSum[CHANNELS];
        uint32_t statsSince = 0;

        void ResetStatsLocked()
        {
            stats = Stats{};
            for (int c = 0; c < CHANNELS; c++)
            {
                rawMin[c] = 1e30f;
                rawMax[c] = -1e30f;
                sqSum[c] = 0;
            }
            statsSince = millis();
        }

        uint8_t GainCode()
        {
            switch (GAIN)
            {
                case 1: return 0b001;
                case 2: return 0b010;
                case 3: return 0b011;
                case 4: return 0b100;
                case 8: return 0b101;
                case 12: return 0b110;
                default: return 0b000;  // 6
            }
        }

        void Select() { digitalWrite(PIN_CS, LOW); }
        void Deselect()
        {
            delayMicroseconds(T_DECODE_US);
            digitalWrite(PIN_CS, HIGH);
            delayMicroseconds(T_DECODE_US);  // CS high at least 2 tCLK
        }

        void Command(uint8_t op)
        {
            SPI.beginTransaction(spiSettings);
            Select();
            SPI.transfer(op);
            Deselect();
            SPI.endTransaction();
        }

        void WriteReg(uint8_t reg, uint8_t value)
        {
            SPI.beginTransaction(spiSettings);
            Select();
            SPI.transfer(CMD_WREG | reg);
            delayMicroseconds(T_DECODE_US);
            SPI.transfer(0x00);  // one register
            delayMicroseconds(T_DECODE_US);
            SPI.transfer(value);
            Deselect();
            SPI.endTransaction();
        }

        uint8_t ReadReg(uint8_t reg)
        {
            SPI.beginTransaction(spiSettings);
            Select();
            SPI.transfer(CMD_RREG | reg);
            delayMicroseconds(T_DECODE_US);
            SPI.transfer(0x00);
            delayMicroseconds(T_DECODE_US);
            uint8_t v = SPI.transfer(0x00);
            Deselect();
            SPI.endTransaction();
            return v;
        }

        uint8_t ChannelSetting() { return (GainCode() << 4) | (uint8_t)mode; }

        /** Writes all channel and signal-path registers. Must be in SDATAC mode. */
        bool Configure()
        {
            uint8_t config1 = SAMPLE_RATE == 500 ? 0x86 : 0x06;  // HR or LP mode, DR = fMOD/1024
            WriteReg(REG_CONFIG1, config1);
            WriteReg(REG_CONFIG2, mode == InputMode::TestSignal ? 0x10 : 0x00);  // internal test signal
            WriteReg(REG_LOFF, 0x00);
            for (int c = 0; c < CHANNELS; c++) WriteReg(REG_CH1SET + c, ChannelSetting());
            // Right-leg drive from both inputs of every channel, out through pad 17
            WriteReg(REG_RLD_SENSP, 0xFF);
            WriteReg(REG_RLD_SENSN, 0xFF);
            WriteReg(REG_LOFF_SENSP, 0x00);
            WriteReg(REG_LOFF_SENSN, 0x00);
            WriteReg(REG_GPIO, 0x00);  // GPIO1-4 are unconnected: drive them low rather than float
            WriteReg(REG_CONFIG4, 0x00);

            bool ok = ReadReg(REG_CONFIG1) == config1 && ReadReg(REG_CH1SET) == ChannelSetting() &&
                      (ReadReg(REG_CONFIG3) & 0xFE) == CONFIG3_VALUE;  // bit 0 is a read-only status
            if (!ok) Serial.println(">> ADS1298: register read-back mismatch, check SPI wiring");
            return ok;
        }

        void StartStreaming()
        {
            if (PIN_START >= 0) digitalWrite(PIN_START, HIGH);
            else Command(CMD_START);
            Command(CMD_RDATAC);
            streaming = true;
        }

        void StopStreaming()
        {
            streaming = false;
            if (PIN_START >= 0) digitalWrite(PIN_START, LOW);
            Command(CMD_SDATAC);
            Command(CMD_STOP);
        }

        void IRAM_ATTR OnDrdy()
        {
            BaseType_t woken = pdFALSE;
            vTaskNotifyGiveFromISR(readerTask, &woken);
            portYIELD_FROM_ISR(woken);
        }

        void ReaderTask(void*)
        {
            uint8_t raw[ADS1298Frame::BYTES];
            float uv[CHANNELS], env[CHANNELS];
            ADS1298Frame::Frame frame;
            for (;;)
            {
                if (ulTaskNotifyTake(pdTRUE, pdMS_TO_TICKS(100)) == 0 || !streaming) continue;

                // Held through filtering too, so a mode change can't reset the filters mid-sample
                xSemaphoreTake(spiLock, portMAX_DELAY);
                SPI.beginTransaction(spiSettings);
                Select();
                memset(raw, 0, sizeof(raw));
                SPI.transferBytes(raw, raw, sizeof(raw));
                Deselect();
                SPI.endTransaction();

                bool good = ADS1298Frame::Decode(raw, frame);
                for (int c = 0; c < CHANNELS; c++) uv[c] = ADS1298Frame::ToMicrovolts(frame.channel[c], VREF, GAIN);
                bool ready = good && envelope.Push(uv, env);
                xSemaphoreGive(spiLock);

                portENTER_CRITICAL(&statsLock);
                stats.frames++;
                if (!good) stats.badFrames++;
                for (int c = 0; good && c < CHANNELS; c++)
                {
                    if (uv[c] < rawMin[c]) rawMin[c] = uv[c];
                    if (uv[c] > rawMax[c]) rawMax[c] = uv[c];
                }
                if (ready) for (int c = 0; c < CHANNELS; c++) sqSum[c] += env[c] * env[c];
                portEXIT_CRITICAL(&statsLock);

                if (ready && xQueueSend(envelopeQueue, env, 0) != pdTRUE)
                {
                    // Nobody is reading (e.g. no app connected): drop the oldest and keep the newest
                    float discard[CHANNELS];
                    xQueueReceive(envelopeQueue, discard, 0);
                    xQueueSend(envelopeQueue, env, 0);
                    portENTER_CRITICAL(&statsLock);
                    stats.dropped++;
                    portEXIT_CRITICAL(&statsLock);
                }
            }
        }
    }

    bool Begin()
    {
        // Datasheet power-up: digital inputs low until the supply is stable
        pinMode(PIN_CS, OUTPUT);
        digitalWrite(PIN_CS, HIGH);
        if (PIN_START >= 0) { pinMode(PIN_START, OUTPUT); digitalWrite(PIN_START, LOW); }
        if (PIN_RESET >= 0) { pinMode(PIN_RESET, OUTPUT); digitalWrite(PIN_RESET, LOW); }
        if (PIN_PWDN >= 0) { pinMode(PIN_PWDN, OUTPUT); digitalWrite(PIN_PWDN, LOW); }
        if (PIN_CLKSEL >= 0) { pinMode(PIN_CLKSEL, OUTPUT); digitalWrite(PIN_CLKSEL, HIGH); }
        pinMode(PIN_DRDY, INPUT_PULLUP);
        SPI.begin(PIN_SCLK, PIN_DOUT, PIN_DIN, -1);

        spiLock = xSemaphoreCreateMutex();
        envelopeQueue = xQueueCreate(16, sizeof(float) * CHANNELS);
        envelope.Init(SAMPLE_RATE, MAINS_HZ);
        portENTER_CRITICAL(&statsLock);
        ResetStatsLocked();
        portEXIT_CRITICAL(&statsLock);

        delay(10);  // oscillator wake-up
        if (PIN_PWDN >= 0) digitalWrite(PIN_PWDN, HIGH);
        if (PIN_RESET >= 0) digitalWrite(PIN_RESET, HIGH);

        // Power-on reset: at least 2^18 tCLK (128 ms) and until VCAP1 has charged past 1.1 V
        delay(500);
        if (PIN_RESET >= 0)
        {
            digitalWrite(PIN_RESET, LOW);
            delayMicroseconds(10);  // at least 2 tCLK
            digitalWrite(PIN_RESET, HIGH);
        }
        else
        {
            Command(CMD_RESET);
        }
        delayMicroseconds(20);  // 18 tCLK before the next command

        // The chip wakes up in continuous-read mode; stop it so registers can be written
        Command(CMD_SDATAC);

        deviceId = ReadReg(REG_ID);
        Serial.printf(">> ADS1298: ID register 0x%02X\n", deviceId);
        if ((deviceId & 0x1F) != 0x12)
        {
            Serial.println(">> ADS1298 not found (expected ID 0x92 or 0xD2). Check power, CS, SCLK, DIN, DOUT.");
            return false;
        }

        WriteReg(REG_CONFIG3, CONFIG3_VALUE);
        delay(150);  // internal reference settles
        bool ok = Configure();

        xTaskCreate(ReaderTask, "ads1298", 4096, nullptr, 5, &readerTask);
        attachInterrupt(digitalPinToInterrupt(PIN_DRDY), OnDrdy, FALLING);
        StartStreaming();
        Serial.printf(">> ADS1298 streaming: %d SPS, gain %d, %d-Hz notch\n", SAMPLE_RATE, GAIN, (int)MAINS_HZ);
        return ok;
    }

    uint8_t DeviceId() { return deviceId; }

    InputMode CurrentMode() { return mode; }

    bool SetInputMode(InputMode newMode)
    {
        xSemaphoreTake(spiLock, portMAX_DELAY);
        StopStreaming();
        mode = newMode;
        bool ok = Configure();
        envelope.Init(SAMPLE_RATE, MAINS_HZ);  // clear filter state from the old input
        StartStreaming();
        xSemaphoreGive(spiLock);
        portENTER_CRITICAL(&statsLock);
        ResetStatsLocked();
        portEXIT_CRITICAL(&statsLock);
        return ok;
    }

    bool NextEnvelope(float out[CHANNELS], uint32_t timeoutMs)
    {
        return envelopeQueue && xQueueReceive(envelopeQueue, out, pdMS_TO_TICKS(timeoutMs)) == pdTRUE;
    }

    Stats TakeStats()
    {
        portENTER_CRITICAL(&statsLock);
        Stats s = stats;
        uint32_t elapsed = (uint32_t)(millis() - statsSince);
        float seconds = (elapsed > 0 ? elapsed : 1) / 1000.0f;
        s.rateHz = s.frames / seconds;
        uint32_t envelopes = s.frames / (SAMPLE_RATE / 50);
        if (envelopes == 0) envelopes = 1;
        for (int c = 0; c < CHANNELS; c++)
        {
            s.peakToPeakUv[c] = rawMax[c] >= rawMin[c] ? rawMax[c] - rawMin[c] : 0;
            s.rmsUv[c] = sqrtf(sqSum[c] / envelopes);
        }
        ResetStatsLocked();
        portEXIT_CRITICAL(&statsLock);
        return s;
    }

    bool HandleCommand(const String& cmd)
    {
        if (!cmd.startsWith("ads")) return false;
        String arg = cmd.substring(3);
        arg.trim();
        if (arg.startsWith("mode"))
        {
            String m = arg.substring(4);
            m.trim();
            InputMode next;
            if (m == "electrodes") next = InputMode::Electrodes;
            else if (m == "shorted") next = InputMode::Shorted;
            else if (m == "supply") next = InputMode::Supply;
            else if (m == "temp") next = InputMode::Temperature;
            else if (m == "test") next = InputMode::TestSignal;
            else
            {
                Serial.println("ads mode electrodes|shorted|supply|temp|test");
                return true;
            }
            Serial.printf(">> ADS1298 input mode: %s %s\n", m.c_str(), SetInputMode(next) ? "" : "(read-back failed)");
        }
        else if (arg == "diag on" || arg == "diag off")
        {
            diagnostics = arg == "diag on";
            TakeStats();
        }
        else
        {
            Serial.println("ads mode electrodes|shorted|supply|temp|test, ads diag on|off");
        }
        return true;
    }

    void PrintDiagnostics()
    {
        static uint32_t last = 0;
        if (!diagnostics || millis() - last < 1000) return;
        last = millis();
        Stats s = TakeStats();
        Serial.printf("ADS %.0f SPS  frames %u  bad %u  dropped %u\n", s.rateHz, s.frames, s.badFrames, s.dropped);
        Serial.print("  p-p uV:");
        for (int c = 0; c < CHANNELS; c++) Serial.printf(" %8.1f", s.peakToPeakUv[c]);
        Serial.print("\n  EMG uV:");
        for (int c = 0; c < CHANNELS; c++) Serial.printf(" %8.1f", s.rmsUv[c]);
        Serial.println();
    }
}
