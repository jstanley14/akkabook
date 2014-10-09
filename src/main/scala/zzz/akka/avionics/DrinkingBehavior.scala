package zzz.akka.avionics

import akka.actor.{Props, Actor, ActorRef}

import scala.concurrent.duration._

trait DrinkingProvider {
  def newDrinkingBehavior(drinker: ActorRef): Props =
    Props(DrinkingBehavior(drinker))
}

object DrinkingBehavior {
  case class LevelChanged(level: Float)

  case object FeelingSober
  case object FeelingTipsy
  case object FeelingLikeZaphod

  def apply(drinker: ActorRef) =
    new DrinkingBehavior(drinker) with DrinkingResolution
}

trait DrinkingResolution {
  import scala.util.Random
  def initialSobering: FiniteDuration = 1.second
  def soberingInterval: FiniteDuration = 1.second
  def drinkInterval(): FiniteDuration = Random.nextInt(300).seconds
}

class DrinkingBehavior(drinker: ActorRef) extends Actor {
  this: DrinkingResolution =>
  import DrinkingBehavior._

  implicit val ec = context.dispatcher
  var currentLevel = 0f
  val scheduler = context.system.scheduler
  val sobering = scheduler.schedule(initialSobering, soberingInterval,
                                    self, LevelChanged(-0.0001f))
  override def preStart(): Unit = {
    drink()
  }

  override def postStop(): Unit = sobering.cancel()

  def drink() = scheduler.scheduleOnce(drinkInterval(), self,
                                       LevelChanged(0.005f))

  def receive = {
    case LevelChanged(amount) =>
      currentLevel = (currentLevel + amount).max(0f)
      drinker ! (if (currentLevel <= 0.01) {
        drink()
        FeelingSober
      } else if (currentLevel <= 0.03) {
        drink()
        FeelingTipsy
      }
        else FeelingLikeZaphod)
  }
}