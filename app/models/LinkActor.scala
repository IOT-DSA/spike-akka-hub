package models

import akka.actor.{ActorLogging, ActorRef, Props, ReceiveTimeout, Terminated}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.PersistentActor
import models.LinkActor._

import scala.concurrent.duration.Duration

/**
  * Imitates a DSLink Actor.
  */
class LinkActor(linkMgr: LinkManager, receiveTimeout: Option[Duration], snapshotInterval: Option[Int])
  extends PersistentActor with ActorLogging {

  val linkId = self.path.name

  var endpoint: Option[ActorRef] = None

  var inbound: Option[InboundMessage] = None

  var outbound: Option[OutboundMessage] = None

  val persistenceId: String = linkId

  receiveTimeout foreach { to =>
    log.info("Link[{}] setting inactivity timeout to {}", linkId, to)
    context.setReceiveTimeout(to)
  }

  override def preStart(): Unit = log.info("Link[{}] started", linkId)

  override def postStop(): Unit = log.info("Link[{}] stopped", linkId)

  def receiveRecover: Receive = {
    case EndpointConnected(ref) => connectEndpoint(ref)
    case EndpointDisconnected   => disconnectEndpoint()
    case InboundReceived(msg)   => inbound = Some(msg)
    case OutboundReceived(msg)  => outbound = Some(msg)
  }

  def receiveCommand: Receive = disconnected

  def disconnected: Receive = commonBehavior orElse {
    case ConnectEndpoint(ref) => persist(EndpointConnected(ref)) { evt =>
      connectEndpoint(ref)
    }
  }

  def connected: Receive = commonBehavior orElse {
    case DisconnectEndpoint  => persist(EndpointDisconnected) { _ =>
      disconnectEndpoint()
    }
    case Terminated(_)       => persist(EndpointDisconnected) { _ =>
      disconnectEndpoint()
    }
    case msg: InboundMessage => persist(InboundReceived(msg)) { evt =>
      log.info("Link[{}] received {} from Endpoint", linkId, evt.msg)
      this.inbound = Some(evt.msg)
      linkMgr.tell(evt.msg.to, OutboundMessage(linkId, evt.msg.body))
    }
  }

  def commonBehavior: Receive = {
    case GetLinkInfo          => sender ! LinkInfo(self, linkId, endpoint, inbound, outbound)
    case ReceiveTimeout       => context.parent ! Passivate(Stop); log.info("Link[{}] passivating", linkId)
    case Stop                 =>
      disconnectEndpoint()
      context.stop(self)
      log.info("Link[{}] stopping", linkId)
    case msg: OutboundMessage => persist(OutboundReceived(msg)) { evt =>
      log.info("Link[{}] received {}, forwarding to Endpoint if present", linkId, evt.msg)
      this.outbound = Some(evt.msg)
      endpoint foreach (_ ! evt.msg)
    }
  }

  private def connectEndpoint(ref: ActorRef) = {
    endpoint = Some(context.watch(ref))
    log.info("Link[{}] connected to endpoint", linkId)
    context.become(connected)
  }

  private def disconnectEndpoint() = {
    endpoint foreach context.unwatch
    endpoint = None
    log.info("Link[{}] disconnected from endpoint", linkId)
    context.become(disconnected)
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