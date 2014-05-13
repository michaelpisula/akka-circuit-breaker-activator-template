import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class Main {

  public static void main( String[] args ) throws Exception {
    ActorSystem system = ActorSystem.create();

    final ActorRef service = system.actorOf( Service.props(), "Service" );
//    final ActorRef service = system.actorOf( SimpleCircuitBreaker.props(), "SimpleCircuitBreaker" );
//    final ActorRef service = system.actorOf( PersistingCircuitBreaker.props(), "PersistingCircuitBreaker" );
//    final ActorRef service = system.actorOf( PersistingChannelLimiter.props(), "PersistingChannelLimiter" );

    final Inbox inbox = Inbox.create( system );

    system.scheduler().schedule( Duration.create( 0, TimeUnit.SECONDS ),
                                 Duration.create( 100, TimeUnit.MILLISECONDS ),
                                 new Runnable() {
                                   @Override
                                   public void run() {
                                     service.tell( getMessage(), inbox.getRef() );
                                   }
                                 }, system.dispatcher()
    );

    Thread.sleep( 30000 );

    system.shutdown();
    system.awaitTermination();

  }

  private static int currentId = 0;

  private static Service.Task getMessage() {
    return new Service.Task( currentId++ );
  }

}
