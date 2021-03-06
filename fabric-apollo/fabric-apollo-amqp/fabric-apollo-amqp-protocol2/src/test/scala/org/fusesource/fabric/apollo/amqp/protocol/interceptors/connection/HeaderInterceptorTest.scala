/*
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved
 *
 *    http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license, a copy of which has been included with this distribution
 * in the license.txt file
 */

package org.fusesource.fabric.apollo.amqp.protocol.interceptors.connection

import org.fusesource.hawtdispatch._
import org.scalatest.matchers.ShouldMatchers
import org.apache.activemq.apollo.util.{Logging, FunSuiteSupport}
import org.apache.activemq.apollo.transport.Transport
import org.fusesource.fabric.apollo.amqp.codec.interfaces.AMQPFrame
import collection.mutable.Queue
import org.fusesource.fabric.apollo.amqp.protocol.interfaces.Interceptor
import org.fusesource.fabric.apollo.amqp.protocol.commands.{CloseConnection, ConnectionCreated, HeaderSent}
import org.fusesource.fabric.apollo.amqp.protocol.utilities.Tasks
import org.fusesource.fabric.apollo.amqp.protocol.interceptors.test_interceptors.{FrameDroppingInterceptor, TerminationInterceptor, TaskExecutingInterceptor, TestSendInterceptor}
import org.fusesource.fabric.apollo.amqp.codec.types.AMQPProtocolHeader

/**
 *
 */
class HeaderInterceptorTest extends FunSuiteSupport with ShouldMatchers with Logging {

  def createInterceptorChain = {
    val dummy_in = new TestSendInterceptor((frame: AMQPFrame, tasks: Queue[() => Unit]) => {
      info("Frame : %s", frame)
      frame match {
        case a:AMQPProtocolHeader =>
        case c:CloseConnection =>
        case _ =>
          fail("Expecting either AMQPProtocolHeader or CloseConnection")

      }
    })

    val header_interceptor = new HeaderInterceptor

    dummy_in.outgoing = new TaskExecutingInterceptor
    dummy_in.incoming = header_interceptor

    header_interceptor.incoming = new FrameDroppingInterceptor
    dummy_in.queue = Dispatch.createQueue

    (dummy_in, Tasks())
  }

  test("Create interceptor, send header to it") {
    val (dummy_in, tasks) = createInterceptorChain
    dummy_in.receive(ConnectionCreated(null.asInstanceOf[Transport]), tasks)

    tasks should be ('empty)
  }

  test("Create interceptor, send wrong version to it") {
    val (dummy_in, tasks) = createInterceptorChain
    val bad = new AMQPProtocolHeader
    bad.revision = 1.asInstanceOf[Short]
    dummy_in.receive(bad, tasks)
  }


}