package models

import akka.actor.{Actor, ActorLogging, Props}
import models.EndpointActor.{EndpointState, GetEndpointState}

/**
  * Imitates a WebSocket actor.
  *
  * @param linkMgr
  * @param linkId
  */
class EndpointActor(linkMgr: LinkManager, linkId: String) extends Actor with ActorLogging {

  private val epId = self.path.name

  var inbound: Option[InboundMessage] = None
  var outbound: Option[OutboundMessage] = None

  override def preStart(): Unit = log.info("Endpoint[{}] started", linkId)

  override def postStop(): Unit = log.info("Endpoint[{}] stopped", linkId)

  def receive: Receive = {

    case msg: InboundMessage =>
      log.info("Endpoint[{}] received {}, forwarding to Link[{}]", epId, msg, linkId)
      this.inbound = Some(msg)
      linkMgr.tell(linkId, msg)

    case msg: OutboundMessage =>
      log.info("Endpoint[{}] received {} from Link[{}]", epId, msg, linkId)
      this.outbound = Some(msg)

    case GetEndpointState => sender ! EndpointState(inbound, outbound)
  }
}

/**
  * Instance factory for WebSocket actor.
  */
object EndpointActor {
  def props(linkMgr: LinkManager, linkId: String) = Props(new EndpointActor(linkMgr, linkId))

  case object GetEndpointState

  case class EndpointState(inbound: Option[InboundMessage], outbound: Option[OutboundMessage])

}