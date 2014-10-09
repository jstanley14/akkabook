package zzz.akka.avionics

import akka.actor._
import scala.concurrent.duration._

trait FlyingProvider {
  def newFlyingBehavior(plane: ActorRef,
                        heading: ActorRef,
                        altimeter: ActorRef,
                        wayPoints: ActorRef): Props =
    Props(new FlyingBehavior(plane, heading, altimeter, wayPoints))
}

object FlyingBehavior {
  import ControlSurfaces._

  sealed trait State
  case object Idle extends State
  case object Flying extends State
  case object PreparingToFly extends State

  case class CourseTarget(altitude: Double, heading: Float, byMillis: Long)
  case class CourseStatus(altitude: Double,
                          heading: Float,
                          headingSinceMS: Long,
                          altitudeSinceMS: Long)

  type Calculator = (CourseTarget, CourseStatus) => Any

  sealed trait Data
  case object Uninitialized extends Data
  case class FlightData(controls: ActorRef,
                        elevCalc: Calculator,
                        bankCalc: Calculator,
                        target: CourseTarget,
                        status: CourseStatus) extends Data

  case class Fly(target: CourseTarget)
  case class NewWaypoint(target: CourseTarget)

  case class NewElevatorCalculator(f: Calculator)
  case class NewBankCalculator(f: Calculator)

  def currentMS = System.currentTimeMillis

  def calcElevator(target: CourseTarget, status: CourseStatus): Any = {
    val alt = (target.altitude - status.altitude).toFloat
    val dur = target.byMillis - status.altitudeSinceMS
    if (alt < 0) StickForward((alt / dur) * -1)
    else StickBack(alt / dur)
  }

  def calcAilerons(target: CourseTarget, status: CourseStatus): Any = {
    import scala.math.{abs, signum}
    val diff = target.heading - status.heading
    val dur = target.byMillis - status.headingSinceMS
    val amount = if (abs(diff) < 180) diff
                 else signum(diff) * (abs(diff) - 360f)
    if (amount > 0) StickRight(amount / dur)
    else StickLeft((amount / dur) * -1)
  }
}

class FlyingBehavior(plane: ActorRef,
                     heading: ActorRef,
                     altimeter: ActorRef,
                     wayPoints: ActorRef) extends Actor with ActorLogging
  with FSM[FlyingBehavior.State, FlyingBehavior.Data] {
  import FSM._
  import FlyingBehavior._
  import Pilots._
  import Plane._
  import Altimeter._
  import HeadingIndicator._
  import EventSource._

  case object Adjust
  case object PrepTimeout

  startWith(Idle, Uninitialized)

  def adjust(flightData: FlightData): FlightData = {
    val FlightData(c, elevCalc, bankCalc, t, s) = flightData
    c ! elevCalc(t, s)
    c ! bankCalc(t, s)
    flightData
  }

  when(Idle) {
    case Event(Fly(target), _) =>
      goto(PreparingToFly) using FlightData(context.system.deadLetters,
                                            calcElevator, calcAilerons,
                                            target, CourseStatus(-1,-1,0,0))
  }

  when(PreparingToFly)(transform {
    case Event(HeadingUpdate(head), d: FlightData) =>
      stay using d.copy(status = d.status.copy(heading = head,
                                               headingSinceMS = currentMS))
    case Event(AltitudeUpdate(alt), d: FlightData) =>
      stay using d.copy(status = d.status.copy(altitude = alt,
                                               altitudeSinceMS = currentMS))
    case Event(Controls(ctrls), d: FlightData) =>
      stay using d.copy(controls = ctrls)
    case Event(PrepTimeout, _) =>
      plane ! LostControl
      goto (Idle)
  } using {
    case s if prepComplete(s.stateData) =>
      s.copy(stateName = Flying)
  })

  when (Flying) {
    case Event(AltitudeUpdate(alt), d: FlightData) =>
      stay using d.copy(status = d.status.copy(altitude = alt,
                                               altitudeSinceMS = currentMS))
    case Event(HeadingUpdate(head), d: FlightData) =>
      stay using d.copy(status = d.status.copy(heading = head,
                                               headingSinceMS = currentMS))
    case Event(Adjust, flightData: FlightData) =>
      stay using adjust(flightData)

    case Event(NewBankCalculator(f), d: FlightData) =>
      stay using d.copy(bankCalc = f)

    case Event(NewElevatorCalculator(f), d: FlightData) =>
      stay using d.copy(elevCalc = f)

    case Event(NewWaypoint(targ), d: FlightData) =>
      log info s"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! $targ"
      stay using d.copy(target = targ)

    case Event(Fly(targ), d: FlightData) =>
      stay using d.copy(target = targ)
  }

  whenUnhandled {
    case Event(RelinquishControl, _) =>
      goto (Idle)
  }

  onTransition {
    case Idle -> PreparingToFly =>
      plane ! GiveMeControl
      heading ! RegisterListener(self)
      altimeter ! RegisterListener(self)
      wayPoints ! RegisterListener(self)
      setTimer("PrepTimeout", PrepTimeout, 5.seconds)

    case PreparingToFly -> Flying =>
      cancelTimer("PrepTimeout")
      setTimer("Adjustment", Adjust, 200.milliseconds, repeat = true)

    case Flying -> _ =>
      cancelTimer("Adjustment")

    case _ -> Idle =>
      heading ! UnregisterListener(self)
      altimeter ! UnregisterListener(self)
      wayPoints ! UnregisterListener(self)
  }

  def prepComplete(data: Data): Boolean = {
    data match {
      case FlightData(c, _, _, _, s) =>
        if (!c.isTerminated && s.heading != -1f && s.altitude != -1f)
          true
        else
          false
      case _ =>
        false
    }
  }

  initialize()
}