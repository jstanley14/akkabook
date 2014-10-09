package zzz.akka.avionics

import akka.actor._
import akka.util.Timeout
import zzz.akka.avionics.ControlSurfaces.HasControl
import zzz.akka.avionics.HeadingIndicator.HeadingUpdate
import zzz.akka.avionics.IsolatedLifeCycleSupervisor.WaitForStart
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import akka.pattern.ask

object Plane {
  case object GiveMeControl
  case object LostControl
  case class Controls(controls: ActorRef)

  def apply() = new Plane with AltimeterProvider
                          with PilotProvider
                          with LeadFlightAttendantProvider
                          with HeadingIndicatorProvider
}

class Plane extends Actor with ActorLogging { this: AltimeterProvider
                                         with PilotProvider
                                         with LeadFlightAttendantProvider
                                         with HeadingIndicatorProvider =>
  import Altimeter._
  import Plane._
  import Pilots._
  import WeatherBehavior.WindOffset

  implicit val askTimeout = Timeout(1.second)

  def actorForControls(name: String): ActorRef =
    context.actorFor("Equipment/" + name)

  def actorForPilots(name: String): ActorRef =
    context.actorFor("Pilots/" + name)

  def startEquipment(): Unit = {
    val plane = self
    val controls = context.actorOf(
      Props(new IsolatedResumeSupervisor with OneForOneStrategyFactory {
        def childStarter(): Unit = {
          val alt = context.actorOf(Props(newAltimeter), "Altimeter")
          val heading = context.actorOf(Props(newHeadingIndicator),
                                        "HeadingIndicator")
          context.actorOf(Props(newAutopilot(plane)), "Autopilot")
          context.actorOf(Props(new ControlSurfaces(plane, alt, heading)),
                                "ControlSurfaces")
          context.actorOf(Props(WayPoint()), "WayPoints")
        }
      }), "Equipment")
    Await.result(controls ? WaitForStart, 1.second)
  }

  def startPeople(): Unit = {
    val plane = self

 //   val controls = actorForControls("ControlSurfaces")
    val autopilot = actorForControls("Autopilot")
    val altimeter = actorForControls("Altimeter")
    val heading = actorForControls("HeadingIndicator")
    val wayPoints = actorForControls("WayPoints")
    val people = context.actorOf(
      Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
        def childStarter(): Unit = {
          context.actorOf(Props(
            newPilot(plane, autopilot, heading, altimeter, wayPoints)), pilotName)
          context.actorOf(Props(
            newCopilot(plane, autopilot, altimeter)), copilotName)
        }
      }), "Pilots")
    context.actorOf(Props(newLeadFlightAttendant), leadAttendantName)
    Await.result(people ? WaitForStart, 1.second)
  }

  def startWeather(): Unit = {
    val plane = self
    val weather = context.actorOf(
      Props(new IsolatedResumeSupervisor with OneForOneStrategyFactory {
        def childStarter(): Unit = {
          context.actorOf(Props(WeatherBehavior(plane)), "Wind")
        }
      }), "Weather")
    Await.result(weather ? WaitForStart, 1.second)
  }

  val config = context.system.settings.config
  val cfgstr = "zzz.akka.avionics.flightcrew"
  val pilotName = config.getString(s"$cfgstr.pilotName")
  val copilotName = config.getString(s"$cfgstr.copilotName")
  val leadAttendantName = config.getString(s"$cfgstr.leadAttendantName")

  override def preStart(): Unit = {
    import EventSource.RegisterListener
    import Pilots.ReadyToGo

    startEquipment()
    startPeople()
    startWeather()

    actorForControls("Altimeter") ! RegisterListener(self)
    actorForControls("HeadingIndicator") ! RegisterListener(self)
    actorForPilots(pilotName) ! ReadyToGo
    actorForPilots(copilotName) ! ReadyToGo
    actorForControls("Autopilot") ! ReadyToGo
  }

  def receive = {
    case GiveMeControl =>
      log info "Plane giving control."
      val controls: ActorRef = actorForControls("ControlSurfaces")
      controls ! HasControl(sender)
      sender ! Controls(controls)

    case LostControl =>
      actorForControls("Autopilot") ! TakeControl

    case AltitudeUpdate(altitude) => println("altitude = " + altitude)

    case HeadingUpdate(heading) => println("heading = " + heading)

    case RequestCopilot =>
      sender ! CopilotReference(actorForPilots(copilotName))

    case m: WindOffset =>
      actorForControls("HeadingIndicator") ! m
  }
}