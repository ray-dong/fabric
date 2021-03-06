/**
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved.
 *
 *     http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */

package org.fusesource.fabric.apollo.amqp.broker

import org.fusesource.hawtdispatch._
import org.scalatest.matchers.ShouldMatchers
import java.util.concurrent.{CountDownLatch, TimeUnit}
import org.fusesource.fabric.apollo.amqp.api.Outcome._
import org.fusesource.fabric.apollo.amqp.api.{Message, Receiver, MessageListener, AmqpConnectionFactory}

/**
 *
 */

class SubscriptionTest extends BrokerTestSupport with ShouldMatchers {

  def createSubscription(linkName:String, address:String) = {
    val latch = new CountDownLatch(1)
    val client = AmqpConnectionFactory.create
    client.setOnClose(^{
      latch.countDown
    })
    client.connect(getConnectionUri, ^{
      val session = client.createSession
      session.begin(^{
        val receiver = session.createReceiver
        receiver.setName(linkName)
        receiver.setAddress(address)
        receiver.setSourceDurable(true)
        receiver.setOnDetach(^{
          session.end(^{
            client.close
          })
        })
        receiver.setListener(new MessageListener {
          def needLinkCredit(available: Long) = 0L
          def refiller(refiller: Runnable) {}
          def offer(receiver: Receiver, message: Message) = true
          def full() = false
        })
        receiver.attach(^{
          receiver.detach
        })
      })
    })
    latch.await(10, TimeUnit.SECONDS)
  }

  def sendMessages(address:String, max:Int) = {
    val latch = new CountDownLatch(1)
    val client = AmqpConnectionFactory.create
    client.setOnClose(^{
      latch.countDown
    })
    client.connect(getConnectionUri, ^{
      val session = client.createSession
      session.begin(^{
        val sender = session.createSender
        sender.setName("Sender")
        sender.setAddress(address)
        sender.setOnDetach(^{
          session.end(^{
            client.close
          })
        })
        sender.attach(^{
          def put(count:Int):Unit = {
            val message = sender.getSession.createMessage
            message.addBodyPart(("message " + count).getBytes)
            message.onAck(^{
              message.getOutcome match {
                case ACCEPTED =>
                  if (count >= max) {
                    sender.detach
                  } else {
                    put(count + 1)
                  }
                case _ =>
                  message.setSettled(false)
                  sender.put(message)
              }
            })
            sender.put(message)
          }
          put(1)
        })
      })
    })
    latch.await(max, TimeUnit.SECONDS)
  }


  test("Create 2 subscriptions, send some messages and then receive them") {
    val address = "topic:testtopic"
    val max = 50
    createSubscription("Receiver1", address) should be (true)
    createSubscription("Receiver2", address) should be (true)

    sendMessages(address, max) should be (true)

    val latch = new CountDownLatch(max * 2)
    val connection_latch = new CountDownLatch(1)

    val client = AmqpConnectionFactory.create
    client.setOnClose(^{
      connection_latch.countDown
    })
    client.connect(getConnectionUri, ^{
      val session = client.createSession
      session.begin(^{
        val receiver1 = session.createReceiver
        val receiver2 = session.createReceiver

        val listener = new MessageListener {
          def needLinkCredit(available: Long) = 1L
          def refiller(refiller: Runnable) {}
          def offer(receiver: Receiver, message: Message) = {
            if (!message.getSettled) {
              receiver.settle(message, ACCEPTED)
            }
            latch.countDown
            true
          }
          def full() = false
        }

        receiver1.setName("Receiver1")
        receiver2.setName("Receiver2")
        receiver1.setAddress(address)
        receiver2.setAddress(address)
        receiver1.setSourceDurable(true)
        receiver2.setSourceDurable(true)
        receiver1.setListener(listener)
        receiver2.setListener(listener)

        receiver1.attach(^{})
        receiver2.attach(^{})

      })
    })

    latch.await(max * 2, TimeUnit.SECONDS) should be (true)
    client.close
    connection_latch.await(10, TimeUnit.SECONDS) should be (true)

  }

}
