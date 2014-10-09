package zzz.akka.avionics

import akka.actor._

import scala.concurrent.Await
import scala.concurrent.duration._

trait PilotProvider {
  def newPilot(plane: ActorRef, autopilot: ActorRef,
               heading: ActorRef, altimeter: ActorRef, wayPoints: ActorRef): Actor =
    new Pilot(plane, autopilot, heading, altimeter, wayPoints) with DrinkingProvider with FlyingProvider
  def newCopilot(plane: ActorRef, autopilot: ActorRef, altimeter: ActorRef): Actor =
    new Copilot(plane, autopilot, altimeter)
  def newAutopilot(plane: ActorRef): Actor = new Autopilot(plane)
}

object Pilots {
  case object ReadyToGo
  case object RelinquishControl
  case object RequestCopilot
  case object TakeControl
  case class CopilotReference(copilot: ActorRef)
}

object Pilot {
  import FlyingBehavior._
  import ControlSurfaces._

  val tipsyCalcElevator: Calculator = { (target, status) =>
    val msg = calcElevator(target, status)
    msg match {
      case StickForward(amt) => StickForward(amt * 1.03f)
      case StickBack(amt) => StickBack(amt * 1.03f)
      case m => m
    }
  }

  val tipsyCalcAilerons: Calculator = { (target, status) =>
    val msg = calcAilerons(target, status)
    msg match {
      case StickLeft(amt) => StickForward(amt * 1.03f)
      case StickRight(amt) => StickBack(amt * 1.03f)
      case m => m
    }
  }

  val zaphodCalcElevator: Calculator = { (target, status) =>
    val msg = calcElevator(target, status)
    msg match {
      case StickForward(amt) => StickBack(1f)
      case StickBack(amt) => StickForward(1f)
      case m => m
    }
  }

  val zaphodCalcAilerons: Calculator = { (target, status) =>
    val msg = calcAilerons(target, status)
    msg match {
      case StickLeft(amt) => StickRight(1f)
      case StickRight(amt) => StickLeft(1f)
      case m => m
    }
  }
}

class Pilot(plane: ActorRef,
            autopilot: ActorRef,
            heading: ActorRef,
            altimeter: ActorRef,
            wayPoints: ActorRef) extends Actor with ActorLogging {
  this: DrinkingProvider with FlyingProvider =>
  import Pilots._
  import Pilot._
  import Plane._
  import Altimeter._
  import ControlSurfaces._
  import DrinkingBehavior._
  import FlyingBehavior._
  import FSM._

//  var copilot: ActorRef = context.system.deadLetters
  val copilotName = context.system.settings.config.getString(
    "zzz.akka.avionics.flightcrew.copilotName")

  def setCourse(flyer: ActorRef): Unit = {
    flyer ! Fly(CourseTarget(20000, 250, System.currentTimeMillis + 30000))
  }

  override def preStart(): Unit = {
    context.actorOf(newDrinkingBehavior(self), "DrinkingBehavior")
    context.actorOf(newFlyingBehavior(plane, heading, altimeter, wayPoints),
                    "FlyingBehavior")
  }

  def bootstrap: Receive = {
    case ReadyToGo =>
      val copilot = context.actorFor("../" + copilotName)
      val flyer = context.actorFor("FlyingBehavior")
      flyer ! SubscribeTransitionCallBack(self)
      setCourse(flyer)
      context.become(sober(copilot, flyer))
  }

  def sober(copilot: ActorRef, flyer: ActorRef): Receive = {
    case FeelingSober =>
    case FeelingTipsy => becomeTipsy(copilot, flyer)
    case FeelingLikeZaphod => becomeZaphod(copilot, flyer)
  }

  def tipsy(copilot: ActorRef, flyer: ActorRef): Receive = {
    case FeelingSober => becomeSober(copilot, flyer)
    case FeelingTipsy =>
    case FeelingLikeZaphod => becomeZaphod(copilot, flyer)
  }

  def zaphod(copilot: ActorRef, flyer: ActorRef): Receive = {
    case FeelingSober => becomeSober(copilot, flyer)
    case FeelingTipsy => becomeTipsy(copilot, flyer)
    case FeelingLikeZaphod =>
  }

  def idle: Receive = { case _ => }

  def becomeSober(copilot: ActorRef, flyer: ActorRef) = {
    flyer ! NewElevatorCalculator(calcElevator)
    flyer ! NewBankCalculator(calcAilerons)
    context.become(sober(copilot, flyer))
  }

  def becomeTipsy(copilot: ActorRef, flyer: ActorRef) = {
    flyer ! NewElevatorCalculator(tipsyCalcElevator)
    flyer ! NewBankCalculator(tipsyCalcAilerons)
    context.become(tipsy(copilot, flyer))
  }

  def becomeZaphod(copilot: ActorRef, flyer: ActorRef) = {
    flyer ! NewElevatorCalculator(zaphodCalcElevator)
    flyer ! NewBankCalculator(zaphodCalcAilerons)
    context.become(zaphod(copilot, flyer))
  }

  override def unhandled(msg: Any) : Unit = {
    msg match {
      case Transition(_, _, Flying) =>
        setCourse(sender)
      case Transition(_, _, Idle) =>
        context.become(idle)
      case Transition(_, _, _) =>
      case CurrentState(_, _) =>
      case m => super.unhandled(m)
    }
  }

  def receive = bootstrap
}

class Copilot(plane: ActorRef, autopilot: ActorRef, altimeter: ActorRef)
  extends Actor {

  import Pilots._
  import Plane._

  var controls: ActorRef = context.system.deadLetters
  var pilot: ActorRef = context.system.deadLetters
  val pilotName = context.system.settings.config.getString(
    "zzz.akka.avionics.flightcrew.pilotName")

  def receive = {
    case ReadyToGo =>
      pilot = context.actorFor("../" + pilotName)
      context.watch(pilot)

    case Terminated(_) => plane ! GiveMeControl

    case TakeControl => plane ! GiveMeControl

    case Controls(controlSurfaces) =>
      controls = controlSurfaces
  }
}

class Autopilot(plane: ActorRef) extends Actor {
  import Pilots._
  import Plane._
  var controls: ActorRef = context.system.deadLetters
  var pilot: ActorRef = context.system.deadLetters
  var copilot: ActorRef = context.system.deadLetters
  val pilotName = context.system.settings.config.getString(
    "zzz.akka.avionics.flightcrew.pilotName")
  val copilotName = context.system.settings.config.getString(
    "zzz.akka.avionics.flightcrew.copilotName")

  def receive = {
    case ReadyToGo =>
      plane ! RequestCopilot

    case CopilotReference(copilotFromPlane) =>
      copilot = copilotFromPlane
      context.watch(copilot)

    case Terminated(_) => plane ! GiveMeControl

    case TakeControl => plane ! GiveMeControl

    case Controls(controlSurfaces) => controls = controlSurfaces

  }
}