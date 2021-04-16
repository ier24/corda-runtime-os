package net.corda.messaging.api.subscription.factory.config

/**
 * Class to store the required params to create a Subscription.
 * @property groupName The unique ID for a group of consumers.
 * @property instanceId Required for transactional publishing where order is important.
 * @property eventTopic Topic to get events from.
 */
data class SubscriptionConfig (val groupName:String,
                               val instanceId:Int,
                               val eventTopic:String)