package zzz.akka.avionics

import akka.actor._
import scala.concurrent.duration._

object Passenger {
  case object FastenSeatbelts
  case object UnfastenSeatbelts

  val SeatAssignment = """([\w\s_]+)-(\d+)-([A-Z])""".r
}

trait DrinkRequestProbability {
  val askThreshold = 0.9f
  val requestMin = 20.minutes
  val requestUpper = 30.minutes

  def randomishTime(): FiniteDuration =
    requestMin + scala.util.Random.nextInt(requestUpper.toMillis.toInt).millis
}

trait PassengerProvider {
  def newPassenger(callButton: ActorRef): Actor =
    new Passenger(callButton) with DrinkRequestProbability
}

class Passenger(callButton: ActorRef) extends Actor with ActorLogging {
  this: DrinkRequestProbability =>
  import Passenger._
  import FlightAttendant.{GetDrink, Drink}
  import scala.collection.JavaConverters._

  var r = scala.util.Random
  implicit val ec = context.dispatcher

  case object CallForDrink

  val SeatAssignment(myname, _, _) =
    self.path.name.replaceAllLiterally("_", " ")

  val drinks = context.system.settings.config.getStringList(
    "zzz.akka.avionics.drinks").asScala.toIndexedSeq

  val scheduler = context.system.scheduler

  override def preStart(): Unit = {
    self ! CallForDrink
  }

  def maybeSendDrinkRequest(): Unit = {
    if (r.nextFloat() > askThreshold) {
      val drinkName = drinks(r.nextInt(drinks.length))
      callButton ! GetDrink(drinkName)
    }
    scheduler.scheduleOnce(randomishTime(), self, CallForDrink)
  }

  def receive = {
    case CallForDrink =>
      maybeSendDrinkRequest()
    case Drink(drinkName) =>
      log.info(s"$myname received a $drinkName - Tastes great")
    case FastenSeatbelts =>
      log.info(s"$myname fastening seatbelt.")
    case UnfastenSeatbelts =>
      log.info(s"$myname unfastening seatbelt.")
  }
}
