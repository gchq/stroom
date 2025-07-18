package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.api.ExportSummary;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.api.ImportExportSerializer;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;

import jakarta.inject.Inject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Version 2.0 of the ImportExportSerializer. Exports in version 2.0 format.
 * If the import is version 2.0 then imports it directly. Otherwise hands
 * the import over to the old ImportExportSerializerImpl.
 */
public class ImportExportSerializerImplV2 implements ImportExportSerializer {

    /** Extension of the node file */
    private final static String NODE_EXTENSION = ".node";

    /** Key for version in .node files */
    private final static String VERSION_KEY = "version";

    /** Possible versions of format on disk */
    enum Version {
        V1,
        V2
    }

    /** Version 1 implementation */
    private final ImportExportSerializer importExportSerializerV1;

    /** */
    private final ExplorerService explorerService;

    /** Used to find the children of a given node */
    private final ExplorerNodeService explorerNodeService;

    /** Provides action handles to allow us to export each node type */
    private final ImportExportActionHandlersImpl importExportActionHandlers;

    /** Security info */
    private final SecurityContext securityContext;

    /** Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportSerializerImplV2.class);

    /**
     * Injected constructor.
     * @param importExportSerializerV1 Serializer for version 1 format.
     */
    @Inject
    public ImportExportSerializerImplV2(final ImportExportSerializerImpl importExportSerializerV1,
                                        final ExplorerService explorerService,
                                        final ExplorerNodeService explorerNodeService,
                                        final ImportExportActionHandlersImpl importExportActionHandlers,
                                        final SecurityContext securityContext) {
        this.importExportSerializerV1 = importExportSerializerV1;
        this.explorerService = explorerService;
        this.explorerNodeService = explorerNodeService;
        this.importExportActionHandlers = importExportActionHandlers;
        this.securityContext = securityContext;
    }

