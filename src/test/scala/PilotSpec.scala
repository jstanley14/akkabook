import akka.actor._
import akka.testkit.{TestActorRef, TestProbe, ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpecLike}
import zzz.akka.avionics.DrinkingBehavior.{FeelingTipsy, FeelingLikeZaphod}
import zzz.akka.avionics.FlyingBehavior.{NewBankCalculator, NewElevatorCalculator}
import zzz.akka.avionics.Pilots.{ReadyToGo, CopilotReference, RequestCopilot}
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


  def makePilot() = {
    val a = TestActorRef[Pilot](
      new Pilot(nilActor, nilActor, nilActor, nilActor, nilActor) with DrinkingProvider
                                                                  with FlyingProvider)
    (a, a.underlyingActor)
  }

  def nilActor: ActorRef = TestProbe().ref
  def pilotPath(actSys: String) = s"/user/$actSys/$pilotName"
  def copilotPath(actSys: String) = s"/user/$actSys/$copilotName"
  def autopilotPath(actSys: String) = s"/user/$actSys/Autopilot"

  def pilotsReadyToGo(systemName: String): ActorRef = {
    implicit val askTimeout = Timeout(4.seconds)
    val a = system.actorOf(
      Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
        def childStarter(): Unit = {
          context.actorOf(Props[FakePilot], pilotName)
          context.actorOf(Props(
            new Copilot(testActor, nilActor, nilActor)), copilotName)
          context.actorOf(Props(new Autopilot(testActor)), "Autopilot")
        }
      }), systemName)
    Await.result(a ? IsolatedLifeCycleSupervisor.WaitForStart, 3.seconds)
    system.actorFor(copilotPath(systemName)) ! Pilots.ReadyToGo
    a
  }

  def autopilotTestSetup(systemName: String): ActorRef = {
    implicit val askTimeout = Timeout(4.seconds)
    val a = system.actorOf(
      Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
        def childStarter(): Unit = {
          context.actorOf(Props[FakePilot], pilotName)
          context.actorOf(Props(
            new Copilot(testActor, nilActor, nilActor)), copilotName)
          context.actorOf(Props(new Autopilot(testActor)), "Autopilot")
        }
      }), systemName)
    Await.result(a ? IsolatedLifeCycleSupervisor.WaitForStart, 3.seconds)
    system.actorFor(autopilotPath(systemName)) ! Pilots.ReadyToGo
    a
  }

  "Pilot.becomeZaphod" should {
    "send new zaphodCalcElevator and zaphodCalcAilerons to FlyingBehavior" in {
      val (ref, a) = makePilot()
      a.context.become(a.sober(nilActor, testActor))
      ref ! FeelingLikeZaphod
      expectMsgAllOf(
        NewElevatorCalculator(Pilot.zaphodCalcElevator),
        NewBankCalculator(Pilot.zaphodCalcAilerons)
      )
    }
  }

  "Pilot.becomeTipsy" should {
    "send new tipsyCalcElevator and tipsyCalcAilerons to FlyingBehavior" in {
      val (ref, a) = makePilot()
      a.context.become(a.sober(nilActor, testActor))
      ref ! FeelingTipsy
      expectMsgAllClassOf(classOf[NewElevatorCalculator],
        classOf[NewBankCalculator]) foreach {
          case NewElevatorCalculator(f) =>
            f should be (Pilot.tipsyCalcElevator)
          case NewBankCalculator(f) =>
            f should be (Pilot.tipsyCalcAilerons)
      }
    }
  }

  "Copilot" should {
    "take control when the Pilot dies" in {
      pilotsReadyToGo("CopilotTest")
      system.actorFor(pilotPath("CopilotTest")) ! PoisonPill
      expectMsg(GiveMeControl)
      lastSender should be (system.actorFor(copilotPath("CopilotTest")))
    }
  }

  "Autopilot" should {
    "take control when the Copilot dies" in {
      autopilotTestSetup("AutopilotTest")
      expectMsg(RequestCopilot)
      lastSender should be (system.actorFor(autopilotPath("AutopilotTest")))
      lastSender ! CopilotReference(system.actorFor(copilotPath("AutopilotTest")))
      system.actorFor(copilotPath("AutopilotTest")) ! PoisonPill
      expectMsg(GiveMeControl)
      lastSender should be (system.actorFor(autopilotPath("AutopilotTest")))
    }
  }
}

