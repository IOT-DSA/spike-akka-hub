package controllers

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.Cluster
import akka.pattern._
import akka.util.Timeout
import javax.inject.{Inject, Named, Singleton}
import models.ClientActor.{ConnectToBroker, DisconnectFromBroker, GetEndpoint, GetEndpoints}
import models.EndpointActor.{EndpointState, GetEndpointState}
import models.IdGenerator.{GenerateIds, IdsGenerated}
import models.InboundMessage
import play.api.Logger
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Simulates client DSLink operations like connect, disconnect, auto-reconnect.
  *
  * The "connection" is simulated by issuing a CreateEndpoint request to the broker node,
  * which automatically routes it to the cluster node which hosts the corresponding LinkActor.
  *
  * @param cc
  */
@Singleton
class ClientController @Inject()(system: ActorSystem,
                                 cc: ControllerComponents,
                                 @Named("client") client: ActorRef,
                                 @Named("idGenerator") idGen: ActorRef) extends AbstractController(cc) {

  protected val log = Logger(getClass)

  implicit protected val cluster = Cluster(system)

  implicit protected val timeout = Timeout(5 seconds)

  implicit protected val ec = cc.executionContext

  private val linkIdPrefix = "dsl"

  def showEndpoints = Action.async {
    allEndpoints map (eps => Ok(views.html.endpoints(cluster.selfMember, eps)))
  }

  def connect(linkId: String) = Action {
    client ! ConnectToBroker(linkId)
    Ok(s"Link [$linkId] connected")
  }

  def connectBatch(count: Int) = Action.async {
    val generated = (idGen ? GenerateIds(count)).mapTo[IdsGenerated]

    generated map { gi =>
      gi.ids foreach { index =>
        val linkId = linkIdPrefix + index
        client ! ConnectToBroker(linkId)
      }
      val message = s"$count links created from [$linkIdPrefix${gi.ids.head}] to [$linkIdPrefix${gi.ids.last}]"
      log.info(message)
      Ok(message)
    }
  }

  def disconnect(linkId: String) = Action {
    client ! DisconnectFromBroker(linkId)
    Ok(s"Link [$linkId] disconnected")
  }

  def testReconnect(linkId: String) = Action.async {
    getEndpoint(linkId).flatMap {
      case Some(ref) => ref ! PoisonPill; Future.successful(Ok(s"Link [$linkId] tested for auto-reconnect"))
      case None      => Future.successful(NotFound(s"Link [$linkId] not found"))
    }
  }

  def send(linkId: String, to: String) = Action.async(parse.text(20)) { request =>
    getEndpoint(linkId).flatMap {
      case Some(ref) =>
        ref ! InboundMessage(to, request.body)
        Future.successful(Ok(s"Inbound message sent to link [$linkId]"))
      case None      =>
        Future.successful(NotFound(s"Link [$linkId] not found"))
    }
  }

  private def allEndpoints = for {
    endpoints <- (client ? GetEndpoints).mapTo[Map[String, ActorRef]]
    states <- Future.sequence(endpoints.map {
      case (linkId, ref) => (ref ? GetEndpointState).mapTo[EndpointState].map(EndpointInfo(linkId, ref, _))
    })
  } yield states

  private def getEndpoint(linkId: String) = (client ? GetEndpoint(linkId)).mapTo[Option[ActorRef]]
}

/**
  * Encapsulates endpoint information.
  *
  * @param linkId
  * @param ref
  * @param state
  */
case class EndpointInfo(linkId: String, ref: ActorRef, state: EndpointState)
