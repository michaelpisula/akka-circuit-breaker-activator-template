package com.tngtech.akka

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.{CircuitBreaker, ask, pipe}
import akka.persistence.{AtLeastOnceDelivery, PersistenceFailure, PersistentActor}
import akka.util.Timeout
import com.tngtech.akka.PersistentCircuitBreaker.{DeliverTask, DeliveryConfirmation}

object PersistentCircuitBreaker {
  def props(serviceProps: Props) = Props(classOf[PersistentCircuitBreaker], serviceProps)

  sealed trait Evt

  case class TaskEnvelope(task: Service.Task, sender: ActorRef) extends Evt

  case class DeliverTask(deliveryId: Long, taskEnvelope: TaskEnvelope)

  case class DeliveryConfirmation(deliveryId: Long, originalSender: ActorRef, response: Any) extends Evt

}

class PersistentCircuitBreaker(serviceProps: Props) extends PersistentActor with AtLeastOnceDelivery with ActorLogging {
  import scala.concurrent.duration._
  import com.tngtech.akka.PersistentCircuitBreaker._

  val circuitBreaker = context.actorOf(Props(classOf[CircuitBreakerActor], serviceProps), "CircuitBreaker")


  override def receiveRecover: Receive = {
    case evt: Evt => updateState(evt)
  }

  override def receiveCommand: Receive = {
    case task: Service.Task =>
      persist(TaskEnvelope(task, sender()))(updateState)
    case confirmation: DeliveryConfirmation =>
      persist(confirmation) { confirmation =>
        confirmation.originalSender ! confirmation.response
        updateState(confirmation)
      }
    case failure: PersistenceFailure =>
      log.error(failure.cause, "Persisting failed for message {}", failure.payload)
  }

  def updateState(event: Evt): Unit = event match {
    case envelope: TaskEnvelope =>
      deliver(circuitBreaker.path, deliveryId => DeliverTask(deliveryId, envelope))
    case confirmation: DeliveryConfirmation =>
      confirmDelivery(confirmation.deliveryId)
  }

  override def persistenceId: String = "PersistentCircuitBreaker"
  override def redeliverInterval = 1 second
}

class CircuitBreakerActor(serviceProps: Props) extends Actor with ActorLogging {

  import context.dispatcher
  import scala.concurrent.duration._
  implicit val timeout = Timeout(100 milliseconds)

  val service = context.actorOf(serviceProps, Service.name)
  val circuitBreaker = new CircuitBreaker(context.system.scheduler,
    maxFailures = 2,
    callTimeout = 100 milliseconds,
    resetTimeout = 2 seconds
  ).onOpen(open).onClose(close).onHalfOpen(halfopen)

  override def receive: Receive = {
    case deliverTask: DeliverTask =>
      circuitBreaker
        .withCircuitBreaker(service ? deliverTask.taskEnvelope.task)
        .map(response => DeliveryConfirmation(deliverTask.deliveryId, deliverTask.taskEnvelope.sender, response))
        .pipeTo(sender())
  }

  def open: Unit = log.info("Circuit Breaker is open")

  def close: Unit = log.info("Circuit Breaker is closed")

  def halfopen: Unit = log.info("Circuit Breaker is half-open, next message goes through")


}
