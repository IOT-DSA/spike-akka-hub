package models

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.singleton._
import javax.inject.{Inject, Provider, Singleton}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

import scala.concurrent.duration.Duration

class MainModule extends Module {
  override def bindings(env: Environment, cfg: Configuration): Seq[Binding[_]] = Seq(
    bind[LinkManager].toProvider[LinkManagerProvider],
    bind[ActorRef].qualifiedWith("broker").toProvider[BrokerActorProvider].eagerly,
    bind[ActorRef].qualifiedWith("client").toProvider[ClientActorProvider].eagerly,
    bind[ActorRef].qualifiedWith("idGenerator").toProvider[IdGeneratorProvider].eagerly
  )
}

@Singleton
class LinkManagerProvider @Inject()(system: ActorSystem, cfg: Configuration) extends Provider[LinkManager] {

  private val linkManager = {
    val typeName = cfg.get[String]("akka-hub.shard.name")
    val shardCount = cfg.get[Int]("akka-hub.shard.count")
    val inactivityTimeout = cfg.getOptional[Duration]("akka-hub.link.inactivity.timeout")
    val snapshotInterval = cfg.getOptional[Int]("akka-hub.link.snapshot.interval")
    new LinkManager(system, typeName, shardCount, inactivityTimeout, snapshotInterval)
  }

  override def get(): LinkManager = linkManager
}

@Singleton
class BrokerActorProvider @Inject()(system: ActorSystem, linkManager: LinkManager) extends Provider[ActorRef] {

  private val broker = system.actorOf(BrokerActor.props(linkManager), "broker")

  def get(): ActorRef = broker
}

@Singleton
class ClientActorProvider @Inject()(system: ActorSystem) extends Provider[ActorRef] {

  private val client = system.actorOf(ClientActor.props(true), "client")

  def get(): ActorRef = client
}

@Singleton
class IdGeneratorProvider @Inject()(system: ActorSystem) extends Provider[ActorRef] {

  system.actorOf(ClusterSingletonManager.props(
    singletonProps = IdGenerator.props,
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)), name = "IdGenerator")

  private val idGenerator = system.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "/user/IdGenerator",
    settings = ClusterSingletonProxySettings(system)), name = "IdGeneratorProxy")

  def get(): ActorRef = idGenerator
}