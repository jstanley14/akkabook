package zzz.akka.avionics

import akka.actor._
import akka.actor.SupervisorStrategy.{Escalate, Resume, Stop, Decider}

import scala.concurrent.duration.Duration

trait SupervisionStrategyFactory {
  def makeStrategy(maxNrRetries: Int, withinTimeRange: Duration)
                  (decider: Decider): SupervisorStrategy
}

trait OneForOneStrategyFactory extends SupervisionStrategyFactory {
  def makeStrategy(maxNrRetries: Int, withinTimeRange: Duration)
                  (decider: Decider): SupervisorStrategy = {
    OneForOneStrategy(maxNrRetries, withinTimeRange)(decider)
  }
}

trait AllForOneStrategyFactory extends SupervisionStrategyFactory {
  def makeStrategy(maxNrRetries: Int, withinTimeRange: Duration)
                  (decider: Decider): SupervisorStrategy = {
    AllForOneStrategy(maxNrRetries, withinTimeRange)(decider)
  }
}

object IsolatedLifeCycleSupervisor {
  case object WaitForStart
  case object Started
}

trait IsolatedLifeCycleSupervisor extends Actor {
  import IsolatedLifeCycleSupervisor._

  def receive = {
    case WaitForStart => sender ! Started
    case m => throw new Exception(s"Don't call ${self.path.name} directly ($m)")
  }

  def childStarter(): Unit

  final override def preStart() { childStarter() }

  final override def postRestart(reason: Throwable) { }

  final override def preRestart(reason: Throwable, message: Option[Any]) { }
}

abstract class IsolatedResumeSupervisor(maxNrRetries: Int = -1,
                                        withinTimeRange: Duration = Duration.Inf)
  extends IsolatedLifeCycleSupervisor { this: SupervisionStrategyFactory =>

  override val supervisorStrategy = makeStrategy(maxNrRetries, withinTimeRange) {
    case _: ActorInitializationException => Stop
    case _: ActorKilledException => Stop
    case _: Exception => Resume
    case _ => Escalate
  }
}

abstract class IsolatedStopSupervisor(maxNrRetries: Int = -1,
                                      withinTimeRange: Duration = Duration.Inf)
  extends IsolatedLifeCycleSupervisor { this: SupervisionStrategyFactory =>

  override val supervisorStrategy = makeStrategy(maxNrRetries, withinTimeRange) {
    case _: ActorInitializationException => Stop
    case _: ActorKilledException => Stop
    case _: Exception => Stop
    case _ => Escalate
  }
}