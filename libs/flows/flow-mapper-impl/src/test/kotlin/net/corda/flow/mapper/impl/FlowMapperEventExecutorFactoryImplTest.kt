package net.corda.flow.mapper.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionError
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.mapper.impl.executor.ExecuteCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.ScheduleCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.SessionErrorExecutor
import net.corda.flow.mapper.impl.executor.SessionEventExecutor
import net.corda.flow.mapper.impl.executor.StartFlowExecutor
import net.corda.libs.configuration.SmartConfigImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class FlowMapperEventExecutorFactoryImplTest {

    private lateinit var executorFactoryImpl: FlowMapperEventExecutorFactoryImpl

    @BeforeEach
    fun setup() {
        val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock()
        whenever(cordaAvroSerializationFactory.createAvroSerializer<SessionEvent>(anyOrNull())).thenReturn(mock())
        executorFactoryImpl = FlowMapperEventExecutorFactoryImpl(cordaAvroSerializationFactory)
    }

    @Test
    fun testStartRPCFlowExecutor() {
        val executor = executorFactoryImpl.create("", FlowMapperEvent(StartFlow()), null, SmartConfigImpl.empty(), Instant.now())
        assertThat(executor::class).isEqualTo(StartFlowExecutor::class)
    }

    @Test
    fun testSessionEventExecutor() {
        val executor = executorFactoryImpl.create(
            "",
            FlowMapperEvent(SessionEvent(MessageDirection.INBOUND, Instant.now(), "", 1,
                HoldingIdentity(), HoldingIdentity(), 0, listOf(), null)),
            null,
            SmartConfigImpl.empty(),
            Instant.now()
        )
        assertThat(executor::class).isEqualTo(SessionEventExecutor::class)
    }

    @Test
    fun testSessionErrorExecutor() {
        val executor = executorFactoryImpl.create(
            "",
            FlowMapperEvent(SessionEvent(
                MessageDirection.INBOUND,
                Instant.now(), "", 1,
                HoldingIdentity(),
                HoldingIdentity(),
                0,
                listOf(),
                SessionError(
                    ExceptionEnvelope(
                        "FlowMapper-SessionError",
                        "Received SessionError with sessionId 1"
                    )
                ))),
            null,
            SmartConfigImpl.empty(),
            Instant.now()
        )
        assertThat(executor::class).isEqualTo(SessionErrorExecutor::class)
    }

    @Test
    fun testExecuteCleanupExecutor() {
        val executor = executorFactoryImpl.create("", FlowMapperEvent(ExecuteCleanup()), null, SmartConfigImpl.empty(), Instant.now())
        assertThat(executor::class).isEqualTo(ExecuteCleanupEventExecutor::class)
    }

    @Test
    fun testScheduleCleanupExecutor() {
        val executor = executorFactoryImpl.create("", FlowMapperEvent(ScheduleCleanup()), null, SmartConfigImpl.empty(), Instant.now())
        assertThat(executor::class).isEqualTo(ScheduleCleanupEventExecutor::class)
    }
}