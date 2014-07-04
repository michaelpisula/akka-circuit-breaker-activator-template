package com.tngtech.akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.dispatch.Mapper;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Function;
import akka.japi.Procedure;
import akka.pattern.CircuitBreaker;
import akka.pattern.Patterns;
import akka.persistence.PersistenceFailure;
import akka.persistence.UntypedPersistentActorWithAtLeastOnceDelivery;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class PersistingCircuitBreaker extends UntypedPersistentActorWithAtLeastOnceDelivery {
  private LoggingAdapter log = Logging.getLogger( getContext().system(), this );
  private static final String name= "CircuitBreakerPersister";

  private final ActorRef circuitBreaker;

  public static Props props( Props serviceProps ) {
    return Props.create( PersistingCircuitBreaker.class, serviceProps );
  }

  public PersistingCircuitBreaker( Props serviceProps ) {
    circuitBreaker = getContext().actorOf( CircuitBreakerActor.props( serviceProps ), CircuitBreakerActor.name );
  }

  @Override
  public FiniteDuration redeliverInterval() {
    return Duration.create( 1, TimeUnit.SECONDS );
  }

  @Override
  public String persistenceId() {
    return name;
  }

  @Override
  public void onReceiveRecover( Object message ) throws Exception {
    updateState(message);
  }

  @Override
  public void onReceiveCommand( Object message ) throws Exception {
    if ( message instanceof Service.Task ) {
      Service.Task task = (Service.Task) message;
      persist( new TaskEnvelope( task, getSender()), new Procedure<TaskEnvelope>() {
        @Override
        public void apply( TaskEnvelope task ) throws Exception {
          updateState( task );
        }
      } );
    } else if (message instanceof DeliveryConfirmation) {
      DeliveryConfirmation confirmation = (DeliveryConfirmation) message;
      persist(confirmation, new Procedure<DeliveryConfirmation>() {
        @Override
        public void apply( DeliveryConfirmation conf ) throws Exception {
          conf.originalSender.tell( conf.response, getSelf() );
          updateState( conf );
        }
      });
    } else if (message instanceof PersistenceFailure) {
      PersistenceFailure failure = ((PersistenceFailure) message);
      log.error(failure.cause(), "Persisting failed for message {}", failure.payload());
    }
  }

  private void updateState( Object event ) {
    if ( event instanceof TaskEnvelope ) {
      final TaskEnvelope task = (TaskEnvelope) event;
      deliver( circuitBreaker.path(), new Function<Long, Object>() {
        @Override
        public Object apply( Long deliveryId ) throws Exception {
          return new DeliverTask( deliveryId, task );
        }
      } );
    } else if (event instanceof DeliveryConfirmation) {
      DeliveryConfirmation confirmation = (DeliveryConfirmation) event;
      confirmDelivery( confirmation.deliveryId );
    }
  }

  public static class CircuitBreakerActor extends UntypedActor {
    private static final String name= "CircuitBreaker";
    private LoggingAdapter log = Logging.getLogger( getContext().system(), this );

    public static final int MAX_FAILURES = 2;
    public static final int ASK_TIMEOUT = 100;
    public static final FiniteDuration CALL_TIMEOUT = Duration.create( 100, TimeUnit.MILLISECONDS );
    public static final FiniteDuration RESET_TIMEOUT = Duration.create( 2, TimeUnit.SECONDS );

    private final ActorRef service;
    private final CircuitBreaker circuitBreaker;

    public static Props props( Props serviceProps ) {
      return Props.create( CircuitBreakerActor.class, serviceProps );
    }

    public CircuitBreakerActor( Props serviceProps ) {
      circuitBreaker = new CircuitBreaker( getContext().dispatcher(),
                                           getContext().system().scheduler(),
                                           MAX_FAILURES,
                                           CALL_TIMEOUT,
                                           RESET_TIMEOUT )
          .onOpen( new Runnable() {
            public void run() {
              onOpen();
            }
          } )
          .onClose( new Runnable() {
            @Override
            public void run() {
              onClose();
            }
          } )
          .onHalfOpen( new Runnable() {
            @Override
            public void run() {
              onHalfOpen();
            }
          } );

      service = getContext().actorOf( serviceProps, "Service" );
    }

    @Override
    public void onReceive( Object message ) throws Exception {
      if ( message instanceof DeliverTask ) {
        final DeliverTask deliverTask = (DeliverTask) message;
        final Service.Task task = deliverTask.envelope.task;
        ActorRef sender = getSender();
        Future<Object> cbFuture = circuitBreaker.callWithCircuitBreaker( new Callable<Future<Object>>() {
          @Override
          public Future<Object> call() throws Exception {
            return Patterns.ask( service, task, ASK_TIMEOUT ).map( new Mapper<Object, Object>() {
              @Override
              public Object apply( Object response ) {
                  return new DeliveryConfirmation( deliverTask.id, deliverTask.envelope.sender, response );
              }
            }, getContext().dispatcher() );
          }
        } );
        Patterns.pipe( cbFuture, getContext().dispatcher() ).to( sender );
      }
    }

    public void onOpen() {
      log.info( "Circuit Breaker is open" );
    }

    public void onClose() {
      log.info( "Circuit Breaker is closed" );
    }

    public void onHalfOpen() {
      log.info( "Circuit Breaker is half open, next message will go through" );
    }

  }

  public static class TaskEnvelope implements Serializable{
    private Service.Task task;
    private ActorRef sender;

    public TaskEnvelope( Service.Task task, ActorRef sender ) {
      this.task = task;
      this.sender = sender;
    }
  }

  public static class DeliverTask  implements Serializable{
    private Long id;
    private TaskEnvelope envelope;

    public DeliverTask( Long id, TaskEnvelope envelope ) {
      this.id = id;
      this.envelope = envelope;
    }
  }

  public static class DeliveryConfirmation implements Serializable{
    private Long deliveryId;
    private ActorRef originalSender;
    private Object response;

    public DeliveryConfirmation( Long deliveryId, ActorRef originalSender, Object response ) {
      this.deliveryId = deliveryId;
      this.originalSender = originalSender;
      this.response = response;
    }
  }

}
