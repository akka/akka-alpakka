/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.mqtt.streaming
package impl

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.annotation.InternalApi
import akka.util.Timeout

import scala.annotation.tailrec
import scala.util.{Failure, Success}

/*
 * A client connector is a Finite State Machine that manages MQTT client
 * session state. A client connects to a server, subscribes/unsubscribes
 * from topics to receive publications on and publishes to its own topics.
 */
@InternalApi private[streaming] object ClientConnector {

  type ConnectData = Option[_]

  /*
   * Construct with the starting state
   */
  def apply(packetIdAllocator: ActorRef[PacketIdAllocator.Request], settings: MqttSessionSettings): Behavior[Event] =
    disconnected(Uninitialized(packetIdAllocator, settings))

  // Our FSM data, FSM events and commands emitted by the FSM

  sealed abstract class Data(val packetIdAllocator: ActorRef[PacketIdAllocator.Request],
                             val settings: MqttSessionSettings)
  final case class Uninitialized(override val packetIdAllocator: ActorRef[PacketIdAllocator.Request],
                                 override val settings: MqttSessionSettings)
      extends Data(packetIdAllocator, settings)
  final case class ConnectReceived(connect: Connect,
                                   connectData: ConnectData,
                                   override val packetIdAllocator: ActorRef[PacketIdAllocator.Request],
                                   override val settings: MqttSessionSettings)
      extends Data(packetIdAllocator, settings)
  final case class ConnAckReceived(connect: Connect,
                                   connAck: ConnAck,
                                   override val packetIdAllocator: ActorRef[PacketIdAllocator.Request],
                                   override val settings: MqttSessionSettings)
      extends Data(packetIdAllocator, settings)

  sealed abstract class Event
  final case class ConnectReceivedLocally(connect: Connect,
                                          connectData: ConnectData,
                                          remote: ActorRef[ForwardConnect.type])
      extends Event
  final case class ConnAckReceivedFromRemote(connAck: ConnAck, local: ActorRef[ForwardConnAck]) extends Event
  case object ReceiveConnAckTimeout extends Event
  case object ConnectionLost extends Event
  final case class DisconnectReceivedLocally(remote: ActorRef[ForwardDisconnect.type]) extends Event
  final case class SubscribeReceivedLocally(subscribe: Subscribe,
                                            subscribeData: Subscriber.SubscribeData,
                                            remote: ActorRef[Subscriber.ForwardSubscribe])
      extends Event
  final case class SubAckReceivedFromRemote(subAck: SubAck, local: ActorRef[Subscriber.ForwardSubAck]) extends Event
  final case class PublishReceivedFromRemote(publish: Publish, local: ActorRef[Subscriber.ForwardPublish.type])
      extends Event
  final case class PubAckReceivedLocally(pubAck: PubAck, remote: ActorRef[Subscriber.ForwardPubAck.type]) extends Event
  final case class PubRecReceivedLocally(pubRec: PubRec, remote: ActorRef[Subscriber.ForwardPubRec.type]) extends Event
  final case class PubRelReceivedFromRemote(pubRel: PubRel, local: ActorRef[Subscriber.ForwardPubRel.type])
      extends Event
  final case class PubCompReceivedLocally(pubComp: PubComp, remote: ActorRef[Subscriber.ForwardPubComp.type])
      extends Event
  final case class PublishReceivedLocally(publish: Publish,
                                          publishData: Producer.PublishData,
                                          local: ActorRef[Producer.ForwardPublish])
      extends Event
  final case class PubAckReceivedFromRemote(pubAck: PubAck, local: ActorRef[Producer.ForwardPubAck]) extends Event

  sealed abstract class Command
  case object ForwardConnect extends Command
  final case class ForwardConnAck(connectData: ConnectData) extends Command
  case object ForwardDisconnect extends Command

  // State event handling

  private val ProducerNamePrefix = "producer-"
  private val SubscriberNamePrefix = "subscriber-"

  def disconnected(data: Uninitialized): Behavior[Event] = Behaviors.receiveMessagePartial {
    case ConnectReceivedLocally(connect, connectData, remote) =>
      remote ! ForwardConnect
      serverConnect(ConnectReceived(connect, connectData, data.packetIdAllocator, data.settings))
  }

  def serverConnect(data: ConnectReceived): Behavior[Event] = Behaviors.withTimers { timer =>
    timer.startSingleTimer("receive-connack", ReceiveConnAckTimeout, data.settings.receiveConnAckTimeout)
    Behaviors.receiveMessagePartial {
      case ConnAckReceivedFromRemote(connAck, local)
          if connAck.returnCode.contains(ConnAckReturnCode.ConnectionAccepted) =>
        local ! ForwardConnAck(data.connectData)
        if (data.connect.connectFlags.contains(ConnectFlags.CleanSession)) cleanSession()
        serverConnected(ConnAckReceived(data.connect, connAck, data.packetIdAllocator, data.settings))
      case ConnAckReceivedFromRemote(_, local) =>
        local ! ForwardConnAck(data.connectData)
        disconnected(Uninitialized(data.packetIdAllocator, data.settings))
      case ReceiveConnAckTimeout =>
        disconnected(Uninitialized(data.packetIdAllocator, data.settings))
    }
  }

  def serverConnected(data: ConnAckReceived): Behavior[Event] = Behaviors.receivePartial {
    case (_, ConnectionLost) =>
      disconnected(Uninitialized(data.packetIdAllocator, data.settings))
    case (_, DisconnectReceivedLocally(remote)) =>
      remote ! ForwardDisconnect
      cleanSession()
      disconnected(Uninitialized(data.packetIdAllocator, data.settings))
    case (context, SubscribeReceivedLocally(subscribe, subscribeData, remote)) =>
      subscribe.topicFilters.foreach { topicFilter =>
        val (topicFilterName, _) = topicFilter
        val subscriberName = SubscriberNamePrefix + topicFilterName
        context.child(subscriberName) match {
          case None =>
            context.spawn(Subscriber(topicFilterName, subscribeData, remote, data.packetIdAllocator, data.settings),
                          subscriberName)
          case _: Some[_] => // Ignored for existing subscriptions
        }
      }
      Behaviors.same
    // FIXME: Improve the way that messages are dispatched to child actors so they aren't broadcast - will need to key by packet id
    case (context, SubAckReceivedFromRemote(pubAck, local)) =>
      context.children.foreach {
        case child if child.path.name.startsWith(SubscriberNamePrefix) =>
          child.upcast ! Subscriber.SubAckReceivedFromRemote(pubAck, local)
        case _ =>
      }
      Behaviors.same
    case (context, PublishReceivedFromRemote(publish, local)) =>
      context.children.foreach {
        case child if child.path.name.startsWith(SubscriberNamePrefix) =>
          child.upcast ! Subscriber.PublishReceivedFromRemote(publish, local)
        case _ =>
      }
      Behaviors.same
    case (context, PubAckReceivedLocally(pubAck, remote)) =>
      context.children.foreach {
        case child if child.path.name.startsWith(SubscriberNamePrefix) =>
          child.upcast ! Subscriber.PubAckReceivedLocally(pubAck, remote)
        case _ =>
      }
      Behaviors.same
    case (context, PubRecReceivedLocally(pubRec, remote)) =>
      context.children.foreach {
        case child if child.path.name.startsWith(SubscriberNamePrefix) =>
          child.upcast ! Subscriber.PubRecReceivedLocally(pubRec, remote)
        case _ =>
      }
      Behaviors.same
    case (context, PubRelReceivedFromRemote(pubRel, local)) =>
      context.children.foreach {
        case child if child.path.name.startsWith(SubscriberNamePrefix) =>
          child.upcast ! Subscriber.PubRelReceivedFromRemote(pubRel, local)
        case _ =>
      }
      Behaviors.same
    case (context, PubCompReceivedLocally(pubComp, remote)) =>
      context.children.foreach {
        case child if child.path.name.startsWith(SubscriberNamePrefix) =>
          child.upcast ! Subscriber.PubCompReceivedLocally(pubComp, remote)
        case _ =>
      }
      Behaviors.same
    case (_, PublishReceivedLocally(publish, _, remote))
        if (publish.flags & ControlPacketFlags.QoSReserved).underlying == 0 =>
      remote ! Producer.ForwardPublish(None)
      Behaviors.same
    case (context, PublishReceivedLocally(publish, publishData, remote)) =>
      val producerName = ProducerNamePrefix + publish.topicName
      context.child(producerName) match {
        case None =>
          context.spawn(Producer(publish.flags, publishData, remote, data.packetIdAllocator, data.settings),
                        producerName)
        case _: Some[_] => // Ignored for existing subscriptions
      }
      Behaviors.same
    case (context, PubAckReceivedFromRemote(pubAck, local)) =>
      context.children.foreach {
        case child if child.path.name.startsWith(ProducerNamePrefix) =>
          child.upcast ! Producer.PubAckReceivedFromRemote(pubAck, local)
        case _ =>
      }
      Behaviors.same
    // TODO: UnsubscribedReceivedLocally, SubscribeReceivedLocally, PubRelReceivedFromRemote, PubAckReceivedLocally, PubRecReceivedLocally, PubCompReceivedLocally, PublishReceivedLocally, PubRecReceivedLocally, PubCompReceivedLocally, PubAckReceivedFromRemote, PubRelReceivedFromRemote
  }

  // Actions

  def cleanSession(): Unit = {} // TODO
}

