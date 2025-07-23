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
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /** Key for UUID in .node files */
    private final static String UUID_KEY = "uuid";

    /** Key for Type in .node files */
    private final static String TYPE_KEY = "type";

    /** Key for Name in .node files */
    private final static String NAME_KEY = "name";

    /** Key for Path in .node files */
    private final static String PATH_KEY = "path";

    /** Key for tags in .node files */
    private final static String TAGS_KEY = "tags";

    /** Delimiter for paths in .node files */
    private final static String NODE_PATH_DELIMITER = "/";

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
    @SuppressWarnings("unused")
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

        if (false) {
            return this.importExportSerializerV1.write(
                    rootNodePath,
                    dir,
                    docRefs,
                    typesToIgnore,
                    omitAuditFields);
        } else {
            try {
                final ExplorerNode rootNode;
                if (rootNodePath == null || rootNodePath.isEmpty()) {
                    rootNode = ExplorerConstants.SYSTEM_NODE;
                } else {
                    rootNode = rootNodePath.getLast();
                }

                final Deque<ExplorerNode> pathToCurrentNode = new ArrayDeque<>();
                pathToCurrentNode.add(rootNode);

                final ExportInfo exportInfo =
                        new ExportInfo(dir, docRefs, typesToIgnore, omitAuditFields);
                searchForNodesToExport(exportInfo, pathToCurrentNode, rootNode);

                return exportInfo.toExportSummary();

            } catch (final IOException e) {
                throw new RuntimeException("Error exporting: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Recursively called to search the Explorer Tree. Looks for
     * nodes in the docRefs parameter passed to the write() method.
     * @param exportInfo Static information about this export.
     * @param pathToCurrentNode How we got here, from the rootNode down
     *                          to the currentNode.
     * @param currentNode The node we're looking at in this method call.
     * @throws IOException if something goes wrong.
     */
    private void searchForNodesToExport(@NonNull final ExportInfo exportInfo,
                                        @NonNull final Deque<ExplorerNode> pathToCurrentNode,
                                        @NonNull final ExplorerNode currentNode) throws IOException {

        final List<ExplorerNode> children = explorerNodeService.getChildren(currentNode.getDocRef());
        for (final ExplorerNode child : children) {
            if (exportInfo.shouldExportDocRef(child.getDocRef())) {
                LOGGER.info("{} Node '{}' must be exported!", "=".repeat(pathToCurrentNode.size()), child.getName());
                try {
                    pathToCurrentNode.addLast(child);
                    exportEverything(exportInfo, pathToCurrentNode, child);
                } finally {
                    pathToCurrentNode.removeLast();
                }
            } else {
                if (ExplorerConstants.isFolder(child)) {
                    try {
                        // Recurse
                        pathToCurrentNode.addLast(child);
                        searchForNodesToExport(exportInfo, pathToCurrentNode, child);
                    } finally {
                        pathToCurrentNode.removeLast();
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
    private void exportEverything(@NonNull final ExportInfo exportInfo,
                                  @NonNull final Deque<ExplorerNode> pathToCurrentNode,
                                  @NonNull final ExplorerNode currentNode)
    throws IOException {

        if (!exportInfo.shouldIgnoreType(currentNode.getType())) {
            // Export this node and path to it
            exportCurrentNode(exportInfo, pathToCurrentNode);

            // Recurse children and export them too
            final List<ExplorerNode> children = explorerNodeService.getChildren(currentNode.getDocRef());
            for (final ExplorerNode child : children) {
                try {
                    // Recurse
                    pathToCurrentNode.addLast(child);
                    exportEverything(exportInfo, pathToCurrentNode, child);
                } finally {
                    pathToCurrentNode.removeLast();
                }
            }
        }
    }

    /**
     * Creates all the folders down to the current node.
     * Side effect is to create the directories on disk and the associated .node files.
     * @param pathToCurrentNode The list of nodes, from the root of the export
     *                          down to the current node.
     * @param exportInfo Static info about the export.
     * @throws IOException if something goes wrong.
     */
    private void exportCurrentNode(@NonNull final ExportInfo exportInfo,
                                   @NonNull final Deque<ExplorerNode> pathToCurrentNode)
        throws IOException {

        // Go through each path set in order, from root to currentNode
        for (int pathIter = 1; pathIter <= pathToCurrentNode.size(); ++pathIter) {

            // Create a list from the root the pathIter'th element
            final List<ExplorerNode> pathToIter = new ArrayList<>(pathToCurrentNode).subList(0, pathIter);
            if (!pathToIter.isEmpty()) {
                final ExplorerNode currentNode = pathToIter.getLast();

                if (ExplorerConstants.isFolder(currentNode)) {
                    // Only export this node if we haven't already
                    if (!exportInfo.alreadyExported(currentNode)) {
                        LOGGER.info("Creating path to '{}'", pathToIter);
                        final Path nodePathOnDisk = foldersToNodeToDiskPath(exportInfo, pathToIter);
                        final File nodeFileOnDisk = nodePathOnDisk.toFile();
                        if (!nodeFileOnDisk.exists()) {
                            if (!nodeFileOnDisk.mkdirs()) {
                                throw new IOException("Could not create directories '"
                                                      + nodeFileOnDisk + "'");
                            } else {
                                LOGGER.info("Created folder {}", nodePathOnDisk);
                            }

                            try {
                                // Create the associated .node file for the folder
                                writeNodeFile(
                                        pathToCurrentNode,
                                        currentNode,
                                        nodePathOnDisk);

                                // Create any other files associated with the Doc e.g. GitRepo stuff
                                writeHandlerFiles(
                                        exportInfo,
                                        currentNode,
                                        nodePathOnDisk);
                                exportInfo.successfullyExported(currentNode);
                            } catch (final IOException e) {
                                exportInfo.failedToExport(currentNode, e);
                            }
                        }
                    }
                } else {
                    // Not a folder so we need the parent directory
                    final Path nodeParentPathOnDisk =
                            foldersToNodeToDiskPath(exportInfo, pathToIter.subList(0, pathIter - 1));

                    // Write node file and any other files
                    try {
                        writeNodeFile(
                                pathToCurrentNode,
                                currentNode,
                                nodeParentPathOnDisk);
                        writeHandlerFiles(
                                exportInfo,
                                currentNode,
                                nodeParentPathOnDisk);
                        exportInfo.successfullyExported(currentNode);
                    } catch (final IOException e) {
                        exportInfo.failedToExport(currentNode, e);
                    }

                }
            }
        }
    }

    /**
     * Works out a path on disk for the given pathToNode
     * @param pathToNode The path that we're creating
     * @param exportInfo Background info about the export.
     * @return A path where things should be put on disk.
     */
    private Path foldersToNodeToDiskPath(@NonNull final ExportInfo exportInfo,
                                         @NonNull final Collection<ExplorerNode> pathToNode) {
        Path path = exportInfo.getDiskDirectory();
        for (final ExplorerNode node : pathToNode) {
            if (ExplorerConstants.isFolder(node)) {
                final String filePrefix = ImportExportFileNameUtil.createFilePrefix(node.getDocRef());
                path = path.resolve(filePrefix);
            }
        }

        return path;
    }

    /**
     * Writes a node file for the currentNode within the parentDirPath.
     * @param pathToCurrentNode Where the currentNode is within the
     *                          ExplorerNode structure. Includes the
     *                          currentNode at the end.
     * @param currentNode       The node we want a node file for.
     * @param parentDirPath     Where we're going to write the node file
     *                          on disk.
     * @throws IOException      if something goes wrong.
     */
    private void writeNodeFile(@NonNull final Deque<ExplorerNode> pathToCurrentNode,
                               @NonNull final ExplorerNode currentNode,
                               @NonNull final Path parentDirPath)
        throws IOException {

        // TODO Check security

        final Properties nodeProps = new Properties();
        nodeProps.setProperty(UUID_KEY, currentNode.getUuid());
        nodeProps.setProperty(TYPE_KEY, currentNode.getType());
        nodeProps.setProperty(NAME_KEY, currentNode.getName());
        nodeProps.setProperty(VERSION_KEY, Version.V2.name());

        final String tagStr = NullSafe.get(currentNode.getTags(), explorerService::nodeTagsToString);
        if (!NullSafe.isBlankString(tagStr)) {
            nodeProps.setProperty(TAGS_KEY, tagStr);
        }

        // Legacy path field
        final StringBuilder buf = new StringBuilder();
        for (final ExplorerNode node : pathToCurrentNode) {
            buf.append(NODE_PATH_DELIMITER);
            buf.append(node.getName());
        }
        nodeProps.setProperty(PATH_KEY, buf.toString());

        // Write the properties to disk
        final String nodeFileName = ImportExportFileNameUtil.createFilePrefix(currentNode.getDocRef()) + ".node";
        try (final OutputStream nodeStream = Files.newOutputStream(parentDirPath.resolve(nodeFileName))) {
            PropertiesSerialiser.write(nodeProps, nodeStream);
            LOGGER.info("Wrote node file '{}' with contents '{}'",
                    nodeFileName, nodeProps);
        }
    }

    /**
     * Writes all the files associated with the Handler for the Doc.
     * @param exportInfo        Static info associated with the export
     * @param currentNode       Current node to export
     * @param parentDirPath     Where to export the currentNode
     * @throws IOException      if something goes wrong.
     */
    private void writeHandlerFiles(@NonNull final ExportInfo exportInfo,
                                   @NonNull final ExplorerNode currentNode,
                                   @NonNull final Path parentDirPath)
        throws IOException {

        // TODO Check security

        final ImportExportActionHandler handler = importExportActionHandlers.getHandler(currentNode.getType());
        if (handler != null) {
            final List<Message> messages = new ArrayList<>();
            final Map<String, byte[]> dataMap =
                    handler.exportDocument(currentNode.getDocRef(), exportInfo.isOmitAuditFields(), messages);

            final String filePrefix = ImportExportFileNameUtil.createFilePrefix(currentNode.getDocRef());
            for (final Map.Entry<String, byte[]> entry : dataMap.entrySet()) {
                final String fileName = filePrefix + "." + entry.getKey();
                try (final OutputStream handlerStream = Files.newOutputStream(parentDirPath.resolve(fileName))) {
                    handlerStream.write(entry.getValue());
                    LOGGER.info("Wrote file '{}/{}'", parentDirPath, fileName);
                }
            }
        }
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

        /** Messages to return to the user */
        private final List<Message> messages = new ArrayList<>();

        /** Compatibility with ExportSummary */
        private final List<String> successTypes = new ArrayList<>();

        /** Compatibility with ExportSummary */
        private final List<String> failedTypes = new ArrayList<>();

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

        /**
         * @return The disk directory where we're going to export stuff to.
         */
        public @NonNull Path getDiskDirectory() {
            return diskDirectory;
        }

        /**
         * Returns true if we should export the given docRef.
         * @param docRef The docRef to check.
         * @return true if we should export, false if not.
         */
        public boolean shouldExportDocRef(@NonNull final DocRef docRef) {
            return docRefsToExport.contains(docRef);
        }

        /**
         * Returns true if we should ignore the given type and
         * everything below it.
         * @param type The type to check.
         * @return true if we should ignore the type, false if we shouldn't.
         */
        public boolean shouldIgnoreType(final String type) {
            return typesToIgnore.contains(type);
        }

        /**
         * @return true if we omit audit fields, false if they should be exported.
         */
        public boolean isOmitAuditFields() {
            return omitAuditFields;
        }

        /**
         * Checks to see if the given node has already been exported.
         * If it has then return true. If not then returns false.
         * Must be used with successfullyExported() to mark the node as
         * exported.
         * @param node The node to check
         * @return false the first time a node is seen, true thereafter
         */
        public boolean alreadyExported(@NonNull final ExplorerNode node) {
            return alreadyExported.contains(node);
        }

        /**
         * Call for each doc that is successfully exported. Used when
         * creating the ExportSummary.
         * @param node The node that was successfully exported.
         */
        public void successfullyExported(@NonNull final ExplorerNode node) {
            messages.add(new Message(Severity.INFO, "Exported '" + node.getName() + "'"));
            successTypes.add(node.getType());
            alreadyExported.add(node);
        }

        /**
         * Call for each doc that couldn't be exported. Used when
         * creating the ExportSummary.
         * @param node The node that failed to be exported.
         * @param e The exception, if any. Can be null.
         */
        public void failedToExport(@NonNull final ExplorerNode node,
                                   @Nullable final Throwable e) {
            if (e != null) {
                messages.add(new Message(Severity.ERROR,
                        "Failed to export '" + node.getName() + "': " + e.getMessage()));
            } else {
                messages.add(new Message(Severity.ERROR,
                        "Failed to export '" + node.getName() + "'"));
            }

            failedTypes.add(node.getType());
        }

        /**
         * Creates an ExportSummary to hand back for display to the user.
         * @return A new ExportSummary to return to the user.
         */
        public ExportSummary toExportSummary() {
            final ExportSummary summary = new ExportSummary();
            for (final String type : successTypes) {
                summary.addSuccess(type);
            }
            for (final String type : failedTypes) {
                summary.addFailure(type);
            }

            summary.setMessages(Collections.unmodifiableList(messages));
            return summary;
        }
    }

}
