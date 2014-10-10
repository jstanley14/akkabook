import akka.actor.{Props, ActorSystem, ActorRef, Actor}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, BeforeAndAfterAll, WordSpecLike}
import zzz.akka.avionics.{PassengerSupervisor, PassengerProvider}

import scala.concurrent.duration._

object PassengerSupervisorSpec {
  val config = ConfigFactory.parseString(
    """
      |zzz.akka.avionics.passengers = [
      |[ "Testie Jones", "23", "A" ],
      |[ "Testie Ronald", "23", "B" ],
      |[ "Reginald Chumley", "23", "C" ],
      |[ "Franscious Dubouidaeir", "24", "A" ],
      |[ "Bob Jim Tom", "24", "B" ]
      |]
    """.stripMargin)
}

trait TestPassengerProvider extends PassengerProvider {
  override def newPassenger(callButton: ActorRef): Actor =
    new Actor {
      def receive = {
        case m => callButton ! m
      }
    }
}

class PassengerSupervisorSpec
  extends TestKit(ActorSystem("PassengerSupervisorSpec",
                              PassengerSupervisorSpec.config))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with Matchers {
  import PassengerSupervisor._

  override def afterAll(): Unit = {
    system.shutdown()
  }

  "PassengerSupervisor" should {
    "work" in {
      val a = system.actorOf(Props(
        new PassengerSupervisor(testActor) with TestPassengerProvider))
      a ! GetPassengerBroadcaster
      val broadcaster =
        expectMsgType[PassengerBroadcaster].broadcaster
      broadcaster ! "Hithere"
      expectMsg("Hithere")
      expectMsg("Hithere")
      expectMsg("Hithere")
      expectMsg("Hithere")
      expectMsg("Hithere")
      expectNoMsg(100.milliseconds)
      a ! GetPassengerBroadcaster
      expectMsg(PassengerBroadcaster(`broadcaster`))
    }
  }
}