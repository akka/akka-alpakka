/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.jms

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

import akka.Done
import akka.stream._
import akka.stream.stage._
import akka.util.OptionVal
import javax.jms._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.{Await, Future, TimeoutException}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

private[jms] final class JmsConsumerStage(settings: JmsConsumerSettings)
    extends GraphStageWithMaterializedValue[SourceShape[Message], KillSwitch] {

  private val out = Outlet[Message]("JmsConsumer.out")

  override def shape: SourceShape[Message] = SourceShape[Message](out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, KillSwitch) = {
    val logic = new SourceStageLogic[Message](shape, out, settings, inheritedAttributes) {

      private val bufferSize = (settings.bufferSize + 1) * settings.sessionCount

      private val backpressure = new Semaphore(bufferSize)

      protected def createSession(connection: Connection,
                                  createDestination: Session => javax.jms.Destination): JmsConsumerSession = {
        val session =
          connection.createSession(false, settings.acknowledgeMode.getOrElse(AcknowledgeMode.AutoAcknowledge).mode)
        new JmsConsumerSession(connection, session, createDestination(session), settings.destination.get)
      }

      protected def pushMessage(msg: Message): Unit = {
        push(out, msg)
        backpressure.release()
      }

      override protected def onSessionOpened(jmsSession: JmsConsumerSession): Unit =
        jmsSession
          .createConsumer(settings.selector)
          .onComplete {
            case Success(consumer) =>
              consumer.setMessageListener(new MessageListener {
                def onMessage(message: Message): Unit = {
                  backpressure.acquire()
                  handleMessage.invoke(message)
                }
              })
            case Failure(e) =>
              fail.invoke(e)
          }
    }

    (logic, logic.killSwitch)
  }
}

final class JmsAckSourceStage(settings: JmsConsumerSettings)
    extends GraphStageWithMaterializedValue[SourceShape[AckEnvelope], KillSwitch] {

  private val out = Outlet[AckEnvelope]("JmsSource.out")

  override def shape: SourceShape[AckEnvelope] = SourceShape[AckEnvelope](out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, KillSwitch) = {

    val logic = new SourceStageLogic[AckEnvelope](shape, out, settings, inheritedAttributes) {
      private val maxPendingAck = settings.bufferSize

      protected def createSession(connection: Connection,
                                  createDestination: Session => javax.jms.Destination): JmsAckSession = {
        val session =
          connection.createSession(false, settings.acknowledgeMode.getOrElse(AcknowledgeMode.ClientAcknowledge).mode)
        new JmsAckSession(connection,
                          session,
                          createDestination(session),
                          settings.destination.get,
                          settings.bufferSize)
      }

      protected def pushMessage(msg: AckEnvelope): Unit = push(out, msg)

      override protected def onSessionOpened(jmsSession: JmsConsumerSession): Unit =
        jmsSession match {
          case session: JmsAckSession =>
            session.createConsumer(settings.selector).onComplete {
              case Success(consumer) =>
                consumer.setMessageListener(new MessageListener {

                  var listenerStopped = false

                  def onMessage(message: Message): Unit = {

                    @tailrec
                    def ackQueued(): Unit =
                      OptionVal(session.ackQueue.poll()) match {
                        case OptionVal.Some(action) =>
                          try {
                            action()
                            session.pendingAck -= 1
                          } catch {
                            case _: StopMessageListenerException =>
                              listenerStopped = true
                          }
                          if (!listenerStopped) ackQueued()
                        case OptionVal.None =>
                      }

                    if (!listenerStopped)
                      try {
                        handleMessage.invoke(AckEnvelope(message, session))
                        session.pendingAck += 1
                        if (session.pendingAck > maxPendingAck) {
                          val action = session.ackQueue.take()
                          action()
                          session.pendingAck -= 1
                        }
                        ackQueued()
                      } catch {
                        case _: StopMessageListenerException =>
                          listenerStopped = true
                        case e: JMSException =>
                          handleError.invoke(e)
                      }
                  }
                })
              case Failure(e) =>
                fail.invoke(e)
            }

          case _ =>
            throw new IllegalArgumentException(
              "Session must be of type JMSAckSession, it is a " +
              jmsSession.getClass.getName
            )
        }
    }

    (logic, logic.killSwitch)
  }
}

