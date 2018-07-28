package client

import akka.actor.{Actor, ActorLogging, ActorRef, Props, ReceiveTimeout, RootActorPath, Terminated}
import akka.cluster.Cluster
import client.ClientActor._
import models.BrokerActor.{CreateEndpoint, EndpointCreated}

import scala.concurrent.duration._

/**
  * Mimics a remote DSLink.
  *
  * @param reconnectTimeout
  */
class ClientActor(reconnectTimeout: Option[Duration]) extends Actor with ActorLogging {

  val linkId = self.path.name

  val cluster = Cluster(context.system)

  var endpoint: Option[ActorRef] = None

  override def preStart(): Unit = {
    reconnectTimeout foreach context.setReceiveTimeout
    log.info("Client [{}] started", linkId)
  }

  override def postStop(): Unit = {
    context.parent ! DisconnectedFromBroker(linkId, self)
    log.info("Client [{}] stopped", linkId)
  }

  def receive: Receive = {
    case ConnectToBroker =>
      val brokerPath = getLeadBrokerPath
      log.info("Client [{}] is trying to connect to broker at {}", linkId, brokerPath)
      context.actorSelection(brokerPath) ! CreateEndpoint(linkId, self, false)

    case EndpointCreated(linkId, ep, client) =>
      assert(linkId == this.linkId)
      assert(client == self)
      log.info("Client [{}] connected to broker endpoint {}", linkId, ep.path)
      endpoint = Some(context.watch(ep))
      context.parent ! ConnectedToBroker(linkId, self, ep)

    case Terminated(_) =>
      endpoint foreach context.unwatch
      endpoint = None
      log.info("Client [{}] peer endpoint terminated", linkId)
      context.parent ! DisconnectedFromBroker(linkId, self)

    case ReceiveTimeout =>
      if (!endpoint.isDefined && reconnectTimeout.isDefined) {
        log.info("Client [{}] is issuing auto-reconnect to broker", linkId)
        self ! ConnectToBroker
      }

    case GetClientInfo => sender ! ClientInfo(self, linkId, endpoint)
  }

  private def getLeadBrokerAddress = cluster.state.roleLeader("broker").getOrElse {
    throw new IllegalStateException("No active broker node present")
  }

  private def getLeadBrokerPath = RootActorPath(getLeadBrokerAddress) / "user" / "broker"
}

/**
  * Factory for [[ClientActor]] instances.
  */
object ClientActor {

  def props(reconnectTimeout: Option[Duration]) = Props(new ClientActor(reconnectTimeout))

  case object ConnectToBroker

  case class ConnectedToBroker(linkId: String, client: ActorRef, endpoint: ActorRef)

  case class DisconnectedFromBroker(linkId: String, client: ActorRef)

  case object GetClientInfo

  case class ClientInfo(ref: ActorRef, linkId: String, endpoint: Option[ActorRef]) {
    val isConnected = endpoint.isDefined
  }

}