/*
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
 * http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */

package org.fusesource.fabric.fab.osgi.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import aQute.lib.osgi.Analyzer;
import org.apache.felix.utils.version.VersionCleaner;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.fusesource.fabric.fab.*;
import org.fusesource.fabric.fab.osgi.ServiceConstants;
import org.fusesource.fabric.fab.util.Filter;
import org.fusesource.fabric.fab.util.IOHelpers;
import org.fusesource.fabric.fab.util.Manifests;
import org.fusesource.fabric.fab.util.Maps;
import org.fusesource.fabric.fab.util.Strings;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.graph.Dependency;

import static org.fusesource.fabric.fab.ModuleDescriptor.FAB_MODULE_DESCRIPTION;
import static org.fusesource.fabric.fab.ModuleDescriptor.FAB_MODULE_ID;
import static org.fusesource.fabric.fab.ModuleDescriptor.FAB_MODULE_NAME;
import static org.fusesource.fabric.fab.ModuleDescriptor.FAB_MODULE_PROPERTIES;
import static org.fusesource.fabric.fab.util.Strings.emptyIfNull;
import static org.fusesource.fabric.fab.util.Strings.join;

/**
 * Resolves the classpath using the FAB resolving mechanism
 */
public class FabClassPathResolver {
    private static final transient Logger LOG = LoggerFactory.getLogger(FabClassPathResolver.class);

    private FabFacade connection;
    private Properties instructions;
    private Map<String, Object> embeddedResources;
    private HashMap<String, DependencyTree> dependenciesByPackage = new HashMap<String, DependencyTree>();
    private ModuleRegistry moduleRegistry;
    private List<String> bundleClassPath = new ArrayList<String>();
    private List<String> requireBundles = new ArrayList<String>();
    private Map<String, Map<String, String>> importPackages = new HashMap<String, Map<String, String>>();
    private boolean offline = false;

    HashSet<String> sharedFilterPatterns = new HashSet<String>();
    HashSet<String> requireBundleFilterPatterns = new HashSet<String>();
    HashSet<String> excludeDependencyFilterPatterns = new HashSet<String>();
    HashSet<String> optionalDependencyPatterns = new HashSet<String>();
    HashSet<String> importExportFilterPatterns = new HashSet<String>();

    private Filter<DependencyTree> sharedFilter;
    private Filter<DependencyTree> requireBundleFilter;
    private Filter<DependencyTree> excludeDependencyFilter;
    private Filter<DependencyTree> optionalDependencyFilter;
    private Filter<DependencyTree> importExportFilter;

    private List<DependencyTree> nonSharedDependencies = new ArrayList<DependencyTree>();
    private List<DependencyTree> sharedDependencies = new ArrayList<DependencyTree>();

    private List<DependencyTree> installDependencies = new ArrayList<DependencyTree>();
    private List<DependencyTree> optionalDependencies = new ArrayList<DependencyTree>();
    private DependencyTree rootTree;
    // don't think there's any need to even look at Import-Packages as BND takes care of it...
    private boolean processImportPackages = false;
    private MavenResolver resolver;
    private VersionedDependencyId moduleId;

    private Manifest manifest;

    public FabClassPathResolver(FabFacade connection, Properties instructions, Map<String, Object> embeddedResources) {
        this.connection = connection;
        this.instructions = instructions;
        this.embeddedResources = embeddedResources;
        this.moduleRegistry = Activator.registry;
        this.resolver = connection.getResolver();
    }


