package net.corda.sandboxgroupcontext.impl

import net.corda.cpk.read.CpkReadService
import net.corda.libs.packaging.CpkIdentifier
import net.corda.packaging.CPK
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.getUniqueObject
import net.corda.sandboxgroupcontext.putUniqueObject
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextCache
import net.corda.sandboxgroupcontext.service.impl.CloseableSandboxGroupContext
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextServiceImpl
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.osgi.framework.BundleContext
import org.osgi.service.component.runtime.ServiceComponentRuntime

class StubSandboxGroupContextCache: SandboxGroupContextCache {
    override val cacheSize: Long
        get() = 0

    override fun remove(virtualNodeContext: VirtualNodeContext) {
    }

    override fun get(
        virtualNodeContext: VirtualNodeContext,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext  = createFunction(virtualNodeContext)

    override fun close() {
    }
}

class SandboxGroupContextServiceImplTest {

    private lateinit var service: SandboxGroupContextServiceImpl
    private val holdingIdentity = HoldingIdentity("foo", "bar")
    private val mainBundle = "MAIN BUNDLE"

    private val scr = mock<ServiceComponentRuntime>()
    private val bundleContext = mock<BundleContext>()
    private val cpks = setOf(Helpers.mockTrivialCpk(mainBundle, "example", "1.0.0"))
    private val cache = StubSandboxGroupContextCache()

    private lateinit var virtualNodeContext: VirtualNodeContext

    private fun createVirtualNodeContextForFlow(holdingIdentity: HoldingIdentity, cpks: Set<CpkIdentifier>):
            VirtualNodeContext {
        return VirtualNodeContext(
            holdingIdentity,
            cpks,
            SandboxGroupType.FLOW,
            SingletonSerializeAsToken::class.java,
            null
        )
    }

    class CpkReadServiceFake(private val cpks: Set<CPK>) : CpkReadService {
        override fun get(cpkId: CpkIdentifier): CPK? {
            return cpks.singleOrNull { (it.metadata.id.name == cpkId.name) && (it.metadata.id.version == cpkId.version) }
        }

        override val isRunning: Boolean
            get() = true

        override fun start() {
        }

        override fun stop() {
        }
    }

    private val cpkServiceImpl = CpkReadServiceFake(cpks)

    @BeforeEach
    private fun beforeEach() {
        service = SandboxGroupContextServiceImpl(
            Helpers.mockSandboxCreationService(listOf(cpks)),
            cpkServiceImpl,
            scr,
            bundleContext,
            cache
        )
        virtualNodeContext = createVirtualNodeContextForFlow(
            holdingIdentity, cpks.map { CpkIdentifier.fromLegacy(it.metadata.id) }.toSet()
        )
    }

    @Test
    fun `can create a sandbox group context without initializer`() {
        val ctx = service.getOrCreate(virtualNodeContext) { _, _ -> AutoCloseable { } }
        assertThat(virtualNodeContext).isEqualTo(ctx.virtualNodeContext)
    }

    @Test
    fun `can create a sandbox group context with initializer`() {
        var initializerCalled = false
        val ctx = service.getOrCreate(virtualNodeContext) { _, _ ->
            initializerCalled = true
            AutoCloseable { }
        }

        assertThat(virtualNodeContext).isEqualTo(ctx.virtualNodeContext)
        assertThat(initializerCalled).isTrue
    }

    data class Dog(val name: String, val noise: String)

    @Test
    fun `can create add objects during initializer`() {
        var initializerCalled = false
        val dog = Dog("Rover", "Woof!")
        var actualHoldingIdentity: HoldingIdentity? = null
        val ctx = service.getOrCreate(virtualNodeContext) { holdingIdentity, mutableContext ->
            initializerCalled = true
            actualHoldingIdentity = holdingIdentity
            mutableContext.putUniqueObject(dog)
            AutoCloseable { }
        }

        assertThat(virtualNodeContext).isEqualTo(ctx.virtualNodeContext)
        assertThat(initializerCalled).isTrue

        val actualDog = ctx.getUniqueObject<Dog>()

        assertThat(actualDog!!).isEqualTo(dog)
        assertThat(actualDog.noise).isEqualTo(dog.noise)
        assertThat(actualDog.noise).isEqualTo(dog.noise)

        assertThat(ctx.virtualNodeContext.holdingIdentity).isEqualTo(holdingIdentity)
        assertThat(actualHoldingIdentity).isEqualTo(holdingIdentity)
    }

