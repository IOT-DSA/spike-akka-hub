package controllers

import akka.actor.{ActorSystem, Address}
import akka.cluster.Cluster
import com.typesafe.config.ConfigRenderOptions
import javax.inject.{Inject, Singleton}
import models.LinkManager
import play.api.Logger
import play.api.mvc.{AbstractController, ControllerComponents}

case class NodeInfo(address: Address, leader: Boolean, uid: Long, roles: Set[String], status: String, dc: String)

@Singleton
class ClusterController @Inject()(system: ActorSystem, cc: ControllerComponents, linkManager: LinkManager)
  extends AbstractController(cc) {

  protected val log = Logger(getClass)

  val cluster = Cluster(system)

  def down(uid: Long) = Action {
    nodeByUID(uid) map { node =>
      cluster.down(node.address)
      log.info(s"Node $node forced down")
      Ok(s"Node ${node.address} forced down")
    } getOrElse (NotFound)
  }

  def leave(uid: Long) = Action {
    nodeByUID(uid) map { node =>
      cluster.leave(node.address)
      log.info(s"Node $node forced to leave the cluster")
      Ok(s"Node ${node.address} forced to leave the cluster")
    } getOrElse (NotFound)
  }

  def clusterInfo = Action {
    val nodes = clusterNodes
    val config = {
      val root = system.settings.config.getConfig("akka").root
      root.render(ConfigRenderOptions.defaults.setComments(false).setOriginComments(false))
    }
    Ok(views.html.cluster(cluster, nodes, system, config))
  }

  private def clusterNodes = {
    val state = cluster.state
    state.members map { m =>
      val status = if (state.unreachable contains m) "Unreachable/" + m.status.toString else m.status.toString
      NodeInfo(m.address, state.leader == Some(m.address), m.uniqueAddress.longUid, m.roles, status, m.dataCenter)
    }
  }

  private def nodeByUID(uid: Long) = cluster.state.members.find(_.uniqueAddress.longUid == uid)
}