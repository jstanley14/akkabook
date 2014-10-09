import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestFSMRef, TestProbe, TestKit}
import org.scalatest.{WordSpecLike, Matchers}
import zzz.akka.avionics.Altimeter.AltitudeUpdate
import zzz.akka.avionics.FlyingBehavior
import zzz.akka.avionics.FlyingBehavior._
import zzz.akka.avionics.HeadingIndicator.HeadingUpdate
import zzz.akka.avionics.Plane.Controls
import akka.actor.FSM


class FlyingBehaviorSpec extends TestKit(ActorSystem("FlyingBehaviorSpec"))
  with WordSpecLike with Matchers {
  def nilActor: ActorRef = TestProbe().ref
  val target = CourseTarget(100, 100, 60000)

  def fsm(plane: ActorRef = nilActor,
          heading: ActorRef = nilActor,
          altimeter: ActorRef = nilActor) = {
    TestFSMRef(new FlyingBehavior(plane, heading, altimeter))
  }

  "FlyingBehavior" should {
    "start in the Idle state and with the Uninitialized data" in {
      val a = fsm()
      a.stateName should be (Idle)
      a.stateData should be (Uninitialized)
    }
  }

  "PreparingToFly state" should {
    "stay in PreparingToFly state when only a HeadingUpdate is received" in {
      val a = fsm()
      a ! Fly(target)
      a ! HeadingUpdate(20)
      a.stateName should be (PreparingToFly)
      val sd = a.stateData.asInstanceOf[FlightData]
      sd.status.altitude should be (-1)
      sd.status.heading should be (20)
    }
    "move to Flying state when all parts are received" in {
      val a = fsm()
      a ! Fly(target)
      a ! HeadingUpdate(20)
      a ! AltitudeUpdate(20)
      a ! Controls(testActor)
      a.stateName should be (Flying)
      val sd = a.stateData.asInstanceOf[FlightData]
      sd.controls should be (testActor)
      sd.status.altitude should be (20)
      sd.status.heading should be (20)
    }
  }

  "transitioning to Flying state" should {
    "create the Adjustment timer" in {
      val a = fsm()
      a.setState(PreparingToFly)
      a.setState(Flying)
      a.isTimerActive("Adjustment") should be (true)
    }
  }
}