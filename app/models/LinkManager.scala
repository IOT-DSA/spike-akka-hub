package models

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.cluster.sharding.ShardRegion.StartEntity
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.util.Timeout
import javax.inject.Inject

import scala.concurrent.Future
import scala.concurrent.duration.Duration

final case class EntityEnvelope(entityId: String, msg: Any)

class LinkManager @Inject()(system: ActorSystem, typeName: String, shardCount: Int,
                            inactivityTimeout: Option[Duration],
                            snapshotInterval: Option[Int]) {

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(linkId, payload) => (linkId, payload)
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(linkId, _) => (math.abs(linkId.hashCode) % shardCount).toString
    case StartEntity(linkId)       => (math.abs(linkId.hashCode) % shardCount).toString
  }

  val region = {
    val sharding = ClusterSharding(system)
    val settings = ClusterShardingSettings(system).withRole("broker")
    sharding.start(
      typeName,
      LinkActor.props(this, inactivityTimeout, snapshotInterval),
      settings,
      extractEntityId,
      extractShardId)
  }

  def tell(linkId: String, message: Any, sender: ActorRef = Actor.noSender): Unit =
    region.tell(wrap(linkId, message), sender)

  def ask(linkId: String, msg: Any)(implicit timeout: Timeout, sender: ActorRef = Actor.noSender): Future[Any] =
    akka.pattern.ask(region, wrap(linkId, msg), sender)(timeout).mapTo[Any]

  private def wrap(linkId: String, msg: Any) = EntityEnvelope(linkId, msg)
}