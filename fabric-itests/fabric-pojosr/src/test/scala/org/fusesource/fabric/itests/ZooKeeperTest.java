/*
 * Copyright (C) 2011 FuseSource, Corp. All rights reserved.
 * http://fusesource.com
 *
 * The software in this package is published under the terms of the CDDL license
 * a copy of which has been included with this distribution in the license.txt file.
 */
package org.fusesource.fabric.itests;

import de.kalpatec.pojosr.framework.PojoServiceRegistryFactoryImpl;
import de.kalpatec.pojosr.framework.launch.ClasspathScanner;
import de.kalpatec.pojosr.framework.launch.PojoServiceRegistry;
import de.kalpatec.pojosr.framework.launch.PojoServiceRegistryFactory;
import org.apache.zookeeper.server.ServerStats;
import org.fusesource.fabric.zookeeper.ZkPath;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.linkedin.zookeeper.client.IZKClient;
import org.osgi.framework.*;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.util.tracker.ServiceTracker;

import java.util.*;

import static org.junit.Assert.assertNotNull;

public class ZooKeeperTest {

    public static final long DEFAULT_TIMEOUT = 30000;

    private BundleContext bundleContext;

    @Before
    public void setUp() throws Exception {

        System.setProperty("org.osgi.framework.storage", "target/osgi/" + System.currentTimeMillis());
        System.setProperty("karaf.name", "root");

        Map config = new HashMap();
        config.put(PojoServiceRegistryFactory.BUNDLE_DESCRIPTORS,
                   new ClasspathScanner().scanForBundles());
        PojoServiceRegistry reg = new PojoServiceRegistryFactoryImpl().newPojoServiceRegistry(config);
        bundleContext = reg.getBundleContext();

    }

    @After
    public void tearDown() throws Exception {
        bundleContext.getBundle().stop();
    }

    @Ignore
    @Test
    public void testZooKeeper() throws Exception {

        Bundle zooKeeperBundle = null;

        for (Bundle bundle : bundleContext.getBundles()) {
            System.err.println(bundle.getBundleId() + ": " + bundle.getSymbolicName() + " / " + bundle.getVersion());
            if (bundle.getSymbolicName() != null && bundle.getSymbolicName().contains("fabric-zookeeper")) {
                zooKeeperBundle = bundle;
            }
        }
        assertNotNull(zooKeeperBundle);

        ManagedServiceFactory msf = getOsgiService(ManagedServiceFactory.class, "(service.pid=org.fusesource.fabric.zookeeper.server)", DEFAULT_TIMEOUT);
        assertNotNull(msf);

        ConfigurationAdmin ca = getOsgiService(ConfigurationAdmin.class);
        Configuration cfgServer = ca.createFactoryConfiguration("org.fusesource.fabric.zookeeper.server");
        Properties props = new Properties();
        props.put("tickTime", "2000");
        props.put("initLimit", "10");
        props.put("syncLimit", "5");
        props.put("dataDir", "target/zookeeper");
        props.put("clientPort", "2181");
        cfgServer.setBundleLocation(null);
        cfgServer.update(props);

        Configuration cfgClient = ca.getConfiguration("org.fusesource.fabric.zookeeper");
        props = new Properties();
        props.put("zookeeper.url", "localhost:2181");
        cfgClient.setBundleLocation(null);
        cfgClient.update(props);

        ServerStats.Provider server = getOsgiService(ServerStats.Provider.class);
        assertNotNull(server);

        IZKClient client = getOsgiService(IZKClient.class);
        assertNotNull(client);

        Thread.sleep(100);

        assertNotNull(client.exists(ZkPath.AGENT_ALIVE.getPath(System.getProperty("karaf.name"))));
    }


    protected <T> T getOsgiService(Class<T> type, long timeout) {
        return getOsgiService(type, null, timeout);
    }

    protected <T> T getOsgiService(Class<T> type) {
        return getOsgiService(type, null, DEFAULT_TIMEOUT);
    }

    protected <T> T getOsgiService(Class<T> type, String filter, long timeout) {
        ServiceTracker tracker = null;
        try {
            String flt;
            if (filter != null) {
                if (filter.startsWith("(")) {
                    flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")" + filter + ")";
                } else {
                    flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")(" + filter + "))";
                }
            } else {
                flt = "(" + Constants.OBJECTCLASS + "=" + type.getName() + ")";
            }
            Filter osgiFilter = FrameworkUtil.createFilter(flt);
            tracker = new ServiceTracker(bundleContext, osgiFilter, null);
            tracker.open(true);
            // Note that the tracker is not closed to keep the reference
            // This is buggy, as the service reference may change i think
            Object svc = type.cast(tracker.waitForService(timeout));
            if (svc == null) {
                Dictionary dic = bundleContext.getBundle().getHeaders();
                System.err.println("Test bundle headers: " + explode(dic));

                for (ServiceReference ref : asCollection(bundleContext.getAllServiceReferences(null, null))) {
                    System.err.println("ServiceReference: " + ref);
                }

                for (ServiceReference ref : asCollection(bundleContext.getAllServiceReferences(null, flt))) {
                    System.err.println("Filtered ServiceReference: " + ref);
                }

                throw new RuntimeException("Gave up waiting for service " + flt);
            }
            return type.cast(svc);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Invalid filter", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * Explode the dictionary into a ,-delimited list of key=value pairs
     */
    private static String explode(Dictionary dictionary) {
        Enumeration keys = dictionary.keys();
        StringBuffer result = new StringBuffer();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            result.append(String.format("%s=%s", key, dictionary.get(key)));
            if (keys.hasMoreElements()) {
                result.append(", ");
            }
        }
        return result.toString();
    }

    /*
     * Provides an iterable collection of references, even if the original array is null
     */
    private static final Collection<ServiceReference> asCollection(ServiceReference[] references) {
        List<ServiceReference> result = new LinkedList<ServiceReference>();
        if (references != null) {
            for (ServiceReference reference : references) {
                result.add(reference);
            }
        }
        return result;
    }

}
