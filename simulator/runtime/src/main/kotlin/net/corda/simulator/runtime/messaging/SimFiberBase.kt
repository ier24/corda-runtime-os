package net.corda.simulator.runtime.messaging

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.runtime.flows.FlowAndProtocol
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.notary.BaseNotaryInfo
import net.corda.simulator.runtime.notary.BaseNotaryLookupFactory
import net.corda.simulator.runtime.notary.NotaryLookupFactory
import net.corda.simulator.runtime.persistence.CloseablePersistenceService
import net.corda.simulator.runtime.persistence.DbPersistenceServiceFactory
import net.corda.simulator.runtime.persistence.PersistenceServiceFactory
import net.corda.simulator.runtime.signing.KeyStoreFactory
import net.corda.simulator.runtime.signing.SigningServiceFactory
import net.corda.simulator.runtime.signing.SimKeyStore
import net.corda.simulator.runtime.signing.keystoreFactoryBase
import net.corda.simulator.runtime.signing.signingServiceFactoryBase
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.membership.MemberInfo
import net.corda.v5.membership.NotaryInfo
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers responder flows and other members, and looks up responder flows via their protocol. All state shared
 * between nodes is stored in this class, so that all generated keys are available on the [MemberInfo]s and
 * that a member's [PersistenceService] is available to each of their nodes.
 *
 * @param persistenceServiceFactory The factory for creating persistence services.
 * @param memberLookUpFactory The factory for creating member lookup.
 */
@Suppress("LongParameterList")
class SimFiberBase(
    private val persistenceServiceFactory : PersistenceServiceFactory = DbPersistenceServiceFactory(),
    private val memberLookUpFactory: MemberLookupFactory = BaseMemberLookupFactory(),
    private val signingServiceFactory: SigningServiceFactory = signingServiceFactoryBase(),
    private val keystoreFactory: KeyStoreFactory = keystoreFactoryBase(),
    private val flowMessagingFactory: FlowMessagingFactory = BaseFlowMessagingFactory(),
    private val flowRegistry: FlowRegistry = BaseFlowRegistry(),
    private val notaryLookupFactory: NotaryLookupFactory = BaseNotaryLookupFactory()
) : SimFiber, FlowRegistry by flowRegistry {

    private val persistenceServices = ConcurrentHashMap<MemberX500Name, CloseablePersistenceService>()
    private val memberInfos = ConcurrentHashMap<MemberX500Name, BaseMemberInfo>()
    private val keyStores = ConcurrentHashMap<MemberX500Name, SimKeyStore>()
    private val notaryX500 = MemberX500Name.parse("CN=SimulatorNotaryService, OU=Simulator, O=R3, L=London, C=GB")
    private lateinit var notaryInfo: NotaryInfo

    init {
        registerNotary()
    }

    override val members : Map<MemberX500Name, MemberInfo>
        get() = memberInfos

    override fun registerMember(member: MemberX500Name) {
        memberInfos.putIfAbsent(member, BaseMemberInfo(member))
        keyStores.putIfAbsent(member, keystoreFactory.createKeyStore())
    }

    override fun getOrCreatePersistenceService(member: MemberX500Name): PersistenceService {
        persistenceServices.computeIfAbsent(member) { m -> persistenceServiceFactory.createPersistenceService(m) }
        return persistenceServices[member]!!
    }

    override fun createSigningService(member: MemberX500Name): SigningService {
        val keyStore = keyStores[member] ?: error("KeyStore not registered for $member; this should never happen")
        return signingServiceFactory.createSigningService(keyStore)
    }

    override fun createNotarySigningService(): SigningService {
        val keyStore = keyStores[notaryX500] ?:
        error("KeyStore not registered for $notaryX500; this should never happen")
        return signingServiceFactory.createSigningService(keyStore)
    }

    override fun createMemberLookup(member: MemberX500Name): MemberLookup {
        return memberLookUpFactory.createMemberLookup(member, this)
    }

    override fun generateAndStoreKey(
        alias: String,
        hsmCategory: HsmCategory,
        scheme: String,
        member: MemberX500Name
    ): PublicKey {
        val keyStore = checkNotNull(keyStores[member])  {
            "KeyStore not created for \"$member\"; this should never happen"
        }
        val key = keyStore.generateKey(alias, hsmCategory, scheme)
        val memberInfo = checkNotNull(memberInfos[member]) {
            "MemberInfo not created for \"$member\"; this should never happen"
        }
        memberInfos[member] = memberInfo.copy(ledgerKeys = memberInfo.ledgerKeys.plus(key))
        return key
    }

    override fun createNotaryLookup(): NotaryLookup {
        return notaryLookupFactory.createNotaryLookup(this, notaryInfo)
    }

    override fun getNotary(): NotaryInfo {
        return notaryInfo
    }

    private fun registerNotary(){
        memberInfos.putIfAbsent(notaryX500, BaseMemberInfo(notaryX500, memberContext = hashMapOf(
            "corda.notary.service.name" to notaryX500.toString()
        )))
        val keyStore = keyStores.putIfAbsent(notaryX500, keystoreFactory.createKeyStore())
        if(keyStore == null){
            val key = keyStores[notaryX500]!!.generateKey(
                "simulated-notary", HsmCategory.LEDGER, "any-scheme")
            notaryInfo = BaseNotaryInfo(notaryX500, "", emptySet(), key)
            val memberInfo = memberInfos[notaryX500]
            memberInfos[notaryX500] =  memberInfo!!.copy(ledgerKeys = memberInfo.ledgerKeys.plus(key))
        }
    }

    override fun createFlowMessaging(
        configuration: SimulatorConfiguration,
        flow: FlowAndProtocol,
        member: MemberX500Name,
        injector: FlowServicesInjector,
        contextProperties: FlowContextProperties
    ): FlowMessaging {
        return flowMessagingFactory
            .createFlowMessaging(configuration, member, this, injector, flow, contextProperties)
    }
    override fun close() {
        persistenceServices.values.forEach { it.close() }
    }

}