final class JmsTxSourceStage(settings: JmsConsumerSettings)
    extends GraphStageWithMaterializedValue[SourceShape[TxEnvelope], KillSwitch] {

  private val out = Outlet[TxEnvelope]("JmsSource.out")

  override def shape: SourceShape[TxEnvelope] = SourceShape[TxEnvelope](out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, KillSwitch) = {
    val logic = new SourceStageLogic[TxEnvelope](shape, out, settings, inheritedAttributes) {
      protected def createSession(connection: Connection, createDestination: Session => javax.jms.Destination) = {
        val session =
          connection.createSession(true, settings.acknowledgeMode.getOrElse(AcknowledgeMode.SessionTransacted).mode)
        new JmsConsumerSession(connection, session, createDestination(session), settings.destination.get)
      }

      protected def pushMessage(msg: TxEnvelope): Unit = push(out, msg)

      override protected def onSessionOpened(jmsSession: JmsConsumerSession): Unit =
        jmsSession match {
          case session: JmsSession =>
            session.createConsumer(settings.selector).onComplete {
              case Success(consumer) =>
                consumer.setMessageListener(new MessageListener {

                  def onMessage(message: Message): Unit =
                    try {
                      val envelope = TxEnvelope(message, session)
                      handleMessage.invoke(envelope)
                      val action = Await.result(envelope.commitFuture, settings.ackTimeout)
                      action()
                    } catch {
                      case _: TimeoutException => session.session.rollback()
                      case e: IllegalArgumentException => handleError.invoke(e) // Invalid envelope. Fail the stage.
                      case e: JMSException => handleError.invoke(e)
                    }
                })
              case Failure(e) =>
                fail.invoke(e)
            }

          case _ =>
            throw new IllegalArgumentException(
              "Session must be of type JMSAckSession, it is a " +
              jmsSession.getClass.getName
            )
        }
    }

    (logic, logic.killSwitch)
  }
}

abstract class SourceStageLogic[T](shape: SourceShape[T],
                                   out: Outlet[T],
                                   settings: JmsConsumerSettings,
                                   inheritedAttributes: Attributes)
    extends GraphStageLogic(shape)
    with JmsConsumerConnector
    with StageLogging {

  override protected def jmsSettings: JmsConsumerSettings = settings
  private val queue = mutable.Queue[T]()
  private val stopping = new AtomicBoolean(false)
  private var stopped = false

  private val markStopped = getAsyncCallback[Done.type] { _ =>
    stopped = true
    if (queue.isEmpty) completeStage()
  }

  private val markAborted = getAsyncCallback[Throwable] { ex =>
    stopped = true
    failStage(ex)
  }

  private[jms] val handleError = getAsyncCallback[Throwable] { e =>
    fail(out, e)
  }

  override def preStart(): Unit = {
    ec = executionContext(inheritedAttributes)
    initSessionAsync()
  }

  private[jms] val handleMessage = getAsyncCallback[T] { msg =>
    if (isAvailable(out)) {
      if (queue.isEmpty) {
        pushMessage(msg)
      } else {
        pushMessage(queue.dequeue())
        queue.enqueue(msg)
      }
    } else {
      queue.enqueue(msg)
    }
  }

  protected def pushMessage(msg: T): Unit

  setHandler(out, new OutHandler {
    override def onPull(): Unit = {
      if (queue.nonEmpty) pushMessage(queue.dequeue())
      if (stopped && queue.isEmpty) completeStage()
    }
  })

  private def stopSessions(): Unit =
    if (stopping.compareAndSet(false, true)) {
      val closeSessionFutures = jmsSessions.map { s =>
        val f = s.closeSessionAsync()
        f.failed.foreach(e => log.error(e, "Error closing jms session"))
        f
      }
      Future
        .sequence(closeSessionFutures)
        .onComplete { _ =>
          jmsConnection.foreach { connection =>
            try {
              connection.close()
            } catch {
              case NonFatal(e) => log.error(e, "Error closing JMS connection {}", connection)
            } finally {
              // By this time, after stopping connection, closing sessions, all async message submissions to this
              // stage should have been invoked. We invoke markStopped as the last item so it gets delivered after
              // all JMS messages are delivered. This will allow the stage to complete after all pending messages
              // are delivered, thus preventing message loss due to premature stage completion.
              markStopped.invoke(Done)
            }
          }
        }
    }

  private def abortSessions(ex: Throwable): Unit =
    if (stopping.compareAndSet(false, true)) {
      val abortSessionFutures = jmsSessions.map { s =>
        val f = s.abortSessionAsync()
        f.failed.foreach(e => log.error(e, "Error closing jms session"))
        f
      }
      Future
        .sequence(abortSessionFutures)
        .onComplete { _ =>
          jmsConnection.foreach { connection =>
            try {
              connection.close()
              log.info("JMS connection {} closed", jmsConnection)
            } catch {
              case NonFatal(e) => log.error(e, "Error closing JMS connection {}", jmsConnection)
            } finally {
              markAborted.invoke(ex)
            }
          }
        }
    }

  private[jms] def killSwitch = new KillSwitch {
    override def shutdown(): Unit = stopSessions()
    override def abort(ex: Throwable): Unit = abortSessions(ex)
  }

  override def postStop(): Unit = {
    queue.clear()
    stopSessions()
  }
}
