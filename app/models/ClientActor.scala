package models

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import models.BrokerActor.{CreateEndpoint, EndpointCreated}
import models.ClientActor.{ConnectToBroker, DisconnectFromBroker, GetEndpoint, GetEndpoints}

/**
  * Simulates a DSLink client.
  */
class ClientActor(autoReconnect: Boolean) extends Actor with ActorLogging {

  assert(self.path == self.path.root / "user" / "client")

  private val broker = context.actorSelection("/user/broker")

  private var endpoints = Map.empty[String, ActorRef]

  def receive: Receive = {

    case ConnectToBroker(linkId) =>
      log.info(s"Received connection request for link [$linkId], forwarding to broker")
      broker ! CreateEndpoint(linkId)

    case EndpointCreated(linkId, ep) =>
      log.info(s"Endpoint for link [$linkId] created on broker")
      endpoints += linkId -> context.watch(ep)

    case Terminated(ref) =>
      endpoints.find(_._2 == ref).map(_._1) foreach { linkId =>
        log.info(s"Endpoint for link [$linkId] terminated")
        endpoints -= linkId
        if (autoReconnect) {
          log.info(s"Issuing auto-connect for link [$linkId]")
          self ! ConnectToBroker(linkId)
        }
      }

    case DisconnectFromBroker(linkId) =>
      log.info(s"Disconnect ordered for link [$linkId]")
      endpoints.get(linkId).foreach { ref =>
        context.unwatch(ref)
        ref ! PoisonPill
      }
      endpoints -= linkId

    case GetEndpoints => sender ! endpoints

    case GetEndpoint(linkId) => sender ! endpoints.get(linkId)
  }
}

/**
  * Factory for ClientActor.
  */
object ClientActor {

  def props(autoReconnect: Boolean = true) = Props(new ClientActor(autoReconnect))

  case class ConnectToBroker(linkId: String)

  case class DisconnectFromBroker(linkId: String)

  case object GetEndpoints

  case class GetEndpoint(linkId: String)

}