    public void resolve() throws RepositoryException, IOException, XmlPullParserException, BundleException {
        moduleId = connection.getVersionedDependencyId();
        if (moduleId == null) {
            return;
        }
        sharedFilterPatterns.addAll(Strings.splitAndTrimAsList(emptyIfNull(getManifestProperty(ServiceConstants.INSTR_FAB_PROVIDED_DEPENDENCY)), "\\s+"));
        requireBundleFilterPatterns.addAll(Strings.splitAndTrimAsList(emptyIfNull(getManifestProperty(ServiceConstants.INSTR_FAB_DEPENDENCY_REQUIRE_BUNDLE)), "\\s+"));
        excludeDependencyFilterPatterns.addAll(Strings.splitAndTrimAsList(emptyIfNull(getManifestProperty(ServiceConstants.INSTR_FAB_EXCLUDE_DEPENDENCY)), "\\s+"));
        optionalDependencyPatterns.addAll(Strings.splitAndTrimAsList(emptyIfNull(getManifestProperty(ServiceConstants.INSTR_FAB_OPTIONAL_DEPENDENCY)), "\\s+"));
        importExportFilterPatterns.addAll(Strings.splitAndTrimAsList(emptyIfNull(getManifestProperty(ServiceConstants.INSTR_FAB_IMPORT_DEPENDENCY_EXPORTS)), "\\s+"));

        sharedFilter = DependencyTreeFilters.parseShareFilter(join(sharedFilterPatterns, " "));
        requireBundleFilter = DependencyTreeFilters.parseRequireBundleFilter(join(requireBundleFilterPatterns, " "));
        optionalDependencyFilter = DependencyTreeFilters.parseExcludeOptionalFilter(join(optionalDependencyPatterns, " "));
        excludeDependencyFilter = DependencyTreeFilters.parseExcludeFilter(join(excludeDependencyFilterPatterns, " "), optionalDependencyFilter);
        importExportFilter  = DependencyTreeFilters.parse(join(importExportFilterPatterns, " "));

        bundleClassPath.addAll(Strings.splitAsList(getManifestProperty(ServiceConstants.INSTR_BUNDLE_CLASSPATH), ","));
        requireBundles.addAll(Strings.splitAsList(getManifestProperty(ServiceConstants.INSTR_REQUIRE_BUNDLE), ","));

        importPackages.putAll(new Analyzer().parseHeader(emptyIfNull(getManifestProperty(ServiceConstants.INSTR_IMPORT_PACKAGE))));


        Filter<Dependency> optionalFilter = DependencyFilters.parseExcludeOptionalFilter(join(optionalDependencyPatterns, " "));
        Filter<Dependency> excludeFilter = DependencyFilters.parseExcludeFilter(join(excludeDependencyFilterPatterns, " "), optionalFilter);

        this.rootTree = connection.collectDependencyTree(offline, excludeFilter);

        String name = getManifestProperty(ServiceConstants.INSTR_BUNDLE_SYMBOLIC_NAME);
        if (name.length() <= 0) {
            name = rootTree.getBundleSymbolicName();
            instructions.setProperty(ServiceConstants.INSTR_BUNDLE_SYMBOLIC_NAME, name);
        }
        String bundleVersion = getManifestProperty(Analyzer.BUNDLE_VERSION);
        if (bundleVersion.length() <= 0) {
            bundleVersion = VersionCleaner.clean(rootTree.getVersion());
            instructions.setProperty(Analyzer.BUNDLE_VERSION, bundleVersion);
        }

        // lets copy across all the FAB headers
        for (String propertyName : ServiceConstants.FAB_PROPERTY_NAMES) {
            String value = getManifestProperty(propertyName);
            if (value != null) {
                instructions.setProperty(propertyName, value);
            }
        }

        LOG.debug("Resolving Dependencies for: "+rootTree.getDependencyId());
        addDependencies(rootTree);

        // Build a ModuleDescriptor using the Jar Manifests headers..
        ModuleRegistry.VersionedModule module = moduleRegistry.getVersionedModule(moduleId);
        if (module == null || module.getFile() != null) {
            registerModule();
        }

        resolveExtensions(rootTree, excludeFilter);

        for (DependencyTree dependencyTree : sharedDependencies) {
            if (requireBundleFilter.matches(dependencyTree)) {
                // lets figure out the bundle ID etc...
                String bundleId = dependencyTree.getBundleSymbolicName();
                Version version = new Version(VersionCleaner.clean(dependencyTree.getVersion()));
                requireBundles.add(bundleId + ";bundle-version=" + version + "");
            } else {
                // TODO don't think we need to do anything now since already the BND stuff figures out the import packages for any missing stuff?
                if (processImportPackages) {
                    // lets add all the import packages...
                    importAllExportedPackages(dependencyTree);
                }
            }
        }

        for (DependencyTree dependencyTree : nonSharedDependencies) {
            if (dependencyTree.isValidLibrary()) {
                String url = dependencyTree.getUrl();
                if (url != null) {
                    String path = dependencyTree.getGroupId() + "." + dependencyTree.getArtifactId() + ".jar";

                    if (!bundleClassPath.contains(path)) {
                        // try use a file if it exists
                        File file = new File(url);
                        if (file.exists()) {
                            embeddedResources.put(path, file);
                        } else {
                            embeddedResources.put(path, new URL(url));
                        }
                        addBundleClassPath(path);
                    }
                }
            }
        }

        // Remove dup dependencies..
        nonSharedDependencies = filterOutDuplicates(nonSharedDependencies);
        sharedDependencies = filterOutDuplicates(sharedDependencies);
        installDependencies = filterOutDuplicates(installDependencies);
        optionalDependencies = filterOutDuplicates(optionalDependencies);

        LOG.debug("nonSharedDependencies:");
        for( DependencyTree d : nonSharedDependencies) {
            LOG.debug("  "+d.getDependencyId());
        }
        LOG.debug("sharedDependencies:");
        for( DependencyTree d : sharedDependencies) {
            LOG.debug("  "+d.getDependencyId());
        }
        LOG.debug("installDependencies:");
        for( DependencyTree d : installDependencies) {
            LOG.debug("  "+d.getDependencyId());
        }

        LOG.debug("resolved: bundleClassPath: " + Strings.join(bundleClassPath, "\t\n"));
        LOG.debug("resolved: requireBundles: " + Strings.join(requireBundles, "\t\n"));
        LOG.debug("resolved: importPackages: " + Strings.join(importPackages.keySet(), "\t\n"));

        instructions.setProperty(ServiceConstants.INSTR_BUNDLE_CLASSPATH, Strings.join(bundleClassPath, ","));
        instructions.setProperty(ServiceConstants.INSTR_REQUIRE_BUNDLE, Strings.join(requireBundles, ","));
        instructions.setProperty(ServiceConstants.INSTR_FAB_MODULE_ID, moduleId.toString());

        // Update the headers fab headers.. they may have been updated by the extensions.
        if( !sharedFilterPatterns.isEmpty() ) {
            instructions.setProperty(ServiceConstants.INSTR_FAB_PROVIDED_DEPENDENCY, join(sharedFilterPatterns, " "));
        }
        if( !requireBundleFilterPatterns.isEmpty() ) {
            instructions.setProperty(ServiceConstants.INSTR_FAB_DEPENDENCY_REQUIRE_BUNDLE, join(requireBundleFilterPatterns, " "));
        }
        if( !excludeDependencyFilterPatterns.isEmpty() ) {
            instructions.setProperty(ServiceConstants.INSTR_FAB_EXCLUDE_DEPENDENCY, join(excludeDependencyFilterPatterns, " "));
        }
        if( !optionalDependencyPatterns.isEmpty() ) {
            instructions.setProperty(ServiceConstants.INSTR_FAB_OPTIONAL_DEPENDENCY, join(optionalDependencyPatterns, " "));
        }

    }

