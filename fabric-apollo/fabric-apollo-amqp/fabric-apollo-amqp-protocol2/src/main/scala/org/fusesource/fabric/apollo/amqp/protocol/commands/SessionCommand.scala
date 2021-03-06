/*
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved
 *
 *    http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license, a copy of which has been included with this distribution
 * in the license.txt file
 */

package org.fusesource.fabric.apollo.amqp.protocol.commands

import org.fusesource.fabric.apollo.amqp.codec.interfaces.AMQPFrame

/**
 *
 */

class SessionCommand extends Command

object BeginSession {
  private val INSTANCE = new BeginSession
  def apply() = INSTANCE
}
class BeginSession extends SessionCommand

object EndSession {
  private val INSTANCE = new EndSession

  def apply() = INSTANCE

  def apply(reason:String) = {
    val rc = new EndSession
    rc.reason = Option(reason)
    rc
  }

  def apply(reason:Throwable) = {
    val rc = new EndSession
    rc.exception = Option(reason)
    rc
  }
}

class EndSession extends SessionCommand {
  var reason:Option[String] = None
  var exception:Option[Throwable] = None
}

object BeginSent {
  private val INSTANCE = new BeginSent
  def apply() = INSTANCE
}
class BeginSent extends SessionCommand

object BeginReceived {
  private val INSTANCE = new BeginReceived
  def apply() = INSTANCE
}
class BeginReceived extends SessionCommand

object EndSent {
  private val INSTANCE = new EndSent
  def apply() = INSTANCE
}
class EndSent extends SessionCommand

object EndReceived {
  private val INSTANCE = new EndReceived
  def apply() = INSTANCE
}
class EndReceived extends SessionCommand