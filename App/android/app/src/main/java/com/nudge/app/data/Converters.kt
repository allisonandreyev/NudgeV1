package com.nudge.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromConnectionStatus(value: ConnectionStatus): String {
        return value.name
    }

    @TypeConverter
    fun toConnectionStatus(value: String): ConnectionStatus {
        return ConnectionStatus.valueOf(value)
    }

    @TypeConverter
    fun fromRestPosition(value: RestPosition): String {
        return value.name
    }

    @TypeConverter
    fun toRestPosition(value: String): RestPosition {
        return RestPosition.valueOf(value)
    }
}