    /**
     * Call to read the serialised format on disk.
     * @param dir             directory containing serialized DocRef items, e.g. files created by
     *                        ImportExportSerializer.write()
     * @param importStateList
     * @param importSettings
     * @return DocRefs of items read from disk.
     */
    @Override
    public Set<DocRef> read(final Path dir,
                            final List<ImportState> importStateList,
                            final ImportSettings importSettings) {

        // Check any node file and if not version 2 then hand to version 1
        final Set<DocRef> docRefs;
        try {
            final Version version = getVersionFromDisk(dir);

            if (version == Version.V2) {
                docRefs = doV2Read(dir, importStateList, importSettings);
            } else {
                docRefs = importExportSerializerV1.read(
                        dir,
                        importStateList,
                        importSettings);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        return docRefs;
    }

    /**
     * Search for a .node file under 'dir', open it
     * and return the version therein.
     * @param dir The root directory of the file structure to import.
     * @return The version of structure to import.
     */
    private @NonNull Version getVersionFromDisk(@NonNull final Path dir) throws IOException {
        final Version[] version = {Version.V1};
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            @NonNull
            public FileVisitResult visitFile(@NonNull final Path file,
                                             @NonNull final BasicFileAttributes attrs)
            throws IOException {
                final Path fileName = file.getFileName();
                if (fileName != null) {
                    if (fileName.toString().endsWith(NODE_EXTENSION)) {
                        version[0] = getVersionFromNodeFile(file);
                        return FileVisitResult.TERMINATE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return version[0];
    }

    /**
     * Given a node file, opens it and extracts the 'version' key.
     * Then uses that key (if present) to determine the version of the
     * structure on disk.
     * @param nodeFile A file with a .node extension.
     * @return The version of the structure on disk.
     * @throws IOException if something goes wrong.
     */
    private @NonNull Version getVersionFromNodeFile(@NonNull final Path nodeFile) throws IOException {
        try (final InputStream inputStream = Files.newInputStream(nodeFile)) {
            final Properties properties = PropertiesSerialiser.read(inputStream);
            final String version = properties.getProperty(VERSION_KEY);
            if (version != null && version.equals(Version.V2.name())) {
                return Version.V2;
            } else {
                return Version.V1;
            }
        }
    }

    /**
     * Does the import for the version 2 structure.
     * @param dir
     * @param importStateList
     * @param importSettings
     * @return
     * @throws IOException
     */
    public @NonNull Set<DocRef> doV2Read(@NonNull final Path dir,
                                         @NonNull final List<ImportState> importStateList,
                                         @NonNull final ImportSettings importSettings)
    throws IOException {

        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                @NonNull
                public FileVisitResult visitFile(@NonNull final Path file,
                                                 @NonNull final BasicFileAttributes attrs)
                        throws IOException {
                    final Path fileName = file.getFileName();
                    if (fileName != null) {
                        // TODO
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException e) {
            throw new IOException("Error reading version 2 structure: " + e.getMessage(), e);
        }

        return Collections.emptySet();
    }

    /**
     * Writes data out in version 2 structure.
     * @param rootNodePath     Path to root node of the export. If null then
     *                         starts at the System node.
     *                         Otherwise removes these path elements from the start of
     *                         the exported path. Normally this should be the path to the
     *                         GitRepo node, including that node.
     * @param dir              Where to serialize the DocRef items to on disk.
     * @param docRefs          Set of the DocRefs to serialize.
     * @param typesToIgnore    Set of the Doc types that shouldn't be exported, nor
     *                         their children. Must not be null.
     * @param omitAuditFields  Do not export audit fields.
     * @return The summary of the export.
     */
    @Override
    public @NonNull ExportSummary write(@Nullable final List<ExplorerNode> rootNodePath,
                                        @NonNull final Path dir,
                                        @NonNull final Set<DocRef> docRefs,
                                        @NonNull final Set<String> typesToIgnore,
                                        final boolean omitAuditFields) {
        LOGGER.info("---- Exporting {} with refs {}", rootNodePath, docRefs);

        try {
            final ExplorerNode rootNode;
            if (rootNodePath == null || rootNodePath.isEmpty()) {
                rootNode = ExplorerConstants.SYSTEM_NODE;
            } else {
                rootNode = rootNodePath.getLast();
            }

            final Deque<ExplorerNode> pathToCurrentNode = new ArrayDeque<>();
            pathToCurrentNode.add(rootNode); // TODO ?

            final ExportInfo exportInfo =
                    new ExportInfo(dir, docRefs, typesToIgnore, omitAuditFields);
            searchForNodesToExport(rootNode, pathToCurrentNode, exportInfo);

        } catch (final IOException e) {
            throw new RuntimeException("Error exporting: " + e.getMessage(), e);
        }
        return new ExportSummary();
    }

    /**
     * Recursively called to search the Explorer Tree.
     * @param currentNode The node we're looking at in this method call.
     * @param pathToCurrentNode How we got here, from the rootNode down
     *                          to the currentNode.
     * @param exportInfo Static information about this export.
     * @throws IOException if something goes wrong.
     */
    private void searchForNodesToExport(@NonNull final ExplorerNode currentNode,
                                        @NonNull final Deque<ExplorerNode> pathToCurrentNode,
                                        @NonNull final ExportInfo exportInfo) throws IOException {

        //LOGGER.info("{} Searching {}", "=".repeat(pathToCurrentNode.size()), currentNode);

        final List<ExplorerNode> children = explorerNodeService.getChildren(currentNode.getDocRef());
        for (final ExplorerNode child : children) {
            if (exportInfo.getDocRefsToExport().contains(child.getDocRef())) {
                LOGGER.info("{} Node must be exported!", "=".repeat(pathToCurrentNode.size()));
                try {
                    pathToCurrentNode.push(child);
                    exportEverything(child, pathToCurrentNode, exportInfo);
                } finally {
                    pathToCurrentNode.pop();
                }
            } else {
                if (ExplorerConstants.isFolder(child)) {
                    try {
                        // Recurse
                        pathToCurrentNode.push(child);
                        searchForNodesToExport(child, pathToCurrentNode, exportInfo);
                    } finally {
                        pathToCurrentNode.pop();
                    }
                }
            }
        }
    }

    /**
     * Exports the current node and everything below it.
     * @param currentNode Where we are in the tree
     * @param pathToCurrentNode Path from root to currentNode
     * @param exportInfo Static information about the export.
     * @throws IOException if something goes wrong.
     */
    private void exportEverything(@NonNull final ExplorerNode currentNode,
                                  @NonNull final Deque<ExplorerNode> pathToCurrentNode,
                                  @NonNull final ExportInfo exportInfo)
    throws IOException {

        // Export everything between root and current node
        exportPathToCurrentNode(pathToCurrentNode, exportInfo);

        // Export this node
        // TODO
        LOGGER.info("{} Export node {}", "-".repeat(pathToCurrentNode.size()), currentNode);

        if (ExplorerConstants.isFolder(currentNode)) {
            // Recurse children and export them too
            final List<ExplorerNode> children = explorerNodeService.getChildren(currentNode.getDocRef());
            for (final ExplorerNode child : children) {
                try {
                    // Recurse
                    pathToCurrentNode.push(child);
                    exportEverything(child, pathToCurrentNode, exportInfo);
                } finally {
                    pathToCurrentNode.pop();
                }
            }
        }
    }

    private void exportPathToCurrentNode(@NonNull final Deque<ExplorerNode> pathToCurrentNode,
                                         @NonNull final ExportInfo exportInfo)
        throws IOException {

        for (final ExplorerNode node : pathToCurrentNode) {
            if (ExplorerConstants.isFolder(node)) {
                if (!exportInfo.alreadyExported(node)) {
                    LOGGER.info("{} Exporting node {} for the first time",
                            ">".repeat(pathToCurrentNode.size()),
                            node);
                    // TODO Write the node to disk as a Folder
                    final Path nodePathOnDisk =
                            foldersToNodeToDiskPath(pathToCurrentNode, exportInfo);
                    // TODO mkdirs on the path
                } else {
                    LOGGER.info("{} Already exported node {}",
                            ">".repeat(pathToCurrentNode.size()),
                            node);
                }
            } else {
                LOGGER.info("{} Export current node: {}",
                        ">".repeat(pathToCurrentNode.size()),
                        pathToCurrentNode);
                // TODO Write the node to disk using action handlers

            }
        }
    }

    /**
     * Creates a path on disk for the given pathToNode
     * @param pathToNode The path that we're creating
     * @param exportInfo Background info about the export.
     * @return A path where things should be put on disk.
     */
    private Path foldersToNodeToDiskPath(@NonNull final Deque<ExplorerNode> pathToNode,
                                         @NonNull final ExportInfo exportInfo) {
        Path path = exportInfo.getDiskDirectory();
        for (final ExplorerNode node : pathToNode) {
            if (ExplorerConstants.isFolder(node)) {
                final String filePrefix = ImportExportFileNameUtil.createFilePrefix(node.getDocRef());
                path = path.resolve(filePrefix);
            }
        }

        return path;
    }

    private void writeNodeToDisk(@NonNull final Path pathToCurrentNode,
                                 @NonNull final ExportInfo exportInfo,
                                 @NonNull final ExplorerNode currentNode)
        throws IOException {

        final ImportExportActionHandler actionHandler = importExportActionHandlers.getHandler(currentNode.getType());
        // TODO Check security
        // TODO Run export
    }

    /**
     * Class to wrap all the static info about an export to save passing
     * lots of parameters down the stack.
     */
    private static class ExportInfo {

        /** Location on disk where we're going to export to */
        private @NonNull final Path diskDirectory;

        /** The document references that we want to export */
        private @NonNull final Set<DocRef> docRefsToExport = new HashSet<>();

        /** Ignore these types */
        private @NonNull final Set<String> typesToIgnore = new HashSet<>();

        /** Whether to omit the audit fields */
        private final boolean omitAuditFields;

        /** Whether the folder has already been created on disk */
        private final Set<ExplorerNode> alreadyExported = new HashSet<>();

        /**
         * Constructor.
         * @param diskDirectory Location on disk where we're going to export to
         * @param docRefsToExport The document references that we want to export
         * @param typesToIgnore Ignore these types
         * @param omitAuditFields Whether to omit the audit fields
         */
        public ExportInfo(@NonNull final Path diskDirectory,
                          @NonNull final Set<DocRef> docRefsToExport,
                          @NonNull final Set<String> typesToIgnore,
                          final boolean omitAuditFields) {
            this.diskDirectory = diskDirectory;
            this.docRefsToExport.addAll(docRefsToExport);
            this.typesToIgnore.addAll(typesToIgnore);
            this.omitAuditFields = omitAuditFields;
        }

        public @NonNull Path getDiskDirectory() {
            return diskDirectory;
        }

        public @NonNull Set<DocRef> getDocRefsToExport() {
            return Collections.unmodifiableSet(docRefsToExport);
        }

        public @NonNull Set<String> getTypesToIgnore() {
            return Collections.unmodifiableSet(typesToIgnore);
        }

        public boolean isOmitAuditFields() {
            return omitAuditFields;
        }

        /**
         * Checks to see if the given node has already been exported.
         * If it has then return true. If not then returns false.
         * @param node The node to check
         * @return false the first time a node is seen, true thereafter
         */
        public boolean alreadyExported(ExplorerNode node) {
            if (!alreadyExported.contains(node)) {
                alreadyExported.add(node);
                return false;
            } else {
                return true;
            }
        }
    }

}
