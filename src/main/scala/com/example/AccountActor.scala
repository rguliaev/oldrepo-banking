package com.example

import akka.actor.{ActorRef, Props, ActorLogging}
import akka.persistence.PersistentActor
import scala.concurrent.duration._

class AccountActor(userId: String) extends PersistentActor with ActorLogging {
  import AccountActor._
  context.setReceiveTimeout(3.minutes)

  private var balance: Balance = Balance(0)

  override def persistenceId: String = userId + "-" + self.path.name
  override def receiveRecover = {
    case RefreshBalance(amount,add) => if(add) deposit(amount) else withDraw(amount)
  }

  private def deposit(amount: BigDecimal) = {
    balance = balance.copy(amount = balance.amount + amount)
    log.info(s"Deposit $amount | Balance = s${balance.amount}")
  }

  private def withDraw(amount: BigDecimal) = {
    val check = balance.amount - amount >= 0
    if(check){
      balance = balance.copy(amount = balance.amount - amount)
      log.info(s"WithDraw $amount | Balance = s${balance.amount}")
    } else {
      log.info(s"Account overdrawn")
    }
  }

  override def receiveCommand = {
    case GetBalance => sender ! balance
    case Deposit(amount) =>
      persist(RefreshBalance(amount,true)){ p =>
        deposit(amount)
      }
    case WithDraw(amount) =>
      persist(RefreshBalance(amount,false)){ p =>
        withDraw(amount)
      }
    case Transfer(amount,to) =>
      persist(RefreshBalance(amount,false)) { p =>
        to ! Deposit(amount)
        withDraw(amount)
      }
    case ResetBalance =>
      persist(RefreshBalance(-balance.amount,true)){ p =>
        deposit(p.amount)
      }
  }
}

object AccountActor {
  def props(userId: String): Props = Props(new AccountActor(userId))

  sealed trait AccountEvents
  case object GetBalance extends AccountEvents
  case class WithDraw(amount: BigDecimal) extends AccountEvents
  case class Deposit(amount: BigDecimal) extends AccountEvents
  case class Balance(amount: BigDecimal) extends AccountEvents
  case class Transfer(amount: BigDecimal, to: ActorRef) extends AccountEvents
  case class RefreshBalance(amount: BigDecimal, add: Boolean) extends AccountEvents
  case object ResetBalance extends AccountEvents
}
