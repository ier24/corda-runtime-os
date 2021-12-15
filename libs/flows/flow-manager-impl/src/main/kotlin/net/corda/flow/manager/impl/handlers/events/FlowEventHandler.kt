package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.flow.fiber.FlowContinuation

interface FlowEventHandler<T> {

    val type: Class<T>

    fun preProcess(context: FlowEventContext<T>): FlowEventContext<T>

    fun resumeOrContinue(context: FlowEventContext<T>): FlowContinuation

    fun postProcess(context: FlowEventContext<T>): FlowEventContext<T>
}

fun FlowEventHandler<*>.requireCheckpoint(checkpoint: Checkpoint?): Checkpoint {
    return checkpoint ?: throw FlowProcessingException("${this::class.java.name} requires a non-null checkpoint as input")
}