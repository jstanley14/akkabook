package zzz.akka.avionics

import akka.actor.{ActorRef, Actor}
import scala.concurrent.duration._
import scala.util.Random

object WeatherBehavior {
  case class WindOffset(force: Float)
  case object BlowAgain

  def apply(plane: ActorRef): WeatherBehavior =
    new WeatherBehavior(plane)
}

class WeatherBehavior(plane: ActorRef) extends Actor {
  import WeatherBehavior._
  implicit val ec = context.dispatcher

  def windyToday(): Unit = {
    val windStrength = Random.nextFloat() * 20 - 10
    val nextBlust = Random.nextInt(3).seconds
    plane ! WindOffset(windStrength)
    context.system.scheduler.scheduleOnce(nextBlust, self, BlowAgain)
  }

  override def preStart() = {
    windyToday()
  }

  def receive = {
    case BlowAgain => windyToday()
  }
}