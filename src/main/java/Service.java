import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public class Service extends UntypedActor {
  private LoggingAdapter log = Logging.getLogger( getContext().system(), this );

  public static Props props() {
    return Props.create( Service.class );
  }

  public Service() {
    FiniteDuration duration = Duration.create( 5, TimeUnit.SECONDS );
    getContext().system()
                .scheduler()
                .schedule( duration,
                           duration,
                           getSelf(),
                           new Swap(),
                           getContext().dispatcher(), getSelf() );
  }

  Procedure<Object> slow = new Procedure<Object>() {
    @Override
    public void apply( Object message ) throws Exception {
      log.info( "SLOW: Received request of type {}", message );
      if ( message instanceof Task ) {
        Thread.sleep( 1000 );
      } else if ( message instanceof Swap ) {
        getContext().unbecome();
      }
    }
  };

  @Override
  public void onReceive( Object message ) throws Exception {
    log.info( "Received request of type {}", message );
    if ( message instanceof Task ) {
      sendResponse();
    } else if ( message instanceof Swap ) {
      getContext().become( slow );
    }
  }

  private void sendResponse() {
    log.info( "Creating response" );
    getSender().tell( new Response(), getSelf() );
  }

  public static class Task implements Serializable {
    private final int id;

    public Task( int id ) {
      this.id = id;
    }

    @Override
    public String toString() {
      return "Task{" +
             "id=" + id +
             '}';
    }
  }

  public static class Response implements Serializable {
  }

  public static class Swap {
  }

  public static void main( String[] args ) {

  }
}