    public List<DependencyTree> getInstallDependencies() {
        return installDependencies;
    }

    public List<DependencyTree> getSharedDependencies() {
        return sharedDependencies;
    }

    public List<DependencyTree> getNonSharedDependencies() {
        return nonSharedDependencies;
    }

    public boolean isOffline() {
        return offline;
    }

    public DependencyTree getRootTree() {
        return rootTree;
    }

    /**
     * Returns a Filter which returns true if the dependency should be treated as optional (and so excluded by default) or false if the dependency matches
     * the {@link ServiceConstants#INSTR_FAB_OPTIONAL_DEPENDENCY} pattern
     */
    public Filter<DependencyTree> getOptionalDependencyFilter() {
        return optionalDependencyFilter;
    }

    private List<DependencyTree> filterOutDuplicates(List<DependencyTree> list) {
        LinkedHashMap<DependencyId, DependencyTree> map = new LinkedHashMap<DependencyId, DependencyTree>();
        for (DependencyTree tree : list) {
            if( !map.containsKey(tree.getDependencyId()) ) {
                map.put(tree.getDependencyId(), tree);
            }
        }
        return new ArrayList<DependencyTree>(map.values());
    }

    private void registerModule() throws IOException, XmlPullParserException {
        try {
            Properties moduleProperties = new Properties();
            for( String key: FAB_MODULE_PROPERTIES) {
                String value = getManifestProperty("Fabric-" + key);
                if( Strings.notEmpty(value) ) {
                    moduleProperties.setProperty(key, value);
                }
            }
            // Enhance with maven pom information
            if( !moduleProperties.containsKey(FAB_MODULE_ID) ) {
                moduleProperties.setProperty(FAB_MODULE_ID, moduleId.toString());
            }
            if( !moduleProperties.containsKey(FAB_MODULE_NAME) ) {
                moduleProperties.setProperty(FAB_MODULE_NAME, moduleId.getArtifactId());
            }
            if( !moduleProperties.containsKey(FAB_MODULE_DESCRIPTION) ) {
                moduleProperties.setProperty(FAB_MODULE_DESCRIPTION, emptyIfNull(connection.getProjectDescription()));
            }

            ModuleDescriptor descriptor = ModuleDescriptor.fromProperties(moduleProperties);
            moduleRegistry.add(descriptor);

        } catch (Exception e) {
            System.err.println("Failed to register the fabric module for: "+moduleId);
            e.printStackTrace();
        }
    }

