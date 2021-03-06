/*
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
 * http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.fabric.service;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import java.lang.management.ManagementFactory;

public class LocalJmxTemplate extends JmxTemplate {
    private MBeanServerConnection mbeanServerConnection;

    public LocalJmxTemplate() {
    }

    public LocalJmxTemplate(MBeanServerConnection mbeanServerConnection) {
        this.mbeanServerConnection = mbeanServerConnection;
    }

    @Override
    protected JMXConnector createConnector() {
        return new LocalJMXConnector(getMBeanServerConnection());
    }

    public MBeanServerConnection getMBeanServerConnection() {
        if (mbeanServerConnection == null) {
            mbeanServerConnection = ManagementFactory.getPlatformMBeanServer();
        }
        return mbeanServerConnection;
    }

    public void setMbeanServerConnection(MBeanServerConnection mbeanServerConnection) {
        this.mbeanServerConnection = mbeanServerConnection;
    }
}
