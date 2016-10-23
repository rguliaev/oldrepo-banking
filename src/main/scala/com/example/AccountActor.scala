package com.example

import akka.actor.{ActorRef, Props, ActorLogging}
import akka.persistence.PersistentActor
import scala.concurrent.duration._

class AccountActor(userId: String) extends PersistentActor with ActorLogging {
  import AccountActor._
  context.setReceiveTimeout(2.minutes)

  private var balance: Balance = Balance(0)

  override def persistenceId: String = userId + "-" + self.path.name
  override def receiveRecover = {
    case RefreshBalance(amount,add) => if(add) deposit(amount) else withDraw(amount)
    case Transfer(amount,to) =>
      withDraw(amount)
      to ! Deposit(amount)
  }

  private def deposit(amount: BigDecimal) = {
    balance = balance.copy(amount = balance.amount + amount)
    log.info(s"Deposit $amount | Balance = s${balance.amount}")
  }

  private def withDraw(amount: BigDecimal): Boolean = {
    val check = balance.amount - amount >= 0
    if(check){
      balance = balance.copy(amount = balance.amount - amount)
      log.info(s"WithDraw $amount | Balance = s${balance.amount}")
    } else {
      log.info(s"Account overdrawn")
    }
    check
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
    case msg @ Transfer(amount,to) =>
      persist(msg) { p =>
        withDraw(amount)
        to ! Deposit(amount)
      }
    case ResetBalance =>
      persist(RefreshBalance(-balance.amount,true)){ p =>
        deposit(p.amount)
      }
  }
}

object AccountActor {
  def props(userId: String): Props = Props(new AccountActor(userId))

  sealed trait AccountActions
  case object GetBalance extends AccountActions
  case class WithDraw(amount: BigDecimal) extends AccountActions
  case class Deposit(amount: BigDecimal) extends AccountActions
  case class Balance(amount: BigDecimal) extends AccountActions
  case class Transfer(amount: BigDecimal, to: ActorRef) extends AccountActions
  case class RefreshBalance(amount: BigDecimal, add: Boolean) extends AccountActions
  case object ResetBalance extends AccountActions
}
