package client

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.singleton._
import javax.inject.{Inject, Provider, Singleton}
import org.slf4j.LoggerFactory
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

import scala.concurrent.duration.Duration

/**
  * Client module bindings.
  */
class ClientModule extends Module {

  private val log = LoggerFactory.getLogger(getClass)

  override def bindings(env: Environment, cfg: Configuration): Seq[Binding[_]] = {
    log.info("Loading client settings...")
    Seq(
      bind[ActorRef].qualifiedWith("clientManager").toProvider[ClientManagerProvider].eagerly,
      bind[ActorRef].qualifiedWith("idGenerator").toProvider[IdGeneratorProvider].eagerly
    )
  }
}

/**
  * Creates a client manager, that will be responsible for creating "dslinks" to connect to the broker.
  *
  * @param system
  * @param cfg
  */
@Singleton
class ClientManagerProvider @Inject()(system: ActorSystem, cfg: Configuration) extends Provider[ActorRef] {

  private val timeout = cfg.getOptional[Duration]("client.reconnect.timeout")

  private val clientManager = system.actorOf(ClientManagerActor.props(timeout), "clientManager")

  def get(): ActorRef = clientManager
}

/**
  * Creates a cluster-wide singleton instance of IdGenerator actor.
  *
  * @param system
  */
@Singleton
class IdGeneratorProvider @Inject()(system: ActorSystem) extends Provider[ActorRef] {

  system.actorOf(ClusterSingletonManager.props(
    singletonProps = IdGenerator.props,
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system).withRole("client")), name = "IdGenerator")

  private val idGenerator = system.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "/user/IdGenerator",
    settings = ClusterSingletonProxySettings(system).withRole("client")), name = "IdGeneratorProxy")

  def get(): ActorRef = idGenerator
}