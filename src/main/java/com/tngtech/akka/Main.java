package com.tngtech.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class Main {

  public static void main( String[] args ) throws Exception {
    ActorSystem system = ActorSystem.create();

    Props serviceProps = Service.props();
    //final ActorRef service = system.actorOf( serviceProps, "Service" );
    //final ActorRef service = system.actorOf( SimpleCircuitBreaker.props(serviceProps), "SimpleCircuitBreaker" );
    final ActorRef service = system.actorOf( PersistingCircuitBreaker.props(serviceProps), "PersistingCircuitBreaker" );
    //    final ActorRef service = system.actorOf( PersistingChannelLimiter.props(serviceProps), "PersistingChannelLimiter" );

    ActorRef taskCreator = system.actorOf( TaskCreator.props( service ) );

    system.scheduler().schedule( Duration.create( 0, TimeUnit.SECONDS ),
                                 Duration.create( 200, TimeUnit.MILLISECONDS ), taskCreator, new Tick(),
                                 system.dispatcher(), ActorRef.noSender()
    );

    Thread.sleep( 10000 );

    system.shutdown();
    system.awaitTermination();

  }

  public static class TaskCreator extends UntypedActor {

    private LoggingAdapter log = Logging.getLogger( getContext().system(), this );

    public static Props props( ActorRef service ) {
      return Props.create( TaskCreator.class, service );
    }

    private final ActorRef service;
    private int currentId = 0;

    public TaskCreator( ActorRef service ) {
      this.service = service;
    }

    @Override
    public void onReceive( Object message ) throws Exception {
      if ( message instanceof Tick ) {
        service.tell( getMessage(), getSelf() );
      } else if ( message instanceof Service.Response ) {
        log.info( "Received response" );
      }
    }

    private Service.Task getMessage() {
      return new Service.Task( currentId++ );
    }
  }

  public static class Tick {

  }

}
