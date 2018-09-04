package controllers

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.Cluster
import akka.pattern._
import akka.util.Timeout
import javax.inject.{Inject, Named, Singleton}
import models.BrokerActor.{GetEndpoint, GetEndpoints}
import models.EndpointActor.{EndpointInfo, GetEndpointInfo}
import play.api.Logger
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Exposes endpoint operations.
  *
  * @param system
  * @param cc
  * @param broker
  */
@Singleton
class EndpointController @Inject()(system: ActorSystem,
                                   cc: ControllerComponents,
                                   @Named("broker") broker: ActorRef) extends AbstractController(cc) {

  protected val log = Logger(getClass)

  implicit protected val timeout = Timeout(5 seconds)

  implicit protected val ec = cc.executionContext

  implicit protected val cluster = Cluster(system)

  def index = Action.async {
    allEndpointsWithInfo map (eps => Ok(views.html.endpoints(cluster.selfMember, eps)))
  }

  def disconnect(linkId: String) = Action.async {
    (broker ? GetEndpoint(linkId)).mapTo[Option[ActorRef]] map { ep =>
      val result = ep.map { ref =>
        ref ! PoisonPill
        Ok(s"Endpoint [$linkId] disconnected")
      }
      result.getOrElse(NotFound(s"Endpoint for link [$linkId] is not found"))
    }
  }

  private def allEndpointsWithInfo = for {
    endpoints <- (broker ? GetEndpoints).mapTo[Iterable[ActorRef]]
    responses <- Future.sequence(endpoints.map { ep =>
      (ep ? GetEndpointInfo).mapTo[EndpointInfo]
    })
  } yield responses
}
