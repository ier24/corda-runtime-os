package net.corda.p2p.linkmanager.sessions

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.virtualnode.HoldingIdentity

internal interface SessionManager : LifecycleWithDominoTile {
    fun processOutboundMessage(message: AuthenticatedMessageAndKey): SessionState
    fun getSessionById(uuid: String): SessionDirection
    fun processSessionMessage(message: LinkInMessage): LinkOutMessage?
    fun inboundSessionEstablished(sessionId: String)
    fun messageAcknowledged(sessionId: String)

    fun recordsForSessionEstablished(
        session: Session,
        messageAndKey: AuthenticatedMessageAndKey,
    ): List<Record<String, *>>


    data class SessionCounterparties(
        val ourId: HoldingIdentity,
        val counterpartyId: HoldingIdentity
    )

    sealed class SessionState {
        data class NewSessionsNeeded(val messages: List<Pair<String, LinkOutMessage>>) : SessionState()
        object SessionAlreadyPending : SessionState()
        data class SessionEstablished(val session: Session) : SessionState()
        object CannotEstablishSession : SessionState()
    }

    sealed class SessionDirection {
        data class Inbound(val counterparties: SessionCounterparties, val session: Session) : SessionDirection()
        data class Outbound(val counterparties: SessionCounterparties, val session: Session) : SessionDirection()
        object NoSession : SessionDirection()
    }
}