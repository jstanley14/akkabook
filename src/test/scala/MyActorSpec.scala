//import akka.actor.{ActorSystem, ActorRef, Props}
//import akka.testkit.TestKit
//import org.scalatest._
//
//class MyActorSpec extends fixture.WordSpec
//                  with Matchers
//                  with fixture.UnitFixture
//                  with ParallelTestExecution {
//  def makeActor(): ActorRef = system.actorOf(Props[MyActor], "MyActor")
//
//  "My Actor" should {
//
//
//    "throw when made with the wrong name" in new ActorSys {
//      an [Exception] should be thrownBy {
//        val a = system.actorOf(Props[MyActor]) }
//    }
//  }
//}
