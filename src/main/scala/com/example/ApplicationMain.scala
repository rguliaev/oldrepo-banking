package com.example

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{Route, RouteResult, Directive0, StandardRoute}
import akka.http.scaladsl.server.Directives._
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json.DefaultJsonProtocol
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import akka.pattern.ask

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val putFormat = jsonFormat2(Put)
  implicit val transferFormat = jsonFormat3(Transfer)

  case class Put(userId: String, amount: BigDecimal)
  case class Transfer(senderId: String, recipientId: String, amount: BigDecimal)
}

object ApplicationMain extends JsonSupport {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    implicit val askTimeout: Timeout = 3.seconds

    val store = system.actorOf(Props[SharedLeveldbStore], "store")
    SharedLeveldbJournal.setStore(store, system)

    val users = List("userId-1","userId-2","userId-3")

    val actors = users.map { user =>
      val actor = system.actorOf(AccountActor.props(user))
      (user,actor)
    }.toMap

    def accountCheck(userId: String)(f: ActorRef => Route): Route = {
      actors.get(userId) match {
        case Some(actor) => f(actor)
        case None => complete(s"Unknown userId, please use userId-1 or userId-2 or userId-3")
      }
    }

    def accountsCheck(senderId: String, recipentId: String)(f: (ActorRef,ActorRef) => Route): Route = {
      (actors.get(senderId), actors.get(recipentId)) match {
        case (Some(sender),Some(recipient)) if(sender != recipient) => f(sender,recipient)
        case _ => complete(s"Unknown one of userIds or impossible transfer")
      }
    }

    val route: Route =
      get {
        path("balance" / Segment) { maybeUserId =>
          accountCheck(maybeUserId) { actor =>
            onSuccess(
              (actor ? AccountActor.GetBalance).mapTo[AccountActor.Balance].recover {
                case _ => AccountActor.Balance(0)
              }
            ){ balance => complete(balance.amount.toString)}
          }
        }
      } ~
      post {
        path("deposit") {
          entity(as[Put]){ deposit => accountCheck(deposit.userId) { actor =>
            actor ! AccountActor.Deposit(deposit.amount)
            complete("Deposit has been sent")
          }}
        } ~
        path("withdraw") {
          entity(as[Put]){ withdraw => accountCheck(withdraw.userId) { actor =>
            actor ! AccountActor.WithDraw(withdraw.amount)
            complete("WithDraw has been sent")
          }}
        } ~
        path("transfer") {
          entity(as[Transfer]){ transfer => accountsCheck(transfer.senderId,transfer.recipientId) {(sender,recipient) =>
            sender ! AccountActor.Transfer(transfer.amount,recipient)
            complete("Transfer has been sent")
          }}
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println("Push Enter to terminate")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}