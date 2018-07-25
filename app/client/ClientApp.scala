package client

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Mimimcs a client DSLink application.
  */
object ClientApp extends App {

  val config = ConfigFactory.load("client.conf")

  val systemName = config.getString("play.akka.actor-system")
  implicit val system = ActorSystem(systemName, config.resolve)

  Thread.sleep(20000)
  Await.ready(system.terminate, Duration.Inf)

  sys.addShutdownHook {
    system.terminate
    println(s"ClientApp stopped. The uptime time is ${system.uptime} seconds.")
  }
}
