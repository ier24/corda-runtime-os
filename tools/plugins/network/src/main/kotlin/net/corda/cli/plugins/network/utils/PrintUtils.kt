package net.corda.cli.plugins.network.utils

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer
import net.corda.cli.plugins.network.output.Output
import java.time.Instant

class PrintUtils {
    companion object {
        inline fun <reified T> printJsonOutput(result: Any, output: T) {

            val objectMapper = jacksonObjectMapper()
            val module = SimpleModule()

            module.addSerializer(Instant::class.java, InstantSerializer.INSTANCE)
            module.addDeserializer(Instant::class.java, InstantDeserializer.INSTANT)

            objectMapper.registerModule(module)
            objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

            val pp = DefaultPrettyPrinter()
            pp.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)

            val jsonString = objectMapper.writer(pp).writeValueAsString(result)
            when (output) {
                is Output -> output.generateOutput(jsonString)
                else -> throw IllegalArgumentException("Unsupported output type")
            }
        }

        fun verifyAndPrintError(action: () -> Unit) {
            try {
                action()
            } catch (e: Exception) {
                System.err.println("Error: ${e.message}")
            }
        }
    }
}