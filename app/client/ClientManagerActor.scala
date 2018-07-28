package client

import akka.actor.{Actor, ActorLogging, PoisonPill, Props, Terminated}
import client.ClientActor._
import client.ClientManagerActor._

import scala.concurrent.duration._

/**
  * Manages multiple "dslinks" imitations that connect to a broker.
  */
class ClientManagerActor(reconnectTimeout: Option[Duration]) extends Actor with ActorLogging {

  override def preStart(): Unit = log.info("ClientManager started")

  override def postStop(): Unit = log.info("ClientManager stopped")

  def receive: Receive = {
    case ConnectClientToBroker(linkId) =>
      log.info("Received connection request for link [{}]", linkId)
      val client = getOrCreateClient(linkId)
      client ! ConnectToBroker

    case ConnectedToBroker(linkId, _, _) =>
      log.info("Client [{}] connected to broker", linkId)

    case DisconnectClientFromBroker(linkId) =>
      log.info("Received disconnect request for link [{}]", linkId)
      context.child(linkId) foreach (_ ! PoisonPill)

    case DisconnectedFromBroker(linkId, _) =>
      log.info("Client [{}] disconnected from broker", linkId)

    case Terminated(ref) =>
      log.info("Client [{}] terminated", ref.path.name)

    case GetClients => sender ! context.children

    case GetClient(linkId) => sender ! context.child(linkId)
  }

  private def getOrCreateClient(linkId: String) = context.child(linkId).getOrElse {
    context.watch(context.actorOf(ClientActor.props(reconnectTimeout), linkId))
  }
}

/**
  * Factory for ClientActor instances.
  */
object ClientManagerActor {
  def props(reconnectTimeout: Option[Duration]) = Props(new ClientManagerActor(reconnectTimeout))

  case class ConnectClientToBroker(linkId: String)

  case class DisconnectClientFromBroker(linkId: String)

  case object GetClients

  case class GetClient(linkId: String)

}