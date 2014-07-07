package com.tngtech.akka

import akka.actor.{ActorLogging, Props, Actor}
import akka.actor.Actor.Receive

import scala.concurrent.duration

object Service {
  case class Task(id: Int)
  case class Response(id: Int)
  case object Swap

  def props: Props = Props[Service]
  val name = "Service"
}

class Service extends Actor with ActorLogging{
  import Service._
  import duration._
  import context.dispatcher

  context.system.scheduler.schedule(2 seconds, 2 seconds, self, Swap)

  override def receive: Receive = {
    case Task(id) =>
      log.info( "Received task with id {}", id );
      sender ! Response(id)
    case Swap => context.become(unresponsive)
  }

  def unresponsive: Receive = {
    case Task(id) =>
      log.info( "SLOW: Received task with id {}", id );
      Thread.sleep(1000)
    case Swap => context.unbecome
  }
}
