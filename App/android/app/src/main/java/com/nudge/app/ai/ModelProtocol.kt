package com.nudge.app.ai

import java.util.Locale

/** Text commands that install a model on the wearable. Must match GestureModel.cpp. */
object ModelProtocol {
    private const val VALUES_PER_LINE = 8

    fun uploadCommands(model: GestureModel, id: Int): List<String> {
        require(id in 1..0xFFFF)
        // Send the exact text the firmware will parse, and checksum those same rounded values
        val values = model.toParameters().map { String.format(Locale.US, "%.7g", it) }
        val checksum = values.sumOf { it.toDouble() }
        val lines = values.chunked(VALUES_PER_LINE).mapIndexed { i, chunk ->
            "mdl ${i * VALUES_PER_LINE} ${chunk.joinToString(" ")}"
        }
        return listOf("mdl_begin $id ${values.size}") + lines + "mdl_end ${String.format(Locale.US, "%.7g", checksum)}"
    }

    const val CLEAR = "mdl_clear"
}
