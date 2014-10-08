package zzz.akka.avionics

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpecLike}

object TestFlightAttendant {
  def apply() = new FlightAttendant with AttendantResponsiveness {
    val maxResponseTimeMS = 1
  }
}

class FlightAttendantSpec
  extends TestKit(ActorSystem("FlightAttendantSpec",
                              ConfigFactory.parseString(
                                "akka.scheduler.tick-duration = 10ms")))
  with ImplicitSender
  with WordSpecLike
  with Matchers {

  import FlightAttendant._

  "FlightAttendant" should {
    "get a drink when asked" in {
      val a = TestActorRef(Props(TestFlightAttendant()))
      a ! GetDrink("Gin")
      expectMsg(Drink("Gin"))
    }
  }
}