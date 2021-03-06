/**
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
 * http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.fabric.fab.osgi.commands.module;

import org.apache.felix.gogo.commands.Command;
import org.fusesource.fabric.fab.osgi.internal.Activator;
import org.fusesource.fabric.fab.osgi.commands.CommandSupport;

@Command(name = "update", scope = "module", description = "Update the modules")
public class UpdateCommand extends CommandSupport {

    @Override
    protected Object doExecute() throws Exception {
        Activator.registry.update(session.getConsole());
        return null;
    }


}