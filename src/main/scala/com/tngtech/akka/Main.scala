package com.tngtech.akka

import akka.actor._
import com.tngtech.akka.TaskCreator.Tick

import scala.concurrent.duration

object Main {

  def main(args: Array[String]) {
    val system = ActorSystem("CircuitBreaker")

    val serviceProps = Service.props

    val service = system.actorOf(serviceProps, Service.name)
    //val service = system.actorOf(SimpleCircuitBreaker.props(serviceProps), SimpleCircuitBreaker.name)
    //val service = system.actorOf(PersistentCircuitBreaker.props(serviceProps), PersistentCircuitBreaker.name)

    val taskCreator = system.actorOf(TaskCreator.props(service), TaskCreator.name)

    import system.dispatcher

import scala.concurrent.duration._

    system.scheduler.schedule(0 seconds, 200 milliseconds, taskCreator, Tick)

    Thread.sleep(10000)

    system.shutdown()
    system.awaitTermination()

  }


}

object TaskCreator {
  val name = "TaskCreator"

  case object Tick

  def props(service: ActorRef) = Props(classOf[TaskCreator], service)
}

class TaskCreator(service: ActorRef) extends Actor with ActorLogging {

  import TaskCreator._

  override def receive: Receive = {
    case Tick =>
      service ! createTask
    case Service.Response(id) =>
      log.info("Received response with id {}", id)
  }

  var currentId = 0

  def createTask = {
    currentId += 1
    Service.Task(currentId)
  }
}