package zzz.akka.avionics

import akka.actor.{ActorLogging, Terminated, ActorRef, Actor}

import scala.concurrent.Await
import scala.concurrent.duration._

trait PilotProvider {
  def newPilot(plane: ActorRef, autopilot: ActorRef,
               controls: ActorRef, altimeter: ActorRef): Actor =
    new Pilot(plane, autopilot, controls, altimeter)
  def newCopilot(plane: ActorRef, autopilot: ActorRef, altimeter: ActorRef): Actor =
    new Copilot(plane, autopilot, altimeter)
  def newAutopilot(plane: ActorRef): Actor = new Autopilot(plane)
}

object Pilots {
  case object ReadyToGo
  case object RelinquishControl
  case object RequestCopilot
  case class CopilotReference(copilot: ActorRef)
}

class Pilot(plane: ActorRef,
            autopilot: ActorRef,
            var controls: ActorRef,
            altimeter: ActorRef) extends Actor {
  import Pilots._
  import Plane._
  var copilot: ActorRef = context.system.deadLetters
  val copilotName = context.system.settings.config.getString(
    "zzz.akka.avionics.flightcrew.copilotName")

  def receive = {
    case ReadyToGo =>
      plane ! GiveMeControl
      copilot = context.actorFor("../" + copilotName)
    case Controls(controlSurfaces) =>
      controls = controlSurfaces
  }
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

    case Controls(controlSurfaces) => controls = controlSurfaces

  }
}