/*
 * A subscriber manages the client state in relation to having made a
 * subscription to a a server-side topic. A subscriber is created
 * per server per topic.
 */
@InternalApi private[streaming] object Subscriber {

  type SubscribeData = Option[_]

  /*
   * Construct with the starting state
   */
  def apply(topicFilterName: String,
            subscribeData: SubscribeData,
            remote: ActorRef[ForwardSubscribe],
            packetIdAllocator: ActorRef[PacketIdAllocator.Request],
            settings: MqttSessionSettings): Behavior[Event] =
    prepareServerSubscribe(Start(topicFilterName, subscribeData, remote, packetIdAllocator, settings))

  // Our FSM data, FSM events and commands emitted by the FSM

  sealed abstract class Data(val topicFilterName: String, val settings: MqttSessionSettings)
  final case class Start(override val topicFilterName: String,
                         subscribeData: SubscribeData,
                         remote: ActorRef[ForwardSubscribe],
                         packetIdAllocator: ActorRef[PacketIdAllocator.Request],
                         override val settings: MqttSessionSettings)
      extends Data(topicFilterName, settings)
  final case class ServerSubscribe(override val topicFilterName: String,
                                   packetId: PacketId,
                                   subscribeData: SubscribeData,
                                   packetIdAllocator: ActorRef[PacketIdAllocator.Request],
                                   override val settings: MqttSessionSettings)
      extends Data(topicFilterName, settings)
  final case class ServerSubscribed(override val topicFilterName: String, override val settings: MqttSessionSettings)
      extends Data(topicFilterName, settings)
  final case class ServerPublishing(packetId: PacketId,
                                    flags: ControlPacketFlags,
                                    override val topicFilterName: String,
                                    override val settings: MqttSessionSettings)
      extends Data(topicFilterName, settings)

  sealed abstract class Event
  final case class AcquiredPacketId(packetId: PacketId) extends Event
  final case object UnacquiredPacketId extends Event
  final case class SubAckReceivedFromRemote(subAck: SubAck, local: ActorRef[ForwardSubAck]) extends Event
  case object ReceiveSubAckTimeout extends Event
  final case class PublishReceivedFromRemote(publish: Publish, local: ActorRef[ForwardPublish.type]) extends Event
  final case class PubAckReceivedLocally(pubAck: PubAck, remote: ActorRef[ForwardPubAck.type]) extends Event
  final case class PubRecReceivedLocally(pubRec: PubRec, remote: ActorRef[ForwardPubRec.type]) extends Event
  case object ReceivePubAckRecTimeout extends Event
  final case class PubRelReceivedFromRemote(pubRel: PubRel, local: ActorRef[ForwardPubRel.type]) extends Event
  case object ReceivePubRelTimeout extends Event
  final case class PubCompReceivedLocally(pubComp: PubComp, remote: ActorRef[ForwardPubComp.type]) extends Event
  case object ReceivePubCompTimeout extends Event

  sealed abstract class Command
  final case class ForwardSubscribe(packetId: PacketId) extends Command
  final case class ForwardSubAck(connectData: SubscribeData) extends Command
  case object ForwardPublish extends Command
  case object ForwardPubAck extends Command
  case object ForwardPubRec extends Command
  case object ForwardPubRel extends Command
  case object ForwardPubComp extends Command

  // State event handling

  def prepareServerSubscribe(data: Start): Behavior[Event] = Behaviors.setup { context =>
    implicit val actorMqttSessionTimeout: Timeout = data.settings.actorMqttSessionTimeout

    context.ask(data.packetIdAllocator)(PacketIdAllocator.Acquire) {
      case Success(acquired: PacketIdAllocator.Acquired) => AcquiredPacketId(acquired.packetId)
      case Failure(_) => UnacquiredPacketId
    }

    Behaviors.receiveMessagePartial {
      case AcquiredPacketId(packetId) =>
        data.remote ! ForwardSubscribe(packetId)
        serverSubscribe(
          ServerSubscribe(data.topicFilterName, packetId, data.subscribeData, data.packetIdAllocator, data.settings)
        )
      case UnacquiredPacketId =>
        Behaviors.stopped
    }
  }

  def serverSubscribe(data: ServerSubscribe): Behavior[Event] = Behaviors.withTimers { timer =>
    timer.startSingleTimer("receive-suback", ReceiveSubAckTimeout, data.settings.receiveSubAckTimeout)

    Behaviors.receiveMessagePartial {
      case SubAckReceivedFromRemote(subAck, local) if subAck.packetId == data.packetId =>
        local ! ForwardSubAck(data.subscribeData)
        data.packetIdAllocator ! PacketIdAllocator.Release(data.packetId)
        serverSubscribed(ServerSubscribed(data.topicFilterName, data.settings))
      case _: SubAckReceivedFromRemote =>
        Behaviors.same // Ignore sub acks not destined for us
      case ReceiveSubAckTimeout =>
        data.packetIdAllocator ! PacketIdAllocator.Release(data.packetId)
        Behaviors.stopped
    }
  }

  def serverSubscribed(data: ServerSubscribed): Behavior[Event] = Behaviors.receiveMessagePartial {
    case PublishReceivedFromRemote(publish, local) if matchTopicFilter(data.topicFilterName, publish.topicName) =>
      local ! ForwardPublish
      if ((publish.flags & ControlPacketFlags.QoSReserved).underlying == 0) {
        Behaviors.same
      } else {
        publish.packetId match {
          case Some(packetId) =>
            serverPublishingUnacknowledged(
              ServerPublishing(packetId, publish.flags, data.topicFilterName, data.settings)
            )
          case None =>
            Behaviors.same // We can't expect an ack if we don't have a means to correlate it
        }
      }
    case x =>
      Behaviors.same
  }

  def serverPublishingUnacknowledged(data: ServerPublishing): Behavior[Event] = Behaviors.withTimers { timer =>
    timer.startSingleTimer("receive-pubackrel", ReceivePubAckRecTimeout, data.settings.receivePubAckRecTimeout)
    Behaviors.receiveMessagePartial {
      case PubAckReceivedLocally(pubAck, remote)
          if data.flags.contains(ControlPacketFlags.QoSAtLeastOnceDelivery) && pubAck.packetId == data.packetId =>
        remote ! ForwardPubAck
        serverSubscribed(ServerSubscribed(data.topicFilterName, data.settings))
      case _: PubAckReceivedLocally =>
        Behaviors.same
      case PubRecReceivedLocally(pubRec, remote)
          if data.flags.contains(ControlPacketFlags.QoSExactlyOnceDelivery) && pubRec.packetId == data.packetId =>
        remote ! ForwardPubRec
        serverPublishingReceived(data)
      case _: PubRecReceivedLocally =>
        Behaviors.same
      case ReceivePubAckRecTimeout =>
        serverSubscribed(ServerSubscribed(data.topicFilterName, data.settings))
    }
  }

  def serverPublishingReceived(data: ServerPublishing): Behavior[Event] = Behaviors.withTimers { timer =>
    timer.startSingleTimer("receive-pubrel", ReceivePubRelTimeout, data.settings.receivePubRelTimeout)
    Behaviors.receiveMessagePartial {
      case PubRelReceivedFromRemote(pubRel, local) if pubRel.packetId == data.packetId =>
        local ! ForwardPubRel
        serverPublishingReleased(data)
      case _: PubRelReceivedFromRemote =>
        Behaviors.same
      case ReceivePubRelTimeout =>
        serverSubscribed(ServerSubscribed(data.topicFilterName, data.settings))
    }
  }

  def serverPublishingReleased(data: ServerPublishing): Behavior[Event] = Behaviors.withTimers { timer =>
    timer.startSingleTimer("receive-pubcomp", ReceivePubCompTimeout, data.settings.receivePubCompTimeout)
    Behaviors.receiveMessagePartial {
      case PubCompReceivedLocally(pubComp, remote) if pubComp.packetId == data.packetId =>
        remote ! ForwardPubComp
        serverSubscribed(ServerSubscribed(data.topicFilterName, data.settings))
      case _: PubRelReceivedFromRemote =>
        Behaviors.same
      case ReceivePubRelTimeout =>
        serverSubscribed(ServerSubscribed(data.topicFilterName, data.settings))
    }
  }

  //

  /*
   * 4.7 Topic Names and Topic Filters
   * http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html
   *
   * Inspired by https://github.com/eclipse/paho.mqtt.java/blob/master/org.eclipse.paho.client.mqttv3/src/main/java/org/eclipse/paho/client/mqttv3/MqttTopic.java#L240
   */
  def matchTopicFilter(topicFilterName: String, topicName: String): Boolean = {
    @tailrec
    def matchStrings(tfn: String, tn: String): Boolean =
      if (tfn == "/+" && tn == "/") {
        true
      } else if (tfn.nonEmpty && tn.nonEmpty) {
        val tfnHead = tfn.charAt(0)
        val tnHead = tn.charAt(0)
        if (tfnHead == '/' && tnHead != '/') {
          false
        } else if (tfnHead == '/' && tnHead == '/' && tn.length == 1) {
          matchStrings(tfn, tn.tail)
        } else if (tfnHead != '+' && tfnHead != '#' && tfnHead != tnHead) {
          false
        } else if (tfnHead == '+') {
          matchStrings(tfn.tail, tn.tail.dropWhile(_ != '/'))
        } else if (tfnHead == '#') {
          matchStrings(tfn.tail, "")
        } else {
          matchStrings(tfn.tail, tn.tail)
        }
      } else if (tfn.isEmpty && tn.isEmpty) {
        true
      } else if (tfn == "/#" && tn.isEmpty) {
        true
      } else {
        false
      }
    matchStrings(topicFilterName, topicName)
  }
}

