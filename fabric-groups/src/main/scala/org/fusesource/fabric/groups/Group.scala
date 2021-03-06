/**
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved.
 *
 *     http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.fabric.groups

import internal.ZooKeeperGroup
import org.linkedin.zookeeper.client.IZKClient
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.ZooDefs.Ids
import java.util.LinkedHashMap

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object ZooKeeperGroupFactory {

  def create(zk: IZKClient, path: String):Group = create(zk, path, Ids.OPEN_ACL_UNSAFE)
  def create(zk: IZKClient, path: String, acl:java.util.List[ACL]):Group = new ZooKeeperGroup(zk, path, acl)
  def members(zk: IZKClient, path: String):LinkedHashMap[String, Array[Byte]] = ZooKeeperGroup.members(zk, path)
}

/**
 * <p>
 *   Used the join a cluster group and to monitor the memberships
 *   of that group.
 * </p>
 * <p>
 *   This object is not thread safe.  You should are responsible for
 *   synchronizing access to it across threads.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
trait Group {

  /**
   * Adds a member to the group with some associated data.
   */
  def join(data:Array[Byte]):String

  /**
   * Updates the data associated with joined member.
   */
  def update(id:String, data:Array[Byte]):Unit

  /**
   * Removes a previously added member.
   */
  def leave(id:String):Unit

  /**
   * Lists all the members currently in the group.
   */
  def members:java.util.LinkedHashMap[String, Array[Byte]]

  /**
   * Registers a change listener which will be called
   * when the cluster membership changes.
   */
  def add(listener:ChangeListener)

  /**
   * Removes a previously added change listener.
   */
  def remove(listener:ChangeListener)

  /**
   * A group should be closed to release aquired resources used
   * to monitor the group membership.
   *
   * Whe the Group is closed, any memberships registered via this
   * Group will be removed from the group.
   */
  def close:Unit

  /**
   * Are we connected with the cluster?
   */
  def connected:Boolean
}

/**
 * <p>
 *   Callback interface used to get notifications of changes
 *   to a cluster group.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
trait ChangeListener {
  def changed:Unit
  def connected:Unit
  def disconnected:Unit
}