    protected void resolveExtensions(DependencyTree root, Filter<Dependency> excludeDependencyFilter) throws IOException, RepositoryException, XmlPullParserException {
        ModuleRegistry.VersionedModule module = moduleRegistry.getVersionedModule(moduleId);
        if( module!=null ) {
            Map<String, ModuleRegistry.VersionedModule> availableExtensions = module.getAvailableExtensions();
            String extensionsString="";
            for (String enabledExtension : module.getEnabledExtensions()) {
                ModuleRegistry.VersionedModule extensionModule = availableExtensions.get(enabledExtension);
                if( extensionModule!=null ) {
                    VersionedDependencyId id = extensionModule.getId();

                    // lets resolve the dependency
                    DependencyTreeResult result = resolver.collectDependencies(id, offline, excludeDependencyFilter);

                    if (result != null) {
                        DependencyTree tree = result.getTree();

                        sharedFilterPatterns.addAll(Strings.splitAndTrimAsList(emptyIfNull(tree.getManifestEntry(ServiceConstants.INSTR_FAB_PROVIDED_DEPENDENCY)), "\\s+"));
                        requireBundleFilterPatterns.addAll(Strings.splitAndTrimAsList(emptyIfNull(tree.getManifestEntry(ServiceConstants.INSTR_FAB_DEPENDENCY_REQUIRE_BUNDLE)), "\\s+"));
                        excludeDependencyFilterPatterns.addAll(Strings.splitAndTrimAsList(emptyIfNull(tree.getManifestEntry(ServiceConstants.INSTR_FAB_EXCLUDE_DEPENDENCY)), "\\s+"));
                        optionalDependencyPatterns.addAll(Strings.splitAndTrimAsList(emptyIfNull(tree.getManifestEntry(ServiceConstants.INSTR_FAB_OPTIONAL_DEPENDENCY)), "\\s+"));

                        sharedFilter = DependencyTreeFilters.parseShareFilter(join(sharedFilterPatterns, " "));
                        requireBundleFilter = DependencyTreeFilters.parseRequireBundleFilter(join(requireBundleFilterPatterns, " "));
                        optionalDependencyFilter = DependencyTreeFilters.parseExcludeOptionalFilter(join(optionalDependencyPatterns, " "));
                        this.excludeDependencyFilter = DependencyTreeFilters.parseExcludeFilter(join(excludeDependencyFilterPatterns, " "), optionalDependencyFilter);

                        LOG.debug("Adding extension: " + tree.getDependencyId());
                        if( extensionsString.length()!=0 ) {
                            extensionsString += " ";
                        }
                        extensionsString += id;
                        addChildDependency(tree);

                    } else {
                        LOG.debug("Could not resolve extension: " + id);
                    }
                }
            }
            if( extensionsString.length()!= 0 ) {
                instructions.put(ServiceConstants.INSTR_FAB_MODULE_ENABLED_EXTENSIONS, extensionsString);
            }
        }
    }

