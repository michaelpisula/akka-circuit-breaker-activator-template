import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.dispatch.Mapper;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.CircuitBreaker;
import akka.pattern.Patterns;
import akka.persistence.*;
import scala.Option;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class PersistingCircuitBreaker extends UntypedActor {

  private final ActorRef channel;
  private final ActorRef circuitBreaker;

  public static Props props() {
    return Props.create( PersistingCircuitBreaker.class );
  }

  public PersistingCircuitBreaker() {
    channel = getContext().actorOf( Channel.props( ChannelSettings
                                                       .create()
                                                       .withRedeliverInterval( Duration.create( 1, TimeUnit.SECONDS )
                                                      ) ), "Channel" );
    /*
    PersistentChannelSettings channelSettings =
        new PersistentChannelSettings( 5, Duration.create( 1, TimeUnit.SECONDS ),
                                       Option.<ActorRef>empty(),
                                       false,
                                       50,
                                       50,
                                       Duration.create( 4, TimeUnit.SECONDS )
        );
    channel = getContext().actorOf( PersistentChannel.props( channelSettings ) );
    */
    circuitBreaker = getContext().actorOf( CircuitBreakerPersistentActor.props(), "CircuitBreaker" );
  }

  @Override
  public void onReceive( Object message ) throws Exception {
    if ( message instanceof Service.Task ) {
      Service.Task task = (Service.Task) message;
      channel.tell( Deliver.create( Persistent.create( task ), circuitBreaker.path() ), getSender() );
    }
  }

  public static class CircuitBreakerPersistentActor extends UntypedActor {

    private LoggingAdapter log = Logging.getLogger( getContext().system(), this );

    public static final int MAX_FAILURES = 2;
    public static final int ASK_TIMEOUT = 100;
    public static final FiniteDuration CALL_TIMEOUT = Duration.create( 100, TimeUnit.MILLISECONDS );
    public static final FiniteDuration RESET_TIMEOUT = Duration.create( 5, TimeUnit.SECONDS );

    private final ActorRef service;
    private final CircuitBreaker circuitBreaker;

    public static Props props() {
      return Props.create( CircuitBreakerPersistentActor.class );
    }

    public CircuitBreakerPersistentActor() {
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

      service = getContext().actorOf( Service.props(), "Service" );
    }

    @Override
    public void onReceive( Object message ) throws Exception {
      if ( message instanceof ConfirmablePersistent ) {
        final ConfirmablePersistent confirmablePersistent = (ConfirmablePersistent) message;
        Object payload = confirmablePersistent.payload();
        if ( payload instanceof Service.Task ) {
          final Service.Task task = (Service.Task) payload;
          ActorRef sender = getSender();
          Future<Object> cbFuture = circuitBreaker.callWithCircuitBreaker( new Callable<Future<Object>>() {
            @Override
            public Future<Object> call() throws Exception {
              return Patterns.ask( service, task, ASK_TIMEOUT ).map( new Mapper<Object, Object>() {
                @Override
                public Object apply( Object response ) {
                  confirmablePersistent.confirm();
                  return response;
                }
              }, getContext().system().dispatcher() );
            }
          } );
          Patterns.pipe( cbFuture, getContext().system().dispatcher() ).to( sender );
        }
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

}
