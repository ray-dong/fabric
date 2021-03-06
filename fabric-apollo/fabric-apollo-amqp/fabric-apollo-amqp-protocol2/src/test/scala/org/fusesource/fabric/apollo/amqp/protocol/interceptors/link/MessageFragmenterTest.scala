/*
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved
 *
 *    http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license, a copy of which has been included with this distribution
 * in the license.txt file
 */

package org.fusesource.fabric.apollo.amqp.protocol.interceptors.link

import org.apache.activemq.apollo.util.{Logging, FunSuiteSupport}
import org.scalatest.matchers.ShouldMatchers
import org.fusesource.fabric.apollo.amqp.codec.interfaces.AMQPFrame
import collection.mutable.Queue
import org.fusesource.fabric.apollo.amqp.codec.api.MessageFactory
import org.fusesource.hawtbuf.Buffer._
import org.fusesource.fabric.apollo.amqp.codec.marshaller.MessageSupport
import org.fusesource.fabric.apollo.amqp.codec.types._
import org.fusesource.fabric.apollo.amqp.protocol.interceptors.test_interceptors.{TaskExecutingInterceptor, TestSendInterceptor}
import org.fusesource.fabric.apollo.amqp.protocol.utilities.Tasks

/**
 *
 */

class MessageFragmenterTest extends FunSuiteSupport with ShouldMatchers with Logging {

  test("Send a message and split it all up") {

    var num_outgoing = 0
    val message = MessageFactory.createDataMessage(ascii("Hello world from AMQP land..."))
    val annotated_message = MessageFactory.createAnnotatedMessage(new Properties(new AMQPLong(0L), ascii("username"), new AMQPString("somewhere"), "Hello there!", new AMQPString("somewhere-else")), message)
    val payload = MessageSupport.toBuffer(annotated_message)

    val num_fragments = 3
    val fragment_size:Long = payload.length() / num_fragments

    val fragmenter = new MessageFragmenter
    fragmenter.max_message_size = fragment_size

    fragmenter.head.outgoing = new TestSendInterceptor((frame:AMQPFrame, tasks:Queue[() => Unit]) => {
      frame match {
        case t:AMQPTransportFrame =>
          printf("Outgoing frame : %s\n", t)
          num_outgoing = num_outgoing + 1
        case _ =>
      }

    })
    fragmenter.head.outgoing = new TaskExecutingInterceptor

    fragmenter.send(new AMQPTransportFrame(new Transfer(0L, 0L, ascii("foo"), 0L), payload), Tasks())

    // actually winds up being 4 fragments since the encoded length is 34 bytes
    num_outgoing should be (num_fragments + 1)

  }

}