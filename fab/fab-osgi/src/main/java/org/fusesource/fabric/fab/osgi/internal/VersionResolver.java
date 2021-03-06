/**
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
 * http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.fabric.fab.osgi.internal;

/**
 */
public interface VersionResolver {
    String resolvePackageVersion(String packageName);

    boolean isPackageOptional(String packageName);

    String resolveExportPackageVersion(String packageName);
}
