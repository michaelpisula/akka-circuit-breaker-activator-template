package com.tngtech.akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.persistence.*;
import scala.Option;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class PersistingChannelLimiter extends UntypedActor {

  private final ActorRef channel;
  private final ActorRef receiver;

  public static Props props(Props serviceProps) {
    return Props.create( PersistingChannelLimiter.class, serviceProps );
  }

  public PersistingChannelLimiter(Props serviceProps) {
    PersistentChannelSettings channelSettings =
        new PersistentChannelSettings( 5, Duration.create( 2, TimeUnit.SECONDS ),
                                       Option.<ActorRef>empty(),
                                       false,
                                       2,
                                       2,
                                       Duration.create( 1, TimeUnit.SECONDS )
        );
    channel = getContext().actorOf( PersistentChannel.props( channelSettings ), "Channel" );
    receiver = getContext().actorOf( PersistentReceiver.props(serviceProps), "PersistentReceiver" );
  }

  @Override
  public void onReceive( Object message ) throws Exception {
    if ( message instanceof Service.Task ) {
      Service.Task task = (Service.Task) message;
      channel.tell( Deliver.create( Persistent.create( task ), receiver.path() ), getSender() );
    }
  }

  public static class PersistentReceiver extends UntypedActor {

    public static Props props(Props serviceProps) {
      return Props.create( PersistentReceiver.class, serviceProps );
    }

    private final ActorRef service;

    public PersistentReceiver(Props serviceProps) {
      service = getContext().actorOf( serviceProps, "Service" );
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
