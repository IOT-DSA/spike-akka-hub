package models

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import models.EndpointActor.{EndpointInfo, EndpointState, GetEndpointInfo, GetEndpointState}

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

    case GetEndpointInfo => sender ! EndpointInfo(self, linkId, client)

    case Terminated(_) => {
      log.info("Endpoint [{}] peer client terminated, stopping...", linkId)
      context.stop(self)
    }
  }

  private def sendToRemote(msg: Any) = client ! msg

  private def sendToLinkActor(msg: Any) = linkMgr.tell(linkId, msg)
}

/**
  * Instance factory for WebSocket actor.
  */
object EndpointActor {

  /**
    * Creates a new props instance for EndpointActor.
    *
    * @param linkMgr
    * @param linkId
    * @param ref
    * @return
    */
  def props(linkMgr: LinkManager, linkId: String, ref: ActorRef) = Props(new EndpointActor(linkMgr, linkId, ref))

  /**
    * Request to return the endpoint state.
    */
  case object GetEndpointState

  /**
    * Request to return endpoint information.
    */
  case object GetEndpointInfo

  /**
    * Encapsulates information about the endpoint, sent back in response to GetEndpointState.
    *
    * @param inbound
    * @param outbound
    */
  case class EndpointState(inbound: Option[InboundMessage], outbound: Option[OutboundMessage])

  /**
    * Encapsulates endpoint information.
    *
    * @param ref
    * @param linkId
    * @param client
    */
  case class EndpointInfo(ref: ActorRef, linkId: String, client: ActorRef)

}