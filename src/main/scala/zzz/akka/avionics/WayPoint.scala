package zzz.akka.avionics

import akka.actor.Actor
import zzz.akka.avionics.FlyingBehavior.{NewWaypoint, CourseTarget}
import scala.concurrent.duration._


object WayPoint {
  def apply() = new WayPoint with ProductionEventSource
}

class WayPoint extends Actor {
  this: EventSource =>
  import WayPoint._

  import scala.collection.JavaConverters._

  case object Tick

  implicit val ec = context.dispatcher
  val config = context.system.settings.config
  val waypointStrings = config.getStringList("zzz.akka.avionics.waypoints").asScala
  val waypointBuf = waypointStrings map { (s: String) =>
    s split " " match {
      case Array(alt, head, ms) =>
        CourseTarget(alt.toDouble, head.toFloat, ms.toLong)
    }
  }
  var waypoints = waypointBuf.toList

  val ticker = context.system.scheduler.schedule(5.seconds, 5.seconds,
                                                 self, Tick)

  def wayPointsReceive: Receive = {
    case Tick =>
      waypoints match {
        case t :: wps => {
          waypoints = wps
          sendEvent(NewWaypoint(t))
        }
        case _ =>
          sendEvent(NewWaypoint(CourseTarget(0,0,10000)))
      }
  }

  def receive = eventSourceReceive orElse wayPointsReceive
}