    @Test
    fun `can create objects with same keys in different VirtualNodeContexts`() {
        val holdingIdentity1 = HoldingIdentity("OU=1", "bar")
        val holdingIdentity2 = HoldingIdentity("OU=2", "bar")
        val holdingIdentity3 = HoldingIdentity("OU=3", "bar")

        val cpks1 = setOf(Helpers.mockTrivialCpk("MAIN1", "apple", "1.0.0"))
        val cpks2 = setOf(Helpers.mockTrivialCpk("MAIN2", "banana", "2.0.0"))
        val cpks3 = setOf(Helpers.mockTrivialCpk("MAIN3", "cranberry", "3.0.0"))

        val ctx1 = createVirtualNodeContextForFlow(
            holdingIdentity1,
            cpks1.map { CpkIdentifier.fromLegacy(it.metadata.id) }.toSet()
        )
        val ctx2 = createVirtualNodeContextForFlow(
            holdingIdentity2,
            cpks2.map { CpkIdentifier.fromLegacy(it.metadata.id) }.toSet()
        )
        val ctx3 = createVirtualNodeContextForFlow(
            holdingIdentity3,
            cpks3.map { CpkIdentifier.fromLegacy(it.metadata.id) }.toSet()
        )

        val sandboxCreationService = Helpers.mockSandboxCreationService(listOf(cpks1, cpks2, cpks3))

        val cpkService = CpkReadServiceFake(cpks1 + cpks2 + cpks3)

        val service = SandboxGroupContextServiceImpl(sandboxCreationService, cpkService, scr, bundleContext, cache)

        val dog1 = Dog("Rover", "Woof!")
        val dog2 = Dog("Rover", "Bark!")
        val dog3 = Dog("Rover", "Howl!")

        val sandboxGroupContext1 = service.getOrCreate(ctx1) { _, mc ->
            mc.putUniqueObject(dog1)
            AutoCloseable { }
        }

        val sandboxGroupContext2 = service.getOrCreate(ctx2) { _, mc ->
            mc.putUniqueObject(dog2)
            AutoCloseable { }
        }

        val sandboxGroupContext3 = service.getOrCreate(ctx3) { _, mc ->
            mc.putUniqueObject(dog3)
            AutoCloseable { }
        }

        // Can get correct 'unique' object from context 1
        assertThat(sandboxGroupContext1.virtualNodeContext.holdingIdentity).isEqualTo(holdingIdentity1)
        val actualDog1 = sandboxGroupContext1.getUniqueObject<Dog>()
        assertThat(actualDog1!!).isEqualTo(dog1)
        assertThat(actualDog1.noise).isEqualTo(dog1.noise)
        assertThat(actualDog1.noise).isNotEqualTo(dog2.noise)

        // Can get correct 'unique' object from context 2
        assertThat(sandboxGroupContext2.virtualNodeContext.holdingIdentity).isEqualTo(holdingIdentity2)
        val actualDog2 = sandboxGroupContext2.getUniqueObject<Dog>()
        assertThat(actualDog2!!).isEqualTo(dog2)
        assertThat(actualDog2.noise).isEqualTo(dog2.noise)
        assertThat(actualDog2.noise).isNotEqualTo(dog1.noise)

        // Can get correct 'unique' object from context 3
        assertThat(sandboxGroupContext3.virtualNodeContext.holdingIdentity).isEqualTo(holdingIdentity3)
        val actualDog3 = sandboxGroupContext3.getUniqueObject<Dog>()
        assertThat(actualDog3!!).isEqualTo(dog3)
        assertThat(actualDog3.noise).isEqualTo(dog3.noise)
        assertThat(actualDog3.noise).isNotEqualTo(dog1.noise)
    }

    @Test
    fun `remove removes from cache`() {
        val holdingIdentity1 = HoldingIdentity("OU=1", "bar")
        val cpks1 = setOf(Helpers.mockTrivialCpk("MAIN1", "example", "1.0.0"))
        val ctx1 = createVirtualNodeContextForFlow(
            holdingIdentity1,
            cpks1.map { CpkIdentifier.fromLegacy(it.metadata.id) }.toSet()
        )
        val sandboxCreationService = Helpers.mockSandboxCreationService(listOf(cpks1))
        val cpkService = CpkReadServiceFake(cpks1)
        val mockCache = mock<SandboxGroupContextCache>()
        val service = SandboxGroupContextServiceImpl(sandboxCreationService, cpkService, scr, bundleContext, mockCache)

        service.remove(ctx1)

        verify(mockCache).remove(ctx1)
    }

    @Test
    fun `assert hasCpks`() {
        val existingCpks = setOf(
            Helpers.mockTrivialCpk("MAIN1", "apple", "1.0.0"),
            Helpers.mockTrivialCpk("MAIN2", "banana", "2.0.0"),
            Helpers.mockTrivialCpk("MAIN3", "cranberry", "3.0.0")
        )
        val nonExistingCpk = setOf(Helpers.mockTrivialCpk("MAIN4", "orange", "4.0.0"))

        val service = existingCpks.let {
            val sandboxCreationService = Helpers.mockSandboxCreationService(listOf(it))
            val cpkService = CpkReadServiceFake(it)
            SandboxGroupContextServiceImpl(sandboxCreationService, cpkService, scr, bundleContext, cache)
        }

        val existingCpkIds = existingCpks.map {
            CpkIdentifier.fromLegacy(it.metadata.id)
        }.toSet()

        val nonExistingCpkId = nonExistingCpk.map { CpkIdentifier.fromLegacy(it.metadata.id) }.toSet()

        val noCpks = emptySet<CpkIdentifier>()

        assertTrue(service.hasCpks(existingCpkIds))
        assertFalse(service.hasCpks(nonExistingCpkId))
        assertTrue(service.hasCpks(noCpks))
    }
}
