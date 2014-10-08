import akka.actor._
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpecLike}
import zzz.akka.avionics._
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

class FakePilot extends Actor {
  override def receive = { case _ => }
}

object PilotsSpec {
  val copilotName = "Mary"
  val pilotName = "Mark"
  val configStr = s"""
       |zzz.akka.avionics.flightcrew.copilotName = "$copilotName"
       |zzz.akka.avionics.flightcrew.pilotName = "$pilotName"
     """.stripMargin
}

class PilotsSpec extends TestKit(ActorSystem("PilotsSpec",
                   ConfigFactory.parseString(PilotsSpec.configStr)))
                 with ImplicitSender with WordSpecLike with Matchers {
  import PilotsSpec._
  import Plane._

  def nilActor: ActorRef = TestProbe().ref
  val pilotPath = s"/user/TestPilots/$pilotName"
  val copilotPath = s"/user/TestPilots/$copilotName"

  def pilotsReadyToGo(): ActorRef = {
    implicit val askTimeout = Timeout(4.seconds)
    val a = system.actorOf(
      Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
        def childStarter(): Unit = {
          context.actorOf(Props[FakePilot], pilotName)
          context.actorOf(Props(
            new Copilot(testActor, nilActor, nilActor)), copilotName)
        }
      }), "TestPilots")
    Await.result(a ? IsolatedLifeCycleSupervisor.WaitForStart, 3.seconds)
    system.actorFor(copilotPath) ! Pilots.ReadyToGo
    a
  }

  "Copilot" should {
    "take control when the Pilot dies" in {
      pilotsReadyToGo()
      system.actorFor(pilotPath) ! PoisonPill
      expectMsg(GiveMeControl)
      lastSender should be (system.actorFor(copilotPath))
    }
  }
}

