/**
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
 * http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.fabric.fab.osgi.commands.module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;
import org.fusesource.fabric.fab.ModuleRegistry;
import org.fusesource.fabric.fab.VersionedDependencyId;
import org.fusesource.fabric.fab.osgi.commands.CommandSupport;
import org.fusesource.fabric.fab.osgi.internal.Activator;
import org.fusesource.fabric.fab.osgi.internal.OsgiModuleRegistry;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import static org.fusesource.fabric.fab.util.Strings.join;

@Command(name = "install", scope = "module", description = "Install a module")
public class InstallCommand extends CommandSupport {

    @Argument(index = 0, required = true, description = "Name of the module to install")
    private String name;

    @Argument(index = 1, required = false, description = "Extensions to enable or disable", multiValued = true)
    private String extensions[] = new String[]{};

    @Option(name = "--version", description = "Version to install")
    private String version;

    @Option(name = "--force", description = "Force install")
    private boolean force;

    @Option(name = "--no-start", description = "Do not start the module once installed")
    private boolean noStart;

    @Override
    protected Object doExecute() throws Exception {
        OsgiModuleRegistry registry = Activator.registry;
        List<ModuleRegistry.Module> modules = registry.getApplicationModules();
        Map<VersionedDependencyId, Bundle> installed = registry.getInstalled();
        for (ModuleRegistry.Module module : modules) {
            if( name.equals(module.getName()) ) {
                HashSet<VersionedDependencyId> s = new HashSet<VersionedDependencyId>(module.getVersionIds());
                s.retainAll(installed.keySet());
                boolean isInstalled = !s.isEmpty();
                if( isInstalled && !force) {
                    throw new Exception("The module is already installed");
                }

                ModuleRegistry.VersionedModule versionedModule = null;
                if( version!=null ) {
                    versionedModule = module.getVersions().get(version);
                    if( versionedModule ==null ) {
                        throw new Exception("Invalid version: "+version);
                    }
                } else {
                    versionedModule = module.latest();
                }

                VersionedDependencyId id = versionedModule.getId();
                String v = version!=null ? version : id.getVersion();

                List<String> extensionAdjustments = Arrays.asList(extensions);
                if( !extensionAdjustments.isEmpty() ) {
                    List<String> enabled = new ArrayList<String>(versionedModule.getDefaultExtensions());
                    for (String adjustment : extensionAdjustments) {
                        if( adjustment.startsWith("+") ) {
                            String name = adjustment.substring(1);
                            enabled.add(name);
                        } else if( adjustment.startsWith("-") ) {
                            String name = adjustment.substring(1);
                            enabled.remove(name);
                        } else {
                            throw new IllegalArgumentException("Expected extension argument '"+adjustment+"' to be prefixed with '+' or '-'");
                        }
                    }
                    versionedModule.setEnabledExtensions(enabled);
                    if( !enabled.isEmpty() ) {
                        println("Enabling extensions: "+join(enabled, ", "));
                    }
                }
                //

                String url = "fab:mvn:"+id.getGroupId()+"/"+id.getArtifactId()+"/"+v+"/"+id.getExtension()+
                        (id.getClassifier()==null ? "" : "/"+id.getClassifier());

                println("Installing: "+url);

                Bundle bundle = install(url);
                if( bundle!=null ) {
                    println("Installed bundle: %d", bundle.getBundleId());
                    if( !noStart ) {
                        bundle.start();
                    }
                }
            }
        }

        return null;
    }

    protected Bundle install(String location) {
        try {
            return getBundleContext().installBundle(location, null);
        } catch (BundleException ex) {
            if (ex.getNestedException() != null) {
                println(ex.getNestedException().toString());
            } else {
                println(ex.toString());
            }
        } catch (Exception ex) {
            println(ex.toString());
        }
        return null;
    }
}