/**
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
 * http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.fusesource.fabric.dosgi.util;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.fusesource.fabric.dosgi.util.internal.BundleToClassLoaderAdapter;
import org.fusesource.fabric.dosgi.util.internal.DefaultWorker;
import org.fusesource.fabric.dosgi.util.internal.EquinoxWorker;
import org.fusesource.fabric.dosgi.util.internal.FelixWorker;
import org.fusesource.fabric.dosgi.util.internal.FrameworkUtilWorker;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

public final class AriesFrameworkUtil {

    private static final FrameworkUtilWorker WORKER;

    static {
        FrameworkUtilWorker w = null;
        Bundle b = FrameworkUtil.getBundle(AriesFrameworkUtil.class);
        if (b == null) {
            w  = new FrameworkUtilWorker() {
                public ClassLoader getClassLoader(final Bundle b) {
                    return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                        public ClassLoader run() {
                            return new BundleToClassLoaderAdapter(b);
                        }
                    });
                }

                public boolean isValid() {
                    return true;
                }
            };
        } else {
            String bundleClassName = b.getClass().getName();
            if (isEquinox(bundleClassName)) {
                w = new EquinoxWorker();
            } else if (isFelix(bundleClassName)) {
                w = new FelixWorker();
            }
            if (w == null || !w.isValid()) {
                w = new DefaultWorker();
            }
        }
        WORKER = w;
    }


    /**
     * This method attempts to get the classloader for a bundle. It may return null if
     * their is no such classloader, or if it cannot obtain the classloader for the bundle.
     *
     * @param b the bundle whose classloader is desired.
     * @return the classloader if found, or null.
     */
    public static ClassLoader getClassLoader(Bundle b) {
        if (b.getState() != Bundle.UNINSTALLED && b.getState() != Bundle.INSTALLED) {
            return WORKER.getClassLoader(b);
        } else {
            return null;
        }
    }

    /**
     * Returns true if we are in equinox, and we can access the interfaces we need.
     *
     * @param bundleClassName the class name of the bundle implementation.
     * @return true if we are in equinox, false otherwise.
     */
    private static boolean isEquinox(String bundleClassName) {
        if (bundleClassName != null && bundleClassName.startsWith("org.eclipse.equinox")) {
            try {
                Class.forName("org.eclipse.osgi.framework.internal.core.BundleHost");
                return true;
            } catch (ClassNotFoundException e) {
            }
        }
        return false;
    }

    /**
     * Returns true if we are in felix.
     *
     * @param bundleClassName the class name of the bundle implementation.
     * @return true if we are in felix, false otherwise.
     */
    private static boolean isFelix(String bundleClassName) {
        return bundleClassName != null && bundleClassName.startsWith("org.apache.felix");
    }

}