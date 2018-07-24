package controllers

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.Cluster
import akka.cluster.sharding.ShardRegion
import akka.pattern._
import akka.util.Timeout
import javax.inject.{Inject, Named, Singleton}
import models.BrokerActor.{CreateEndpoint, EndpointCreated, EndpointRemoved, RemoveEndpoint}
import models.LinkActor._
import models.LinkManager
import play.api.mvc.{AbstractController, ControllerComponents}
import play.api.{Configuration, Logger}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal

/**
  * Exposes link-related operations.
  *
  * @param system
  * @param cc
  * @param linkManager
  * @param configuration
  * @param broker
  * @param idGen
  */
@Singleton
class LinkController @Inject()(system: ActorSystem,
                               cc: ControllerComponents,
                               linkManager: LinkManager,
                               configuration: Configuration,
                               @Named("broker") broker: ActorRef,
                               @Named("idGenerator") idGen: ActorRef) extends AbstractController(cc) {

  import ShardRegion._

  protected val log = Logger(getClass)

  implicit protected val cluster = Cluster(system)

  implicit protected val ec = cc.executionContext

  implicit protected val timeout = Timeout(5 seconds)

  private val linkIdPrefix = "link"

  def showLinks = Action.async {
    val fcsrs = (linkManager.region ? GetShardRegionState).mapTo[CurrentShardRegionState]
    val fcss = (linkManager.region ? GetClusterShardingStats(timeout.duration)).mapTo[ClusterShardingStats]
    for {
      csrs <- fcsrs
      css <- fcss
      links <- linkList
    } yield Ok(views.html.links(links, cluster.selfMember, csrs, css))
  }

  def linkInfo(linkId: String) = Action.async {
    for {
      linkInfo <- linkManager.ask(linkId, GetLinkInfo).mapTo[LinkInfo]
    } yield Ok(views.html.link(cluster.selfMember, linkInfo))
  }

  def connect(linkId: String) = Action.async {
    (broker ? CreateEndpoint(linkId)).mapTo[EndpointCreated] map { ec =>
      Ok(s"Link [$linkId] connected to endpoint")
    }
  }

  def disconnect(linkId: String) = Action.async {
    val result = (broker ? RemoveEndpoint(linkId)).mapTo[EndpointRemoved]
    result.map(_ => Ok(s"Link [$linkId] disconnected from endpoint")).recover {
      case NonFatal(e) => BadRequest(e.getMessage)
    }
  }

  def stop(linkId: String) = Action {
    linkManager.tell(linkId, Stop)
    Ok(s"Link [$linkId] stopped")
  }

  private def linkList() = {
    val maxLinksToShow = configuration.get[Int]("akka-hub.link.view.maxRows")

    val fcsrs = (linkManager.region ? GetShardRegionState).mapTo[CurrentShardRegionState]

    for {
      allLinkIds <- fcsrs.map(_.shards.flatMap(_.entityIds))
      linkIds = Random.shuffle(allLinkIds).take(maxLinksToShow)
      infos <- Future.sequence(linkIds.map(linkId => linkManager.ask(linkId, GetLinkInfo).mapTo[LinkInfo]))
    } yield infos.toList.sortBy(li => li.shardId + li.linkId)
  }
}