/*
 * A producer manages the client state in relation to publishing to a a server-side topic.
 * A producer is created per server per topic.
 */
@InternalApi private[streaming] object Producer {

  type PublishData = Option[_]

  /*
   * Construct with the starting state
   */
  def apply(flags: ControlPacketFlags,
            publishData: PublishData,
            remote: ActorRef[ForwardPublish],
            packetIdAllocator: ActorRef[PacketIdAllocator.Request],
            settings: MqttSessionSettings): Behavior[Event] =
    preparePublish(Start(flags, publishData, remote, packetIdAllocator, settings))

  // Our FSM data, FSM events and commands emitted by the FSM

  sealed abstract class Data(val settings: MqttSessionSettings)
  final case class Start(flags: ControlPacketFlags,
                         publishData: PublishData,
                         remote: ActorRef[ForwardPublish],
                         packetIdAllocator: ActorRef[PacketIdAllocator.Request],
                         override val settings: MqttSessionSettings)
      extends Data(settings)
  final case class PublishUnacknowledged(flags: ControlPacketFlags,
                                         packetId: PacketId,
                                         publishData: PublishData,
                                         packetIdAllocator: ActorRef[PacketIdAllocator.Request],
                                         override val settings: MqttSessionSettings)
      extends Data(settings)

  sealed abstract class Event
  final case class AcquiredPacketId(packetId: PacketId) extends Event
  final case object UnacquiredPacketId extends Event
  case object ReceivePubAckRecTimeout extends Event
  final case class PubAckReceivedFromRemote(pubAck: PubAck, local: ActorRef[ForwardPubAck]) extends Event

  sealed abstract class Command
  final case class ForwardPublish(packetId: Option[PacketId]) extends Command
  final case class ForwardPubAck(publishData: PublishData) extends Command

  // State event handling

  def preparePublish(data: Start): Behavior[Event] = Behaviors.setup { context =>
    implicit val actorMqttSessionTimeout: Timeout = data.settings.actorMqttSessionTimeout

    context.ask(data.packetIdAllocator)(PacketIdAllocator.Acquire) {
      case Success(acquired: PacketIdAllocator.Acquired) => AcquiredPacketId(acquired.packetId)
      case Failure(_) => UnacquiredPacketId
    }

    Behaviors.receiveMessagePartial {
      case AcquiredPacketId(packetId) =>
        data.remote ! ForwardPublish(Some(packetId))
        publishUnacknowledged(
          PublishUnacknowledged(data.flags, packetId, data.publishData, data.packetIdAllocator, data.settings)
        )
      case UnacquiredPacketId =>
        Behaviors.stopped
    }
  }

  def publishUnacknowledged(data: PublishUnacknowledged): Behavior[Event] = Behaviors.withTimers { timer =>
    timer.startSingleTimer("receive-pubackrec", ReceivePubAckRecTimeout, data.settings.receivePubAckRecTimeout)

    Behaviors.receiveMessagePartial {
      case PubAckReceivedFromRemote(pubAck, local)
          if data.flags.contains(ControlPacketFlags.QoSAtLeastOnceDelivery) && pubAck.packetId == data.packetId =>
        local ! ForwardPubAck(data.publishData)
        data.packetIdAllocator ! PacketIdAllocator.Release(data.packetId)
        Behaviors.stopped
      case _: PubAckReceivedFromRemote =>
        Behaviors.stopped // Ignore sub acks not destined for us
      case ReceivePubAckRecTimeout =>
        data.packetIdAllocator ! PacketIdAllocator.Release(data.packetId)
        Behaviors.stopped
    } // TODO: PUBREC from remote
  }
}

