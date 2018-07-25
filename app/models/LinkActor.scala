package models

import akka.actor.{ActorLogging, ActorRef, Props, ReceiveTimeout, Terminated}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{PersistentActor, SnapshotOffer}
import models.LinkActor._

import scala.concurrent.duration.Duration

/**
  * Imitates a DSLink Actor.
  */
class LinkActor(linkMgr: LinkManager, receiveTimeout: Option[Duration], snapshotInterval: Option[Int])
  extends PersistentActor with ActorLogging {

  val linkId = self.path.name

  val persistenceId: String = linkId

  var state = LinkState.Empty

  receiveTimeout foreach { to =>
    log.info("Link[{}] setting inactivity timeout to {}", linkId, to)
    context.setReceiveTimeout(to)
  }

  override def preStart(): Unit = log.info("Link[{}] started", linkId)

  override def postStop(): Unit = log.info("Link[{}] stopped", linkId)

  def receiveRecover: Receive = {
    case EndpointConnected(ref)                => connectEndpoint(ref)
    case EndpointDisconnected                  => disconnectEndpoint()
    case InboundReceived(msg)                  => state = state.withLastInbound(msg)
    case OutboundReceived(msg)                 => state = state.withLastOutbound(msg)
    case SnapshotOffer(_, snapshot: LinkState) => state = snapshot
  }

  def receiveCommand: Receive = disconnected

  def disconnected: Receive = commonBehavior orElse {
    case ConnectEndpoint(ref) => persist(EndpointConnected(ref)) { evt =>
      connectEndpoint(evt.epRef)
      maybeSaveSnapshot()
    }
  }

  def connected: Receive = commonBehavior orElse {
    case DisconnectEndpoint  => persist(EndpointDisconnected) { _ =>
      disconnectEndpoint()
      maybeSaveSnapshot()
    }
    case Terminated(_)       => persist(EndpointDisconnected) { _ =>
      log.info("Link[{}] endpoint terminated", linkId)
      disconnectEndpoint()
      maybeSaveSnapshot()
    }
    case msg: InboundMessage => persist(InboundReceived(msg)) { evt =>
      log.info("Link[{}] received {} from Endpoint", linkId, evt.msg)
      state = state.withLastInbound(evt.msg)
      linkMgr.tell(evt.msg.to, OutboundMessage(linkId, evt.msg.body))
      maybeSaveSnapshot()
    }
  }

  def commonBehavior: Receive = {
    case GetLinkInfo          => sender ! LinkInfo(self, linkId, state.endpoint, state.inbound, state.outbound)
    case ReceiveTimeout       => context.parent ! Passivate(Stop); log.info("Link[{}] passivating", linkId)
    case Stop                 =>
      disconnectEndpoint()
      context.stop(self)
      log.info("Link[{}] stopping", linkId)
    case msg: OutboundMessage => persist(OutboundReceived(msg)) { evt =>
      log.info("Link[{}] received {}, forwarding to Endpoint if present", linkId, evt.msg)
      state = state.withLastOutbound(evt.msg)
      state.endpoint foreach (_ ! evt.msg)
      maybeSaveSnapshot()
    }
  }

  private def connectEndpoint(ref: ActorRef) = {
    state = state.withEndpoint(context.watch(ref))
    log.info("Link[{}] connected to endpoint", linkId)
    context.become(connected)
  }

  private def disconnectEndpoint() = {
    state.endpoint foreach context.unwatch
    state = state.withoutEndpoint
    log.info("Link[{}] disconnected from endpoint", linkId)
    context.become(disconnected)
  }

  private def maybeSaveSnapshot() = snapshotInterval foreach { interval =>
    if (lastSequenceNr % interval == 0 && lastSequenceNr != 0) {
      saveSnapshot(state)
      log.info("Link[{}] snapshot saved", linkId)
    }
  }
}

/**
  * Factory for LinkActor instances.
  */
object LinkActor {

  def props(linkMgr: LinkManager, timeout: Option[Duration], snapshotInterval: Option[Int]) =
    Props(new LinkActor(linkMgr, timeout, snapshotInterval))

  case class ConnectEndpoint(epRef: ActorRef)

  case class EndpointConnected(epRef: ActorRef)

  case object DisconnectEndpoint

  case object EndpointDisconnected

  case object Stop

  case object GetLinkInfo

  case class LinkInfo(ref: ActorRef, linkId: String, endpoint: Option[ActorRef],
                      inbound: Option[InboundMessage],
                      outbound: Option[OutboundMessage]) {
    val isConnected = endpoint.isDefined
    val shardId = ref.path.parent.name
    val isLocalEp = endpoint.map(_.path.address == ref.path.address)
  }

  case class InboundReceived(msg: InboundMessage)

  case class OutboundReceived(msg: OutboundMessage)

}

/**
  * LinkActor state.
  *
  * @param endpoint
  * @param inbound
  * @param outbound
  */
case class LinkState(endpoint: Option[ActorRef], inbound: Option[InboundMessage], outbound: Option[OutboundMessage]) {

  def withEndpoint(ref: ActorRef) = copy(endpoint = Some(ref))

  def withoutEndpoint = copy(endpoint = None)

  def withLastInbound(msg: InboundMessage) = copy(inbound = Some(msg))

  def withLastOutbound(msg: OutboundMessage) = copy(outbound = Some(msg))
}

/**
  * Instance factory for LinkState.
  */
object LinkState {
  val Empty = LinkState(None, None, None)
}