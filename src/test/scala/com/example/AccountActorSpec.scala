package com.example

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import akka.persistence.Persistence
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.stream.ActorMaterializer
import akka.testkit.{ TestActors, TestKit, ImplicitSender }
import akka.util.Timeout
import com.example.AccountActor.ResetBalance
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.duration._
 
class AccountActorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("MySpec"))
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val askTimeout: Timeout = 3.seconds

  val store = system.actorOf(Props[SharedLeveldbStore], "store")
  SharedLeveldbJournal.setStore(store, system)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A AccountActor" must {
    val actor1 = system.actorOf(AccountActor.props("test1"))
    val actor2 = system.actorOf(AccountActor.props("test2"))

    "accept deposit" in {
      actor1 ! ResetBalance
      actor1 ! AccountActor.Deposit(100)
      actor1 ! AccountActor.GetBalance
      expectMsg(AccountActor.Balance(100))
    }

    "increase balance" in {
      actor1 ! AccountActor.Deposit(100)
      actor1 ! AccountActor.GetBalance
      expectMsg(AccountActor.Balance(200))
    }

    "decrease balance" in {
      actor1 ! AccountActor.WithDraw(50)
      actor1 ! AccountActor.GetBalance
      expectMsg(AccountActor.Balance(150))
    }

    "transfer deposit" in {
      actor2 ! ResetBalance
      actor1 ! AccountActor.Transfer(50, actor2)
      actor1 ! AccountActor.GetBalance
      expectMsg(AccountActor.Balance(100))
    }

    "actor 2 must have expected balance" in {
      actor2 ! AccountActor.GetBalance
      expectMsg(AccountActor.Balance(50))
    }
  }
}