/*
 * Manage packet identifiers for MQTT. Once released, they can
 * be re-used. The algorithm is optimised to return allocated
 * packet ids fast, and take the cost when releasing them as
 * the client isn't waiting on a reply.
 */
@InternalApi private[streaming] object PacketIdAllocator {

  /*
   * In case some brokers treat 0 as no packet id, we set our min to 1
   * e.g. https://renesasrulz.com/synergy/synergy_tech_notes/f/technical-bulletin-board-notification-postings/8998/mqtt-client-packet-identifier-is-0-by-default-which-causes-azure-iot-hub-to-reset-connection
   */
  private val MinPacketId = PacketId(1)
  private val MaxPacketId = PacketId(0xffff)

  /*
   * Construct with the starting state
   */
  def apply(): Behavior[Request] =
    main(Set.empty, MinPacketId)

  // Requests

  sealed abstract class Request
  final case class Acquire(replyTo: ActorRef[Acquired]) extends Request
  final case class Release(packetId: PacketId) extends Request

  // Replies

  sealed abstract class Reply
  final case class Acquired(packetId: PacketId) extends Reply

  // Processing

  def main(allocatedPacketIds: Set[PacketId], nextPacketId: PacketId): Behavior[Request] = Behaviors.receiveMessage {
    case Acquire(replyTo) if nextPacketId.underlying <= MaxPacketId.underlying =>
      replyTo ! Acquired(nextPacketId)
      main(allocatedPacketIds + nextPacketId, PacketId(nextPacketId.underlying + 1))
    case _: Acquire =>
      Behaviors.same // We cannot allocate any more. This will eventually cause a timeout to occur on the requestor.
    case Release(packetId) =>
      val remainingPacketIds = allocatedPacketIds - packetId
      val revisedNextPacketId = if (remainingPacketIds.nonEmpty) {
        val maxPacketId = PacketId(remainingPacketIds.map(_.underlying).max)
        PacketId(maxPacketId.underlying + 1)
      } else {
        MinPacketId
      }
      main(remainingPacketIds, revisedNextPacketId)
  }
}
