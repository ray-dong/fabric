/**
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved.
 *
 *     http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.fabric.monitor.plugins

import jmx.JmxDataSourceRegistry
import org.fusesource.fabric.api.monitor.{ArchiveDTO, PollDTO, DataSourceDTO, MonitoredSetDTO}
import org.fusesource.fabric.api.monitor._

/**
 * A helper class for building a monitor set
 */
abstract class MonitorSetBuilder(name: String) {
  var set: MonitoredSetDTO = _
  val jmxFactory = new JmxDataSourceRegistry()

  def apply(): MonitoredSetDTO = {
    set = new MonitoredSetDTO(name)
    configure
    set
  }

  def configure: Unit

  def archive( window: String, step: String = null,consolidation: String = "AVERAGE") = {
    val a = new ArchiveDTO(consolidation, step, window)
    set.archives.add(a)
    a
  }

  def dataSource(poll: PollDTO, id: String, name: String = null, description: String = null, kind: String = "gauge", heartbeat: String = "1s", min: Double = Double.NaN, max: Double = Double.NaN) = {
    var n = if (name == null) id else name
    var d = if (description == null) n else description
    val ds = DataSourceEnricher(new DataSourceDTO(id, n, d, kind, heartbeat, min, max, poll))
    addDataSource(ds)
    ds
  }

  def jmxDataSource(objectName: String, attributeName: String, key: String=null): Option[DataSourceDTO] = {
    def error_message = if (key==null)
      "No attribute " + attributeName + " in MBean " + objectName
    else
      "No key " + key + " in MBean " + objectName + " attribute " + attributeName

    val answer = jmxFactory.createDataSource(objectName, attributeName, key)
    addDataSource(answer, error_message)
  }

  def processPoll(name: String) = {
    // TODO!
    new ProcessPollDTO()
  }

  def addDataSource(ds: DataSourceDTO): Unit = {
    set.data_sources.add(ds)
  }

  def addDataSource(answer: Option[DataSourceDTO], message: => String): Option[DataSourceDTO] = {
    answer match {
      case Some(ds) => addDataSource(ds)
      case _ =>
    }
    answer
  }


}