    protected void importAllExportedPackages(DependencyTree dependencyTree) {
        try {
            String text = dependencyTree.getManifestEntry(ServiceConstants.INSTR_EXPORT_PACKAGE);
            if (text != null && text.length() > 0) {
                Map<String, Map<String, String>> map = new Analyzer().parseHeader(text);
                for (Map.Entry<String, Map<String, String>> entry : map.entrySet()) {
                    String key = entry.getKey();
                    Map<String, String> values = entry.getValue();
                    // TODO add optional resolution if this dependency is otional??

                    Map<String, String> current = importPackages.get(key);
                    if (current == null) {
                        current = new HashMap<String, String>();
                        importPackages.put(key, current);
                    }

                    // only copy in the allowable import package parameters
                    Maps.putAll(current, values, ServiceConstants.IMPORT_PACKAGE_PARAMETERS);

                    // double check for 2 versions
                    String specVersion = current.get("specification-version");
                    if (specVersion != null) {
                        current.remove("specification-version");
                        String version = current.get("version");
                        if (version == null) {
                            current.put("version", connection.toVersionRange(specVersion));
                        } else {
                            LOG.warn("Have specification-version " + specVersion + " and version: " + version + " for dependency: " + dependencyTree + " will ignore specification-version");
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to find export packages for " + dependencyTree + ". " + e, e);
        }
        List<DependencyTree> children = dependencyTree.getChildren();
        for (DependencyTree child : children) {
            if (!child.isOptional()) {
                importAllExportedPackages(child);
            }
        }
    }


    public boolean isProcessImportPackages() {
        return processImportPackages;
    }

    public Manifest getManifest() {
        if( manifest == null ) {
            try {
                File jarFile = connection.getJarFile();
                if (jarFile != null && jarFile.exists()) {
                    manifest = Manifests.getManifest(jarFile);
                }
            } catch (IOException e) {
                // TODO: warn
                manifest = new Manifest();
            }
        }
        return manifest;
    }

    public String getManifestProperty(String name) {
        String answer = null;
        Manifest manifest = getManifest();
        if (manifest != null) {
            // TODO do some caching!!!
            answer = manifest.getMainAttributes().getValue(name);
        } else {
            answer = instructions.getProperty(name, "");
        }
        if (answer == null) {
            answer = "";
        }
        return answer;
    }


    /**
     * Recursively add all the package information for each node in the tree, before filtering takes place!
     */
    protected void addPackagesRecursive(DependencyTree tree) {
        addPackages(tree);

        List<DependencyTree> children = tree.getChildren();
        for (DependencyTree child : children) {
            if (isExcludedDependency(child)) {
                continue;
            } else if (isIncludedOptionaDependency(child)) {
                addPackages(child);

                // lets add all non-excluded descendents
                List<DependencyTree> descendants = child.getDescendants();
                for (DependencyTree descendant : descendants) {
                    if (!isExcludedDependency(descendant)) {
                        addPackages(descendant);
                    }
                }
            } else {
                addPackagesRecursive(child);
            }
        }
    }

    protected void addPackages(DependencyTree tree) {
        try {
            for (String p : tree.getPackages()) {
                DependencyTree current = dependenciesByPackage.get(p);
                if (current != null) {
                    String version1 = Versions.getOSGiPackageVersion(current, p);
                    String version2 = Versions.getOSGiPackageVersion(tree, p);
                    if (Versions.isVersionOlder(version1, version2)) {
                        // lets mark the current one as invalid
                        current.addHiddenPackage(p);

                        if (current.isAllPackagesHidden() && !current.isBundleFragment()) {
                            LOG.debug("Dependency now hidden: " + current + " due to " + tree);
                        }
                    } else {
                        // we already have the best dependency to use
                        continue;
                    }
                }
                dependenciesByPackage.put(p, tree);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void addDependencies(DependencyTree tree) throws IOException {
        // lets register all the packages then we can filter out unnecessary dependencies
        addPackagesRecursive(tree);

        List<DependencyTree> children = tree.getChildren();
        for (DependencyTree child : children) {
            addChildDependency(child);
        }
    }


    private void addChildDependency(DependencyTree child) throws IOException {
        String dependencyId = child.getDependencyId().toString();
        if (isExcludedDependency(child)) {
            // ignore
            LOG.debug("Excluded dependency: " + dependencyId);
        } else if (isIncludedOptionaDependency(child)) {
            addOptionalDependency(child);
        } else if (isSharedOrRequired(child)) {
            // lets add all the transitive dependencies as shared
            addSharedDependency(child);
        } else {
            LOG.debug("Added non-shared dependency: " + dependencyId);
            nonSharedDependencies.add(child);
            // we now need to recursively flatten all transitive dependencies (whether shared or not)
            addDependencies(child);
        }
    }

    protected boolean isIncludedOptionaDependency(DependencyTree child) {
        return optionalDependencyFilter.matches(child);
    }

    protected boolean isExcludedDependency(DependencyTree child) {
        return excludeDependencyFilter.matches(child) || (child.isAllPackagesHidden() && !child.isBundleFragment());
    }

    protected boolean isSharedOrRequired(DependencyTree child) {
        return sharedFilter.matches(child) || requireBundleFilter.matches(child);
    }

    private void addOptionalDependency(DependencyTree tree) {
        LOG.debug("Added optional dependency: " + tree.getDependencyId());
        optionalDependencies.add(tree);
        List<DependencyTree> list = tree.getDescendants();
        for (DependencyTree child : list) {
            if (isExcludedDependency(child)) {
                LOG.debug("Excluded transitive dependency: " + child.getDependencyId());
                continue;
            } else {
                addOptionalDependency(child);
            }
        }
    }

    protected void addSharedDependency(DependencyTree tree) throws IOException {
        LOG.debug("Added shared dependency: " + tree.getDependencyId());
        sharedDependencies.add(tree);
        boolean importExports = false;
        if (connection.isIncludeSharedResources()) {
            importExports = includeSharedResources(tree);
        }
        if (!importExports && importExportFilter.matches(tree)) {
            importExports = true;
        }
        if (importExports) {
            importAllExportedPackages(tree);
        }

        List<DependencyTree> list = tree.getChildren();
        for (DependencyTree child : list) {
            if (isExcludedDependency(child)) {
                LOG.debug("Excluded transitive dependency: " + child.getDependencyId());
                continue;
            } else if (isIncludedOptionaDependency(child)) {
                LOG.debug("Excluded optional transitive dependency: " + child.getDependencyId());
                continue;
            } else {
                addSharedDependency(child);
            }
            if (importExportFilter.matches(child)) {
                importAllExportedPackages(child);
            }
        }
        addInstallDependencies(tree);
    }

    /**
     * If there are any matching shared resources using the current filters
     * then extract them from the jar and create a new jar to be added to the Bundle-ClassPath
     * containing the shared resources; which are then added to the flat class path to avoid
     * breaking the META-INF/services contracts
     */
    protected boolean includeSharedResources(DependencyTree tree) throws IOException {
        boolean answer = false;
        if (tree.isValidLibrary()) {
            File file = tree.getJarFile();
            if (file.exists()) {
                File sharedResourceFile = null;
                JarOutputStream out = null;
                JarFile jarFile = new JarFile(file);
                String[] sharedPaths = connection.getConfiguration().getSharedResourcePaths();
                String pathPrefix = tree.getGroupId() + "." + tree.getArtifactId() + "-resources";
                String path = pathPrefix + ".jar";
                if (sharedPaths != null) {
                    for (String sharedPath : sharedPaths) {
                        Enumeration<JarEntry> entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry jarEntry = entries.nextElement();
                            String name = jarEntry.getName();
                            if (name.startsWith(sharedPath)) {
                                if (sharedResourceFile == null) {
                                    sharedResourceFile = File.createTempFile(pathPrefix + "-", ".jar");
                                    out = new JarOutputStream(new FileOutputStream(sharedResourceFile));
                                }
                                out.putNextEntry(new ZipEntry(jarEntry.getName()));
                                if (!jarEntry.isDirectory()) {
                                    IOHelpers.writeTo(out, jarFile.getInputStream(jarEntry), false);
                                }
                                out.closeEntry();
                            }
                        }
                    }
                }
                if (sharedResourceFile != null) {
                    out.finish();
                    out.close();
                    if (!bundleClassPath.contains(path)) {
                        embeddedResources.put(path, sharedResourceFile);
                        addBundleClassPath(path);
                        LOG.info("Adding shared resources jar: " + path);

                        // lets add the imports from this bundle's exports...
                        // as we are probably using META-INF/services type stuff
                        answer = true;
                    }
                }
            }
        }
        return answer;
    }

    protected void addBundleClassPath(String path) {
        if (bundleClassPath.isEmpty()) {
            bundleClassPath.add(".");
        }
        bundleClassPath.add(path);
    }

    protected void addInstallDependencies(DependencyTree node) {
        // we only transitively walk bundle dependencies as any non-bundle
        // dependencies will be installed using FAB which will deal with
        // non-shared or shared transitive dependencies
        if (node.isBundle()) {
            List<DependencyTree> list = node.getChildren();
            for (DependencyTree child : list) {
                if (isExcludedDependency(child)) {
                    continue;
                } else if (child.isThisOrDescendantOptional() && isIncludedOptionaDependency(child)) {
                    continue;
                } else {
                    addInstallDependencies(child);
                }
            }
        }
        // lets add the child dependencies first so we add things in the right order
        installDependencies.add(node);
    }

    public Map<String, Map<String, String>> getExtraImportPackages() {
        return importPackages;
    }

    public Map<String, DependencyTree> getDependenciesByPackage() {
        return dependenciesByPackage;
    }

    public List<DependencyTree> getOptionalDependencies() {
        return optionalDependencies;
    }
}
