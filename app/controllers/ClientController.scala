package controllers

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.pattern._
import akka.util.Timeout
import client.ClientActor.{ClientInfo, GetClientInfo}
import client.ClientManagerActor.{ConnectClientToBroker, DisconnectClientFromBroker, GetClient, GetClients}
import client.IdGenerator.{GenerateIds, IdsGenerated}
import javax.inject.{Inject, Named, Singleton}
import play.api.Logger
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Simulates client DSLink operations like connect, disconnect, auto-reconnect.
  */
@Singleton
class ClientController @Inject()(system: ActorSystem,
                                 cc: ControllerComponents,
                                 @Named("clientManager") clientMgr: ActorRef,
                                 @Named("idGenerator") idGen: ActorRef) extends AbstractController(cc) {

  protected val log = Logger(getClass)

  implicit protected val timeout = Timeout(5 seconds)

  implicit protected val ec = cc.executionContext

  private val linkIdPrefix = "dsa"

  def index = Action.async {
    allClientsWithInfo map (clients => Ok(views.html.clients(clients)))
  }

  def connect(linkId: String) = Action {
    clientMgr ! ConnectClientToBroker(linkId)
    Ok(s"Link [$linkId] ordered to connect")
  }

  def disconnect(linkId: String) = Action {
    clientMgr ! DisconnectClientFromBroker(linkId)
    Ok(s"Link [$linkId] ordered to disconnect")
  }

  def testReconnect(linkId: String) = Action.async {
    clientWithInfo(linkId) map { info =>
      val result = info.flatMap(_.endpoint).map { ep =>
        ep ! PoisonPill
        Ok(s"Link [$linkId] tested for auto-reconnect")
      }
      result.getOrElse(NotFound(s"Endpoint for link [$linkId] is not found"))
    }
  }

  def connectBatch(count: Int) = Action.async {
    val generated = (idGen ? GenerateIds(count)).mapTo[IdsGenerated]

    generated map { gi =>
      gi.ids foreach { index =>
        val linkId = linkIdPrefix + index
        clientMgr ! ConnectClientToBroker(linkId)
      }
      val message = s"$count links created from [$linkIdPrefix${gi.ids.head}] to [$linkIdPrefix${gi.ids.last}]"
      log.info(message)
      Ok(message)
    }
  }

  private def allClientsWithInfo = for {
    clients <- (clientMgr ? GetClients).mapTo[Iterable[ActorRef]]
    responses <- Future.sequence(clients.map { client =>
      (client ? GetClientInfo).mapTo[ClientInfo]
    })
  } yield responses

  private def clientWithInfo(linkId: String) = {
    val client = (clientMgr ? GetClient(linkId)).mapTo[Option[ActorRef]]
    client.flatMap {
      case Some(c) => (c ? GetClientInfo).mapTo[ClientInfo].map(Some(_))
      case None    => Future.successful(None)
    }
  }
}