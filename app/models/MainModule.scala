package models

import akka.actor.{ActorRef, ActorSystem}
import javax.inject.{Inject, Provider, Singleton}
import org.slf4j.LoggerFactory
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

import scala.concurrent.duration.Duration

/**
  * Broker module bindings.
  */
class MainModule extends Module {
  private val log = LoggerFactory.getLogger(getClass)

  override def bindings(env: Environment, cfg: Configuration): Seq[Binding[_]] = {
    log.info("Loading broker settings...")
    Seq(
      bind[LinkManager].toProvider[LinkManagerProvider],
      bind[ActorRef].qualifiedWith("broker").toProvider[BrokerActorProvider].eagerly
    )
  }
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