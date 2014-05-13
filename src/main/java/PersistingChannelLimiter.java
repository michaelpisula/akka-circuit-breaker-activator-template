import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.persistence.*;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class PersistingChannelLimiter extends UntypedActor {

  private final ActorRef channel;
  private final ActorRef receiver;

  public static Props props() {
    return Props.create( PersistingChannelLimiter.class );
  }

  public PersistingChannelLimiter() {
    channel = getContext().actorOf( PersistentChannel.props( PersistentChannelSettings.create()
                                                                                      .withPendingConfirmationsMax( 2 )
                                                                                      .withPendingConfirmationsMin( 2 )
    ), "Channel" );
    receiver = getContext().actorOf( PersistentReceiver.props(), "PersistentReceiver" );
  }

  @Override
  public void onReceive( Object message ) throws Exception {
    if ( message instanceof Service.Task ) {
      Service.Task task = (Service.Task) message;
      channel.tell( Deliver.create( Persistent.create( task ), receiver.path() ), getSender() );
    }
  }

  public static class PersistentReceiver extends UntypedActor {

    public static Props props() {
      return Props.create( PersistentReceiver.class );
    }

    private final ActorRef service;

    public PersistentReceiver() {
      service = getContext().actorOf( Service.props(), "Service" );
    }

    @Override
    public void onReceive( Object message ) throws Exception {
      if ( message instanceof ConfirmablePersistent ) {
        getContext().actorOf( ServiceCaller.props( service ) ).tell( message, getSender() );
      }
    }
  }

  public static class ServiceCaller extends UntypedActor {

    public static Props props( ActorRef service ) {
      return Props.create( ServiceCaller.class, service );
    }

    private LoggingAdapter log = Logging.getLogger( getContext().system(), this );
    private final ActorRef service;
    private ConfirmablePersistent confirmablePersistent;
    private ActorRef originalSender;

    public ServiceCaller( ActorRef service ) {
      this.service = service;
      getContext().setReceiveTimeout( Duration.create( 1, TimeUnit.MINUTES ) );
    }

    @Override
    public void onReceive( Object message ) throws Exception {
      if ( message instanceof ConfirmablePersistent ) {
        originalSender = getSender();
        confirmablePersistent = (ConfirmablePersistent) message;
        Object payload = confirmablePersistent.payload();
        service.tell( payload, getSelf() );
      } else if ( message instanceof Service.Response ) {
        Service.Response response = (Service.Response) message;
        confirmablePersistent.confirm();
        originalSender.tell( response, getSelf() );
        getContext().stop( getSelf() );
      } else if ( message instanceof ReceiveTimeout ) {
        getContext().stop( getSelf() );
      }
    }
  }

}
