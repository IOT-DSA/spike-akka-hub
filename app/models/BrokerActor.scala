package models

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, RootActorPath}
import akka.cluster.Cluster
import akka.pattern._
import akka.util.Timeout
import models.BrokerActor.{CreateEndpoint, EndpointCreated, EndpointRemoved, RemoveEndpoint}
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

    case CreateEndpoint(linkId, true) =>
      val ep = context.actorOf(EndpointActor.props(linkMgr, linkId))
      linkMgr.tell(linkId, ConnectEndpoint(ep))
      log.info("Link [{}] connected to endpoint {}", linkId, ep)
      context.actorSelection("/user/client") ! EndpointCreated(linkId, ep)

    case CreateEndpoint(linkId, false) =>
      val originalSender = sender
      val linkAddress = linkMgr.ask(linkId, GetLinkInfo).mapTo[LinkInfo].map(_.ref.path.address)
      linkAddress foreach { address =>
        val selection = context.actorSelection(RootActorPath(address) / "user" / "broker")
        log.info("Routing CreateEndpoint({}) to {}", linkId, address)
        selection.tell(CreateEndpoint(linkId, true), originalSender)
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
    * Commands broker actor to create an endpoint and connect it to the link actor.
    *
    * @param linkId
    * @param local if `true`, immediately creates an endpoint; if `false`, determines if the command needs
    *              to be forwarded to the cluster node where link actor is running.
    */
  case class CreateEndpoint(linkId: String, local: Boolean = false)

  /**
    * Sent as response to CreateEndpoint.
    *
    * @param linkId
    * @param ep
    */
  case class EndpointCreated(linkId: String, ep: ActorRef)

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

}
