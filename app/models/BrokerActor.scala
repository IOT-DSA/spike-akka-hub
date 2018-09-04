package models

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, RootActorPath}
import akka.cluster.Cluster
import akka.pattern._
import akka.util.Timeout
import models.BrokerActor._
import models.LinkActor.{ConnectEndpoint, GetLinkInfo, LinkInfo}

import scala.concurrent.duration._

/**
  * Responsible for major broker operations. Needs to be created under /user/broker path.
  *
  * @param linkMgr
  */
class BrokerActor(linkMgr: LinkManager) extends Actor with ActorLogging {

  implicit protected val cluster = Cluster(context.system)

  implicit protected val timeout = Timeout(5 seconds)

  import context.dispatcher

  assert(self.path == self.path.root / "user" / "broker")

  def receive: Receive = {

    case GetEndpoints => sender ! context.children

    case GetEndpoint(linkId) => sender ! context.child(linkId)

    case CreateEndpoint(linkId, client, true) =>
      val ep = context.actorOf(EndpointActor.props(linkMgr, linkId, client), linkId)
      log.info("Endpoint [{}] created to communicate with client [{}]", ep.path.name, linkId)
      linkMgr.tell(linkId, ConnectEndpoint(ep))
      sender ! EndpointCreated(linkId, ep, client)

    case evt @ CreateEndpoint(linkId, _, false) =>
      val originalSender = sender
      val linkAddress = linkMgr.ask(linkId, GetLinkInfo).mapTo[LinkInfo].map(_.ref.path.address)
      linkAddress foreach { address =>
        val selection = context.actorSelection(RootActorPath(address) / "user" / "broker")
        log.info("Routing CreateEndpoint({}) to {}", linkId, address)
        selection.tell(evt.copy(local = true), originalSender)
      }

    case RemoveEndpoint(linkId) =>
      val endpoint = linkMgr.ask(linkId, GetLinkInfo).mapTo[LinkInfo].map(_.endpoint)
      endpoint map {
        case Some(ref) =>
          ref ! PoisonPill
          log.info("Link [{}] disconnected from endpoint {}", linkId, ref)
          EndpointRemoved(linkId)
        case None      => Failure(new IllegalStateException(s"Link [$linkId] is not connected"))
      } pipeTo sender
  }
}

/**
  * Constants and helper methods for BrokerActor.
  */
object BrokerActor {

  def props(linkManager: LinkManager) = Props(new BrokerActor(linkManager))

  /**
    * Commands broker to create and endpoint actor logically connected to a remote "client".
    *
    * @param linkId
    * @param client
    * @param local if `true`, immediately creates an endpoint; if `false`, determines if the command needs
    *              to be forwarded to the cluster node where link actor is running.
    */
  case class CreateEndpoint(linkId: String, client: ActorRef, local: Boolean = false)

  /**
    * Sent as response to CreateEndpoint.
    *
    * @param linkId
    * @param endpoint
    * @param client
    */
  case class EndpointCreated(linkId: String, endpoint: ActorRef, client: ActorRef)

  /**
    * Commands broker actor to kill the endpoint actor connected to the link.
    *
    * @param linkId
    */
  case class RemoveEndpoint(linkId: String)

  /**
    * Sent as response to RemoveEndpoint.
    *
    * @param linkId
    */
  case class EndpointRemoved(linkId: String)

  /**
    * Request for all broker's endpoints.
    */
  case object GetEndpoints

  /**
    * Request to retrieve the specified endpoint.
    *
    * @param linkId
    */
  case class GetEndpoint(linkId: String)

}
