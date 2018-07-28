package models

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import models.EndpointActor.{EndpointState, GetEndpointState}

/**
  * Imitates a WebSocket actor connected to a "client" actor representing a remote dslink.
  *
  * @param linkMgr
  * @param linkId
  * @param client
  */
class EndpointActor(linkMgr: LinkManager, linkId: String, client: ActorRef) extends Actor with ActorLogging {

  private val epId = self.path.name

  var inbound: Option[InboundMessage] = None
  var outbound: Option[OutboundMessage] = None

  override def preStart(): Unit = {
    context.watch(client)
    log.info("Endpoint[{}] started", linkId)
  }

  override def postStop(): Unit = log.info("Endpoint[{}] stopped", linkId)

  def receive: Receive = {

    case msg: InboundMessage =>
      log.info("Endpoint[{}] received {}, forwarding to Link[{}]", epId, msg, linkId)
      this.inbound = Some(msg)
      sendToLinkActor(msg)

    case msg: OutboundMessage =>
      log.info("Endpoint[{}] received {} from Link[{}]", epId, msg, linkId)
      this.outbound = Some(msg)

    case GetEndpointState => sender ! EndpointState(inbound, outbound)

    case Terminated(_) => context.stop(self)
  }

  private def sendToRemote(msg: Any) = client ! msg

  private def sendToLinkActor(msg: Any) = linkMgr.tell(linkId, msg)
}

/**
  * Instance factory for WebSocket actor.
  */
object EndpointActor {
  def props(linkMgr: LinkManager, linkId: String, ref: ActorRef) = Props(new EndpointActor(linkMgr, linkId, ref))

  case object GetEndpointState

  case class EndpointState(inbound: Option[InboundMessage], outbound: Option[OutboundMessage])

}