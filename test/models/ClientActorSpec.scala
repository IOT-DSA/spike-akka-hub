//package models
//
//import akka.actor.{ActorRef, ActorSystem, PoisonPill}
//import akka.pattern._
//import akka.testkit.{ImplicitSender, TestActors, TestKit, TestProbe}
//import akka.util.Timeout
//import models.BrokerActor.{CreateEndpoint, EndpointCreated}
//import models.ClientActor.{ConnectToBroker, GetEndpoint, GetEndpoints}
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.{BeforeAndAfterAll, MustMatchers, OptionValues, WordSpecLike}
//
//import scala.concurrent.duration._
//
///**
//  * ClientActor test suite.
//  */
//class ClientActorSpec extends TestKit(ActorSystem()) with ImplicitSender
//  with WordSpecLike with MustMatchers with BeforeAndAfterAll with ScalaFutures
//  with OptionValues {
//
//  implicit val timeout = Timeout(5 seconds)
//
//  override def afterAll = TestKit.shutdownActorSystem(system)
//
//  val client = system.actorOf(ClientActor.props(true), "client")
//
//  val broker = createProbe("broker")
//
//  val ep = system.actorOf(TestActors.blackholeProps)
//
//  "ClientActor" should {
//    "handle ConnectToBroker command" in {
//      client ! ConnectToBroker("abc")
//      broker.expectMsg(CreateEndpoint("abc"))
//    }
//    "handle EndpointCreated callback" in {
//      client ! EndpointCreated("abc", ep)
//      whenReady((client ? GetEndpoints).mapTo[Map[String, ActorRef]]) {
//        _ mustBe Map("abc" -> ep)
//      }
//    }
//    "handle autoconnect on endpoint termination" in {
//      ep ! PoisonPill
//      broker.expectMsg(CreateEndpoint("abc"))
//    }
//    "handle GetEndpoints query" in {
//      whenReady((client ? GetEndpoints).mapTo[Map[String, ActorRef]]) {
//        _.keys mustBe Set("abc")
//      }
//    }
//    "handle GetEndpoint query" in {
//      whenReady((client ? GetEndpoint("abc")).mapTo[Option[ActorRef]]) {
//        _ mustBe defined
//      }
//      whenReady((client ? GetEndpoint("abc")).mapTo[Option[ActorRef]]) {
//        _ mustBe empty
//      }
//    }
//    "handle DisconnectFromBroker" in {
//      whenReady((client ? GetEndpoints).mapTo[Map[String, ActorRef]]) {
//        _ mustBe empty
//      }
//    }
//  }
//
//  private def createProbe(name: String) = {
//    val broker = TestProbe()
//    system.actorOf(TestActors.forwardActorProps(broker.ref), name)
//    broker
//  }
//}
