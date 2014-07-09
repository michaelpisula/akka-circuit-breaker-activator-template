package com.tngtech.akka

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.{CircuitBreaker, ask, pipe}
import akka.util.Timeout

object SimpleCircuitBreaker {
  def props(serviceProps: Props) = Props(classOf[SimpleCircuitBreaker], serviceProps)
  val name = "SimpleCircuitBreaker"
}

class SimpleCircuitBreaker(serviceProps: Props) extends Actor with ActorLogging {

  import context.dispatcher
  import scala.concurrent.duration._

  val service = context actorOf(serviceProps, Service.name)
  val circuitBreaker = new CircuitBreaker(context.system.scheduler,
    maxFailures = 2,
    callTimeout = 100 milliseconds,
    resetTimeout = 2 seconds
  ).onOpen(open).onClose(close).onHalfOpen(halfopen)

  implicit val timeout = Timeout(100 milliseconds)

  override def receive: Receive = {
    case msg: Service.Task =>
      circuitBreaker.withCircuitBreaker(service ? msg).pipeTo(sender())
  }

  def open: Unit = log.info("Circuit Breaker is open")

  def close: Unit = log.info("Circuit Breaker is closed")

  def halfopen: Unit = log.info("Circuit Breaker is half-open, next message goes through")
}
