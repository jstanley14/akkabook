package zzz.akka.avionics

import akka.actor.SupervisorStrategy.{Stop, Resume, Escalate}
import akka.actor._
import akka.routing.BroadcastRouter


object PassengerSupervisor {
  case object GetPassengerBroadcaster
  case class PassengerBroadcaster(broadcaster: ActorRef)
  def apply(callButton: ActorRef) =
    new PassengerSupervisor(callButton) with PassengerProvider
}

class PassengerSupervisor(callButton: ActorRef) extends Actor {
  this: PassengerProvider =>
  import PassengerSupervisor._

  override val supervisorStrategy = OneForOneStrategy() {
    case _: ActorKilledException => Escalate
    case _: ActorInitializationException => Escalate
    case _ => Resume
  }

  case class GetChildren(forSomeone: ActorRef)
  case class Children(children: Iterable[ActorRef],
                      childrenFor: ActorRef)

  override def preStart(): Unit = {
    context.actorOf(Props(new Actor {
      val config = context.system.settings.config
      override val supervisorStrategy = OneForOneStrategy() {
        case _: ActorKilledException => Escalate
        case _: ActorInitializationException => Escalate
        case _ => Stop
      }

      override def preStart(): Unit = {
        import scala.collection.JavaConverters._
        import com.typesafe.config.ConfigList

        val passengers = config.getList("zzz.akka.avionics.passengers")
        passengers.asScala.foreach { nameWithSeat =>
          val id = nameWithSeat.asInstanceOf[ConfigList]
            .unwrapped().asScala.mkString("-")
            .replaceAllLiterally(" ", "_")
          context.actorOf(Props(newPassenger(callButton)), id)
        }
      }

      def receive = {
        case GetChildren(forSomeone: ActorRef) =>
          sender ! Children(context.children, forSomeone)
      }
    }), "PassengerSupervisor")
  }

  def noRouter: Receive = {
    case GetPassengerBroadcaster =>
      val passengers = context.actorFor("PassengerSupervisor")
      passengers ! GetChildren(sender)
    case Children(passengers, destinedFor) =>
      val router = context.actorOf(Props.empty.withRouter(
        BroadcastRouter(passengers.toVector)), "Passengers")
      destinedFor ! PassengerBroadcaster(router)
      context.become(withRouter(router))
  }

  def withRouter(router: ActorRef): Receive = {
    case GetPassengerBroadcaster =>
      sender ! PassengerBroadcaster(router)
    case Children(_, destinedFor) =>
      destinedFor ! PassengerBroadcaster(router)
  }

  def receive = noRouter
}