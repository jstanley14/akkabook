package zzz.akka.avionics

import akka.actor.{Props, Actor, ActorSystem, ActorLogging}
import zzz.akka.avionics
import scala.concurrent.duration._

trait AltimeterProvider {
  def newAltimeter: Actor = Altimeter()
}

object Altimeter {
  case class RateChange(amount: Float)
  case class AltitudeUpdate(altitude: Double)
  def apply() = new Altimeter with ProductionEventSource
}

class Altimeter extends Actor with ActorLogging { this: EventSource =>
  import Altimeter._
  implicit val ec = context.dispatcher
  val ceiling = 43000
  val maxRateOfClimb = 5000
  var rateOfClimb = 0f
  var altitude = 0d
  var lastTick = System.currentTimeMillis
  val ticker = context.system.scheduler.schedule(100.millis,
                                                 100.millis,
                                                 self,
                                                 Tick)
  case object Tick

  def altimeterReceive: Receive = {
    case RateChange(amount) =>
      rateOfClimb = amount.min(1.0f).max(-1.0f) * maxRateOfClimb
      log info s"Altimeter changed rate of climb to $rateOfClimb"

    case Tick =>
      val tick = System.currentTimeMillis
      altitudeCalculator ! CalculateAltitude(lastTick, tick, rateOfClimb)
      lastTick = tick

    case AltitiudeCalculated(tick, altCng) =>
      altitude += altCng
      sendEvent(AltitudeUpdate(altitude))


  }

  def receive = eventSourceReceive orElse altimeterReceive

  override def postStop(): Unit = ticker.cancel()

  case class CalculateAltitude(lastTick: Long, tick: Long, rateOfClimb: Double)
  case class AltitiudeCalculated(newTick: Long, altitude: Double)
  val altitudeCalculator = context.actorOf(Props(new Actor {
    def receive = {
      case CalculateAltitude(lastTick, tick, rateOfClimb) =>
        val altCng = ((tick - lastTick) / 60000.0) * rateOfClimb
        sender ! AltitiudeCalculated(tick, altCng)
    }
  }), "AltitudeCalculator")
}