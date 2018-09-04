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

  /**
    * Handles incoming messages.
    */
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

  /**
    * Returns a child ClientActor with this name or creates a new one, if it does not exist.
    *
    * @param linkId
    * @return
    */
  private def getOrCreateClient(linkId: String) = context.child(linkId).getOrElse {
    context.watch(context.actorOf(ClientActor.props(reconnectTimeout), linkId))
  }
}

/**
  * Factory for ClientManagerActor instances.
  */
object ClientManagerActor {

  /**
    * Creates a new props for ClientManagerActor.
    *
    * @param reconnectTimeout
    * @return
    */
  def props(reconnectTimeout: Option[Duration]) = Props(new ClientManagerActor(reconnectTimeout))

  /**
    * Request to create a new client and connect it to the broker.
    *
    * @param linkId
    */
  case class ConnectClientToBroker(linkId: String)

  /**
    * Request to disconnect a client with this name, it also remove the client actor.
    *
    * @param linkId
    */
  case class DisconnectClientFromBroker(linkId: String)

  /**
    * Request to return all client actor refs.
    */
  case object GetClients

  /**
    * Request to return a client actor ref with this name.
    *
    * @param linkId
    */
  case class GetClient(linkId: String)
}