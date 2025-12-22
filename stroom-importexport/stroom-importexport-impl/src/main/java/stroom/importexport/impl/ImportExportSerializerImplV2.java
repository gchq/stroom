/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.importexport.api.ExportSummary;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.api.ImportExportDocumentEventLog;
import stroom.importexport.api.ImportExportSerializer;
import stroom.importexport.api.ImportExportVersion;
import stroom.importexport.api.NonExplorerDocRefProvider;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Version 2.0 of the ImportExportSerializer. Exports in version 2.0 format.
 * If the import is version 2.0 then imports it directly. Otherwise, hands
 * the import over to the old ImportExportSerializerImpl.
 * <p>
 *     Note that @NullMarked means everything is NonNull unless
 *     explicitly marked @Nullable
 * </p>
 */
@NullMarked
public class ImportExportSerializerImplV2 implements ImportExportSerializer {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(ImportExportSerializerImplV2.class);

    /** Extension of the node file */
    private static final String NODE_EXTENSION = ".node";

    /** Indicates that the file is hidden (on UNIX) */
    private static final String HIDDEN_FILENAME_PREFIX = ".";

    /** Star symbol for globbing in directory streams */
    private static final String GLOB_STAR = "*";

    /** Key for version in .node files */
    private static final String VERSION_KEY = "version";

    /** Key for UUID in .node files */
    private static final String UUID_KEY = "uuid";

    /** Key for Type in .node files */
    private static final String TYPE_KEY = "type";

    /** Key for Name in .node files */
    private static final String NAME_KEY = "name";

    /** Key for Path in .node files */
    private static final String PATH_KEY = "path";

    /** Key for tags in .node files */
    private static final String TAGS_KEY = "tags";

    /** Delimiter for paths in .node files */
    private static final String PATH_DELIMITER = "/";

    /** Name of the .git directory - to be ignored */
    private static final String GIT_DIRECTORY = ".git";

    /** Version 1 implementation */
    private final ImportExportSerializer importExportSerializerV1;

    /** Used to convert node tags to a String */
    private final ExplorerService explorerService;

    /** Used to find the children of a given node */
    private final ExplorerNodeService explorerNodeService;

    /** Provides action handles to allow us to export each node type */
    private final ImportExportActionHandlersImpl importExportActionHandlers;

    /** Provides access to Processor Filters so we can find out whether to export them */
    private final ProcessorFilterService processorFilterService;

    /** Security info */
    private final SecurityContext securityContext;

    /** Logs stuff that was imported */
    private final ImportExportDocumentEventLog importExportDocumentEventLog;

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
                                        final ProcessorFilterService processorFilterService,
                                        final SecurityContext securityContext,
                                        final ImportExportDocumentEventLog importExportDocumentEventLog) {
        this.importExportSerializerV1 = importExportSerializerV1;
        this.explorerService = explorerService;
        this.explorerNodeService = explorerNodeService;
        this.importExportActionHandlers = importExportActionHandlers;
        this.processorFilterService = processorFilterService;
        this.securityContext = securityContext;
        this.importExportDocumentEventLog = importExportDocumentEventLog;
    }

    /**
     * Call to read the serialised format on disk.
     * @param dir             directory containing serialized DocRef items, e.g. files created by
     *                        ImportExportSerializer.write()
     * @param importStateList ?
     * @param importSettings  Settings associated with the import.
     * @return DocRefs of items read from disk.
     */
    @Override
    public Set<DocRef> read(final Path dir,
                            @Nullable final List<ImportState> importStateList,
                            final ImportSettings importSettings) {

        // Check any node file and if not version 2 then hand to version 1
        final Set<DocRef> docRefs;
        try {
            final ImportRoot importRoot = recursiveVersionSearch(dir);

            if (importRoot != null && importRoot.version == ImportExportVersion.V2) {
                Path rootPath = importRoot.rootPath;
                if (rootPath == null) {
                    // Shouldn't happen but maybe we didn't find anything
                    LOGGER.debug("rootPath is null, using {}", dir);
                    rootPath = dir;
                }
                LOGGER.debug("Kicking off V2 read from '{}'", rootPath);
                docRefs = doV2Read(rootPath, importStateList, importSettings);
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
     * and return the version therein. Use the location of the .node file as
     * the root of the import tree.
     * Uses breadth-first search of the directory tree rather than the
     * FileVisitor depth-first search.
     * @param dir The root directory of the file structure to import.
     * @return The version and root of the structure to import.
     */
    private @Nullable ImportRoot recursiveVersionSearch(final Path dir) throws IOException {
        LOGGER.debug("Looking for version in {}", dir);

        ImportRoot importRoot = null;
        try (final DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir, Files::isRegularFile)) {
            for (final Path filePath : dirStream) {
                if (filePath.getFileName().toString().endsWith(NODE_EXTENSION)) {
                    LOGGER.debug("Found node file {}", filePath);
                    importRoot = new ImportRoot(getVersionFromNodeFile(filePath), filePath.getParent());
                    break;
                }
            }
        }

        // Search the next levels if necessary
        if (importRoot == null) {
            try (final DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir, Files::isDirectory)) {
                for (final Path dirPath : dirStream) {
                    if (importRoot != null) {
                        break;
                    }
                    importRoot = this.recursiveVersionSearch(dirPath);
                }
            }
        }

        return importRoot;
    }

    /**
     * Structure to return from recursiveVersionSearch().
     */
    private static class ImportRoot {
        final ImportExportVersion version;
        @Nullable final Path rootPath;

        ImportRoot(final ImportExportVersion version, @Nullable final Path rootPath) {
            this.version = version;
            this.rootPath = rootPath;
        }
    }

    /**
     * Given a node file, opens it and extracts the 'version' key.
     * Then uses that key (if present) to determine the version of the
     * structure on disk.
     * @param nodeFile A file with a .node extension.
     * @return The version of the structure on disk.
     * @throws IOException if something goes wrong.
     */
    private ImportExportVersion getVersionFromNodeFile(final Path nodeFile) throws IOException {
        try (final InputStream inputStream = Files.newInputStream(nodeFile)) {
            final Properties properties = PropertiesSerialiser.read(inputStream);
            final String version = properties.getProperty(VERSION_KEY);
            if (version != null && version.equals(ImportExportVersion.V2.name())) {
                return ImportExportVersion.V2;
            } else {
                return ImportExportVersion.V1;
            }
        }
    }

    /**
     * Recursively searches the import data for items marked up as Actions.
     * The path to them is then marked up as Actions so that the Folders
     * get imported too.
     * @param dir             The directory to search.
     * @param confirmMap      Where the Actions are stored
     * @param docRefPath      Path down to the current item
     * @throws IOException    if something goes wrong.
     */
    private void recursiveMarkupAction(final Path dir,
                                       final Map<DocRef, ImportState> confirmMap,
                                       final Deque<DocRef> docRefPath) throws IOException {

        LOGGER.debug("{}Recursive Markup Action: Looking in {}", indent(docRefPath), dir);

        // Used to store the directory name -> DocRef so we can push the DocRef
        // onto the docRefPath when we recurse the directory name.
        final Map<String, DocRef> pathToFolderDocRef = new HashMap<>();

        // Recurse through all the node files in this folder
        try (final DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir, this::filterNodeFiles)) {
            for (final Path nodeFile : dirStream) {
                LOGGER.debug("{}Found node file in Action search: {}", indent(docRefPath), nodeFile);
                final DocRef docRef = nodeFileToDocRef(nodeFile, null);
                if (ExplorerConstants.isFolder(docRef)) {
                    final String childDirectoryName = nodeFilePathToDirectoryName(nodeFile);
                    pathToFolderDocRef.put(childDirectoryName, docRef);
                }
                final ImportState nodeFileState = confirmMap.get(docRef);
                if (nodeFileState != null && nodeFileState.isAction()) {
                    // This is actioned - mark up path to this item
                    final Deque<DocRef> pathToItem = new ArrayDeque<>();
                    for (final DocRef pathItem : docRefPath) {
                        final ImportState pathItemState =
                                getImportStateFromConfirmMap(confirmMap, pathItem, pathToItem);
                        LOGGER.debug("Generated state from {} / {}", pathToItem, pathItem);
                        if (!pathItemState.isAction()) {
                            LOGGER.debug("{}Marking Folder item as action: '{} / {}'",
                                    indent(docRefPath), pathToItem, pathItem);
                            pathItemState.setAction(true);
                        }
                        pathToItem.addLast(pathItem);
                    }
                }
            }
        }

        // Recurse through all the child directories on disk
        LOGGER.debug("{}Looking for child directories of '{}'", indent(docRefPath), dir);
        try (final DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir, Files::isDirectory)) {
            for (final Path childPath : dirStream) {
                LOGGER.info("{}Recursing into '{}'", indent(docRefPath), childPath);
                if (childPath.endsWith(GIT_DIRECTORY)) {
                    LOGGER.info("{}Ignoring .git directory", indent(docRefPath));
                    continue;
                }
                // Pull the docRef for this directory from the map
                final DocRef parentDocRef = pathToFolderDocRef.get(childPath.getFileName().toString());
                if (parentDocRef == null) {
                    throw new IOException("Node file for folder '" + childPath + "' was not found");
                }
                LOGGER.debug("{}Parent DocRef of {} is {}", indent(docRefPath), childPath.getFileName(), parentDocRef);
                docRefPath.addLast(parentDocRef);
                try {
                    LOGGER.debug("{}Recursing into {}: {}", indent(docRefPath), childPath, docRefPath);
                    this.recursiveMarkupAction(
                            childPath,
                            confirmMap,
                            docRefPath);
                } finally {
                    LOGGER.debug("{}Leaving {}: {}", indent(docRefPath), childPath, docRefPath);
                    docRefPath.removeLast();
                }
            }
        }

    }

    /**
     * Does the import for the version 2 structure.
     * Call to read the serialised format on disk.
     * @param dir             directory containing serialized DocRef items, e.g. files created by
     *                        ImportExportSerializer.write()
     * @param importStateList ?
     * @param importSettings  Settings associated with the import.
     * @return                DocRefs of items read from disk.
     * @throws IOException    if something goes wrong.
     */
    private Set<DocRef> doV2Read(final Path dir,
                                @Nullable List<ImportState> importStateList,
                                final ImportSettings importSettings)
            throws IOException {

        LOGGER.debug("V2 import from '{}'", dir);
        // (Re)Create the importStateList if necessary
        if (importStateList == null || ImportMode.IGNORE_CONFIRMATION.equals(importSettings.getImportMode())) {
            importStateList = new ArrayList<>();
        }

        // Key the actionConfirmation's by their key
        final Map<DocRef, ImportState> confirmMap = importStateList.stream()
                .collect(Collectors.toMap(ImportState::getDocRef, Function.identity()));

        // Stores what we've imported
        final Set<DocRef> importedDocRefs = new HashSet<>();

        // Path down to the parent of the thing we're importing
        final Deque<DocRef> docRefPath = new ArrayDeque<>();
        if (importSettings.getRootDocRef() != null) {
            docRefPath.addLast(importSettings.getRootDocRef());
        } else {
            docRefPath.addLast(ExplorerConstants.SYSTEM_DOC_REF);
        }

        // If we're doing an ACTION_CONFIRMATION then we must make sure that
        // all the Folders in the path to the Actioned items are also Actioned.
        // Otherwise, we might try to import something with no Folder to put it in.
        if (importSettings.getImportMode().equals(ImportMode.ACTION_CONFIRMATION)) {
            recursiveMarkupAction(dir, confirmMap, docRefPath);
        }

        // Recursively read in the structure on disk
        recursiveRead(dir,
                confirmMap,
                importSettings,
                importedDocRefs,
                docRefPath);

        // Rebuild the list
        importStateList.clear();
        importStateList.addAll(confirmMap.values());

        // Rebuild the tree if anything might have changed
        if (!ImportMode.CREATE_CONFIRMATION.equals(importSettings.getImportMode())) {
            explorerService.rebuildTree();
        }

        // Back compatibility with older imports
        importedDocRefs.add(ExplorerConstants.SYSTEM_DOC_REF);

        return importedDocRefs;
    }

    /**
     * Recursively import everything. We make sure we import Folder nodes
     * before we import anything underneath them in the Explorer tree,
     * so we cannot use the standard FileVisitor as it doesn't get the order
     * right.
     * @param dir The directory to import from.
     * @param confirmMap The state of each DocRef imported.
     * @param importSettings The settings for the import.
     * @param importedDocRefs The docrefs we've imported.
     * @param docRefPath The docRef path to the current level.
     */
    private void recursiveRead(final Path dir,
                               final Map<DocRef, ImportState> confirmMap,
                               final ImportSettings importSettings,
                               final Set<DocRef> importedDocRefs,
                               final Deque<DocRef> docRefPath) throws IOException {

        LOGGER.debug("{}==============================", indent(docRefPath));
        LOGGER.debug("{}Looking in {}", indent(docRefPath), dir);

        // Used to store the directory name -> DocRef so we can push the DocRef
        // onto the docRefPath when we recurse the directory name.
        final Map<String, DocRef> pathToFolderDocRef = new HashMap<>();

        // Recurse through all the node files in this folder
        try (final DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir, this::filterNodeFiles)) {
            for (final Path filePath : dirStream) {
                LOGGER.debug("{}Found node file {}", indent(docRefPath), filePath);
                final DocRef docRef = importItemFromDisk(
                        filePath,
                        confirmMap,
                        importSettings,
                        docRefPath);

                if (docRef != null) {
                    // Store the directoryName->DocRef mapping so we can add the
                    // DocRef to the docRefPath when recursing the directories on disk.
                    final String childDirectoryName = nodeFilePathToDirectoryName(filePath);
                    LOGGER.debug("{}childDirectoryName {} -> {}", indent(docRefPath), childDirectoryName, docRef);
                    pathToFolderDocRef.put(childDirectoryName, docRef);
                    importedDocRefs.add(docRef);
                }
            }
        }

        // Recurse through all the child directories on disk
        LOGGER.debug("{}Looking for child directories of '{}'", indent(docRefPath), dir);
        try (final DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir, Files::isDirectory)) {
            for (final Path childPath : dirStream) {
                LOGGER.info("{}Recursing into '{}'", indent(docRefPath), childPath);
                if (childPath.endsWith(GIT_DIRECTORY)) {
                    LOGGER.info("{}Ignoring .git directory", indent(docRefPath));
                    continue;
                }

                // Pull the docRef for this directory from the map
                final DocRef parentDocRef = pathToFolderDocRef.get(childPath.getFileName().toString());
                if (parentDocRef == null) {
                    throw new IOException("Node file for folder '" + childPath + "' was not found");
                }
                LOGGER.debug("{}Parent DocRef of {} is {}", indent(docRefPath), childPath.getFileName(), parentDocRef);
                docRefPath.addLast(parentDocRef);
                try {
                    LOGGER.debug("{}Recursing into {}: {}", indent(docRefPath), childPath, docRefPath);
                    this.recursiveRead(
                            childPath,
                            confirmMap,
                            importSettings,
                            importedDocRefs,
                            docRefPath);
                } finally {
                    LOGGER.debug("{}Leaving {}: {}", indent(docRefPath), childPath, docRefPath);
                    docRefPath.removeLast();
                }
            }
        }

    }

    /**
     * Returns a string that indicates the depth in the recursive structure
     * of the log entry.
     * @param path The path in the structure
     * @return A string to indent the log entry with
     */
    private String indent(final Deque<DocRef> path) {
        return "  ".repeat(path.size());
    }

    /**
     * Lamba functional implementation that accepts .node files and
     * rejects everything else.
     * @param path The path to filter
     * @return true if this is a .node file.
     */
    private boolean filterNodeFiles(final Path path) {
        return path.toFile().isFile() && path.getFileName().toString().endsWith(NODE_EXTENSION);
    }

    /**
     * Works out the name of the directory on disk that corresponds to the
     * given node file path.
     * @param nodeFilePath The path to the node file. Must be a .node file.
     * @return The name of the corresponding directory on disk. May not
     * exist if the node file does not represent some kind of Folder.
     */
    private String nodeFilePathToDirectoryName(final Path nodeFilePath)
            throws IOException {

        final String nodeFileName = nodeFilePath.getFileName().toString();
        if (nodeFileName.endsWith(NODE_EXTENSION)) {
            return nodeFileName.substring(0, nodeFileName.length() - NODE_EXTENSION.length());
        } else {
            throw new IOException("nodeFilePath '"
                                  + nodeFilePath + "' does not end with '"
                                  + NODE_EXTENSION + "'");
        }
    }

    /**
     * Returns the ImportState given a DocRef. Creates the ImportState if necessary.
     * @param confirmMap       Map holding the ImportState
     * @param importDocRef     The docRef we want ImportState for
     * @param importDocRefPath The path to the docRef
     * @return                 The ImportState for the docRef.
     */
    private ImportState getImportStateFromConfirmMap(final Map<DocRef, ImportState> confirmMap,
                                                     final DocRef importDocRef,
                                                     final Deque<DocRef> importDocRefPath) {
        return confirmMap.computeIfAbsent(
                importDocRef,
                k -> new ImportState(importDocRef, resolvePath(importDocRefPath, importDocRef.getName())));
    }

    /**
     * Reads a nodeFile and returns a DocRef.
     * Also optionally returns the tags via an in-out parameter.
     * @param nodeFile     The Path to the nodefile to read.
     * @param tags         Null if not required, or somewhere to store any tags read
     *                     from the nodefile.
     * @return             The DocRef generated from the nodefile.
     * @throws IOException If something goes wrong.
     */
    private DocRef nodeFileToDocRef(final Path nodeFile,
                                    @Nullable final Set<String> tags)
            throws IOException {

        final Properties properties;
        try (final InputStream inputStream = Files.newInputStream(nodeFile)) {
            properties = PropertiesSerialiser.read(inputStream);
        }

        // Get the properties from the node file
        final String uuid = properties.getProperty(UUID_KEY);
        final String type = properties.getProperty(TYPE_KEY);
        final String name = properties.getProperty(NAME_KEY);
        if (tags != null) {
            tags.addAll(explorerService.parseNodeTags(properties.getProperty(TAGS_KEY)));
        }
        final String versionOfNode = properties.getProperty(VERSION_KEY);

        // Check that values were read
        if (uuid == null) {
            throw new IOException("Node file '" + nodeFile + "' does not contain a 'uuid'");
        }
        if (type == null) {
            throw new IOException("Node file '" + nodeFile + "' does not contain a 'type'");
        }
        // name can be null in Processor Filters

        // Check the version is correct - all node files should be the same version
        if (!ImportExportVersion.V2.name().equals(versionOfNode)) {
            throw new IOException("Node file '" + nodeFile
                                  + "' is not version '" + ImportExportVersion.V2.name() + "'");
        }

        return new DocRef(type, uuid, name);
    }

    /**
     * Imports the node file, whatever it represents.
     * @param nodeFile          The Path to the nodeFile to import
     * @param confirmMap        Stuff to tell the user
     * @param importSettings    Stuff the user has told us
     * @param importDocRefPath  Stack of DocRefs above this thing that we're trying
     *                          to import.
     * @return                  Reference of the imported doc.
     */
    private @Nullable DocRef importItemFromDisk(final Path nodeFile,
                                                final Map<DocRef, ImportState> confirmMap,
                                                final ImportSettings importSettings,
                                                final Deque<DocRef> importDocRefPath)
            throws IOException {

        DocRef imported = null;

        // Read the node file.
        final Set<String> tags = new HashSet<>();

        // Create a doc ref for temporary use.
        final DocRef importDocRef = nodeFileToDocRef(nodeFile, tags);
        LOGGER.debug("{}Read node file: {}", indent(importDocRefPath), importDocRef);

        // Create or get the import state.
        final ImportState importState = getImportStateFromConfirmMap(
                confirmMap,
                importDocRef,
                importDocRefPath);

        // Get other associated data.
        final Map<String, byte[]> dataMap = new HashMap<>();
        final String filePrefix = ImportExportFileNameUtil.createFilePrefix(importDocRef);
        final Path dir = nodeFile.getParent();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filePrefix + GLOB_STAR)) {
            for (final Path file : stream) {
                final String fileName = file.getFileName().toString();
                if (!file.toFile().isDirectory()
                    && !file.equals(nodeFile)
                    && !fileName.startsWith(HIDDEN_FILENAME_PREFIX)) {

                    // Find the filename extension as the key
                    if (fileName.length() <= filePrefix.length()) {
                        throw new IOException("Cannot get key from filename '" + fileName + "'");
                    } else {
                        final String key = fileName.substring(filePrefix.length() + 1);
                        LOGGER.debug("{}Found path with key '{}'", indent(importDocRefPath), key);
                        final byte[] bytes = Files.readAllBytes(file);
                        dataMap.put(key, bytes);
                    }
                }
            }
        }

        try {
            // Find the appropriate handler
            final ImportExportActionHandler importExportActionHandler =
                    importExportActionHandlers.getHandler(importDocRef.getType());

            if (importExportActionHandler instanceof NonExplorerDocRefProvider) {
                LOGGER.debug("{}Importing non-explorer doc for node file {}",
                        indent(importDocRefPath),
                        nodeFile);

                imported = importNonExplorerDoc(
                        importExportActionHandler,
                        nodeFile,
                        importDocRef,
                        importDocRefPath,
                        dataMap,
                        importState,
                        confirmMap,
                        importSettings);

            } else {
                LOGGER.debug("{}Importing explorer doc for node file {}", indent(importDocRefPath), nodeFile);
                imported = importExplorerDoc(
                        importExportActionHandler,
                        nodeFile,
                        importDocRefPath,
                        importDocRef,
                        tags,
                        dataMap,
                        importState,
                        confirmMap,
                        importSettings);
            }
        } catch (final PermissionException e) {
            LOGGER.error("Error importing file {}", nodeFile.toAbsolutePath(), e);
            importState.addMessage(Severity.ERROR, e.getMessage());
        }


        return imported;
    }

    private DocRef importNonExplorerDoc(final ImportExportActionHandler importExportActionHandler,
                                        final Path nodeFile,
                                        final DocRef importDocRef,
                                        final Deque<DocRef> importDocRefPath,
                                        final Map<String, byte[]> dataMap,
                                        final ImportState importState,
                                        final Map<DocRef, ImportState> confirmMap,
                                        final ImportSettings importSettings)
            throws IOException {

        final NonExplorerDocRefProvider nonExplorerDocRefProvider =
                (NonExplorerDocRefProvider) importExportActionHandler;

        // Might return null but unlikely - seems it would only be due to code error
        final DocRef ownerDocument = nonExplorerDocRefProvider.getOwnerDocument(importDocRef, dataMap);
        if (ownerDocument == null) {
            throw new IOException("Owner Document for '" + importDocRef.getName() + "' could not be found");
        }

        // Calculate the source and destination paths
        final OwnerDocStateV2 ownerDocState = new OwnerDocStateV2(explorerNodeService,
                ownerDocument,
                importDocRefPath,
                importSettings.isUseImportFolders(),
                importSettings.isUseImportNames());
        importState.setSourcePath(ownerDocState.getSourcePath());
        importState.setDestPath(ownerDocState.getDestPath());

        try {
            // Import the item via the appropriate handler.
            if (ImportMode.CREATE_CONFIRMATION.equals(importSettings.getImportMode()) ||
                ImportMode.IGNORE_CONFIRMATION.equals(importSettings.getImportMode()) ||
                importState.isAction()) {

                // Do the actual import of the ProcessorFilter
                final DocRef importedDocRef = importExportActionHandler.importDocument(
                        importDocRef,
                        dataMap,
                        importState,
                        importSettings);

                if (importedDocRef == null) {
                    throw new IOException("Import failed - no DocRef returned");
                }

                if (ImportSettings.ok(importSettings, importState)) {
                    // Report the import
                    importExportDocumentEventLog.importDocument(
                            importDocRef.getType(),
                            importedDocRef.getUuid(),
                            importDocRef.getName(),
                            null);
                }
            } else {
                // We can't import this item so remove it from the map.
                LOGGER.debug("Import mode is '{}': not importing '{}'", importSettings.getImportMode(), importDocRef);
                confirmMap.remove(importDocRef);
                // importDocRef == null => return value of method
            }
        } catch (final RuntimeException e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
            LOGGER.error("Error importing file {}", nodeFile.toAbsolutePath(), e);
            importExportDocumentEventLog.importDocument(
                    importDocRef.getType(),
                    importDocRef.getUuid(),
                    importDocRef.getName(),
                    e);
            throw e;
        }

        return importDocRef;
    }

    /**
     * Imports something that appears in the Explorer Tree.
     * @param importExportActionHandler Handler for the type of DocRef
     * @param nodeFile Path to the import file on disk
     * @param importDocRefPath The path-like list of DocRefs from root to the parent
     *                         of the thing we're importing.
     * @param importDocRef DocRef created from the .node data on disk
     * @param tags List of tags extracted from .node data on disk
     * @param dataMap Map of disk file extension to disk file contents
     * @param importState State of the import for docRef
     * @param confirmMap Accessed to remove docRef from the map if the docRef
     *                   cannot be imported.
     * @param importSettings Key settings for the import; notably the RootDocRef.
     * @return The DocRef of the imported document, or null if the importMode
     *         means that the item shouldn't be imported.
     */
    private DocRef importExplorerDoc(
            @Nullable final ImportExportActionHandler importExportActionHandler,
            final Path nodeFile,
            final Deque<DocRef> importDocRefPath,
            final DocRef importDocRef,
            final Set<String> tags,
            final Map<String, byte[]> dataMap,
            final ImportState importState,
            final Map<DocRef, ImportState> confirmMap,
            final ImportSettings importSettings)
            throws IOException {

        LOGGER.debug("{}Importing explorer doc with node file '{}'",
                indent(importDocRefPath),
                importDocRef);

        final ImportDocRefStateV2 importDocRefState = new ImportDocRefStateV2(
                explorerNodeService,
                importDocRefPath,
                importDocRef,
                importSettings.isUseImportFolders(),
                importSettings.isUseImportNames());

        if (importDocRefState.nodeAlreadyExists()) {
            LOGGER.debug("{}Document exists", indent(importDocRefPath));

            // This is a pre-existing item so make sure we are allowed to update it.
            if (!securityContext.hasDocumentPermission(importDocRef,
                    DocumentPermission.EDIT)) {
                throw new PermissionException(securityContext.getUserRef(),
                        "You do not have permission to update '" + importDocRef + "'");
            }

            importState.setState(State.UPDATE);

        } else {
            LOGGER.debug("{}Document does not exist", indent(importDocRefPath));
            importState.setState(State.NEW);
        }

        // Record where this was put so the user can be told
        importState.setDestPath(importDocRefState.getDestPathForImportState());

        // The docRef of whatever we managed to import/modify/verify
        DocRef importedDocRef = null;

        try {
            // Import the item via the appropriate handler.
            LOGGER.debug("{}DocRef '{}' has ImportMode: {}",
                    indent(importDocRefPath),
                    importDocRefState.getImportDocRef().getName(),
                    importSettings.getImportMode());

            if (ImportMode.CREATE_CONFIRMATION.equals(importSettings.getImportMode()) ||
                ImportMode.IGNORE_CONFIRMATION.equals(importSettings.getImportMode()) ||
                importState.isAction()) {

                LOGGER.debug("{}Importing '{}'",
                        indent(importDocRefPath),
                        importDocRefState.getImportDocRef().getName());

                // Only do this bit for things that aren't folders
                if (!importDocRefState.isNodeFileExactlyFolderType()) {
                    if (importExportActionHandler == null) {
                        throw new IOException("Cannot import '" + importDocRefState.getImportDocRef().getName()
                                              + "' of type '" + importDocRefState.getImportDocRef().getType()
                                              + "' as its handler is cannot be found");
                    }

                    // One implementation of importExportActionHandler.importDocument()
                    // is in stroom/docstore/impl/StoreImpl.java:
                    // Checks if there is an existing document
                    // Checks if the new document is the same as the old one
                    // Checks for EDIT permission in some cases
                    // Checks ImportSettings.ok() if import mode is not CREATE_CONFIRMATION
                    // Imports by de-serialising the doc from the dataMap,
                    // copying date and time from any existing doc,
                    // then de-serializing again and storing in the DB.
                    // Returned docRef will be the same except for the name, which will
                    // be the same as the name of an existing DocRef, if it exists.
                    // Note that importSettings.isUseImportNames() is ignored here.
                    // This implementation can never return null.
                    // There may be other implementations.
                    importedDocRef = importExportActionHandler.importDocument(
                            importDocRefState.getImportDocRef(),
                            dataMap,
                            importState,
                            importSettings);

                    if (importedDocRef == null) {
                        throw new IOException("Import failed - no DocRef returned");
                    }
                } else {
                    // If it is a Folder then get the initial importedDocRef from importDocRefState
                    importedDocRef = importDocRefState.getImportedFolderDocRef();
                }

                // Add explorer node
                if (ImportSettings.ok(importSettings, importState)) {
                    LOGGER.debug("{}ImportSettings.ok()", indent(importDocRefPath));

                    // Create a non-DB ExplorerNode
                    final ExplorerNode explorerNode = ExplorerNode
                            .builder()
                            .docRef(importedDocRef)
                            .build();

                    // Create, rename and/or move explorer node.
                    if (!importDocRefState.nodeAlreadyExists()) {
                        // Node doesn't exist
                        LOGGER.debug("{}DocRef doesn't exist so creating '{}' within '{}'",
                                indent(importDocRefPath),
                                importedDocRef.getName(),
                                importDocRefState.getImportParentDocRef().getName());
                        explorerNodeService.createNode(
                                importedDocRef,
                                importDocRefState.getImportParentDocRef(),
                                PermissionInheritance.DESTINATION,
                                tags);
                        explorerService.rebuildTree();

                    } else {
                        // Set tags when the node already exists
                        explorerNodeService.updateTags(importedDocRef, tags);

                        // Node already exists
                        LOGGER.debug("{}DocRef '{}' already exists",
                                indent(importDocRefPath),
                                importedDocRef.getName());

                        // Don't rename unless name is incorrect
                        if (importDocRefState.isRenamed()) {
                            LOGGER.debug("{}Renaming '{}' to '{}'",
                                    indent(importDocRefPath),
                                    explorerNode.getName(),
                                    importDocRef.getName());
                            explorerService.rename(explorerNode, importDocRef.getName());
                        }
                        if (importDocRefState.isMoving()) {
                            if (importDocRefState.getDestParentNode() == null) {
                                throw new IOException("Destination node for move is null");
                            }
                            LOGGER.debug("{}Moving to '{}'",
                                    indent(importDocRefPath),
                                    importDocRefState.getDestParentNode().getName());
                            explorerService.move(
                                    Collections.singletonList(explorerNode),
                                    importDocRefState.getDestParentNode(),
                                    PermissionInheritance.DESTINATION);
                        }
                    }

                    // Log the document as imported
                    importExportDocumentEventLog.importDocument(
                            importedDocRef.getType(),
                            importedDocRef.getUuid(),
                            importedDocRef.getName(),
                            null);
                }

            } else {
                // We can't import this item so remove it from the map.
                LOGGER.debug("{}Cannot import item '{}' as import mode is '{}'",
                        indent(importDocRefPath),
                        importDocRef.getName(),
                        importSettings.getImportMode());
                confirmMap.remove(importDocRef);
                // We need to return a DocRef so that the importDocRefPath can be set correctly
                importedDocRef = importDocRef;
            }
        } catch (final Exception e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
            LOGGER.error("Error importing file {}", nodeFile.toAbsolutePath(), e);

            // Log the best docref we've got
            final DocRef docRefToLog =
                    importedDocRef != null ? importedDocRef : importDocRefState.getImportDocRef();

            importExportDocumentEventLog.importDocument(docRefToLog.getType(),
                    docRefToLog.getUuid(),
                    docRefToLog.getName(),
                    e);
            throw e;
        }

        // Return docRef of thing imported.
        return importedDocRef;
    }

    /**
     * Resolves the child against the path down from the parent.
     * @param docRefPath The path to the parent of the object we're looking at
     * @param childName The name of the object we're importing
     * @return The resolved path.
     */
    private String resolvePath(final Deque<DocRef> docRefPath, final String childName) {
        final StringBuilder buf = new StringBuilder();
        for (final DocRef docRef : docRefPath) {
            buf.append(docRef.getName());
        }
        buf.append(childName);

        return buf.toString();
    }

    /**
     * Writes data out in the latest export format.
     * @param rootNodePath     Path to root node of the export. If null then
     *                         starts at the System node.
     *                         Otherwise, removes these path elements from the start of
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
    public ExportSummary write(@Nullable final List<ExplorerNode> rootNodePath,
                               final Path dir,
                               @Nullable final Set<DocRef> docRefs,
                               final Set<String> typesToIgnore,
                               final boolean omitAuditFields) {
        return this.write(rootNodePath,
                dir,
                docRefs,
                typesToIgnore,
                omitAuditFields,
                ImportExportVersion.V2);
    }

    /**
     * Writes data out in version 2 or version 1 structure.
     * @param rootNodePath     Path to root node of the export. If null then
     *                         starts at the System node.
     *                         Otherwise, removes these path elements from the start of
     *                         the exported path. Normally this should be the path to the
     *                         GitRepo node, including that node.
     * @param dir              Where to serialize the DocRef items to on disk.
     * @param docRefs          Set of the DocRefs to serialize.
     * @param typesToIgnore    Set of the Doc types that shouldn't be exported, nor
     *                         their children. Must not be null.
     * @param omitAuditFields  Do not export audit fields.
     * @param version          The version of the export routine to use.
     * @return The summary of the export.
     */
    @Override
    public ExportSummary write(@Nullable final List<ExplorerNode> rootNodePath,
                               final Path dir,
                               @Nullable final Set<DocRef> docRefs,
                               final Set<String> typesToIgnore,
                               final boolean omitAuditFields,
                               final ImportExportVersion version) {
        LOGGER.debug("---- Exporting {} with refs {}", rootNodePath, docRefs);

        if (version != ImportExportVersion.V2) {
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

                final Set<DocRef> surrogateDocRefs = findSurrogateDocRefs(docRefs);
                final Deque<ExplorerNode> pathToCurrentNode = new ArrayDeque<>();
                final ExportInfo exportInfo =
                        new ExportInfo(dir, docRefs, surrogateDocRefs, typesToIgnore, omitAuditFields);

                LOGGER.debug("Searching for nodes to export below {}/{}", pathToCurrentNode, rootNode);
                searchForNodesToExport(exportInfo, pathToCurrentNode, rootNode);

                // Deal with any docRefs that were not exported by the tree.
                // Note that this may include Folders where the UUID didn't match
                // the previous import's random UUID generation.
                final Set<DocRef> unexportedDocRefs = exportInfo.getUnexportedDocRefs();
                LOGGER.debug("Unexported docRefs: {}", unexportedDocRefs);

                return exportInfo.toExportSummary();

            } catch (final IOException e) {
                throw new RuntimeException("Error exporting: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Given the set of docRefs from the client, finds any surrogate docRefs and
     * returns them.
     * @param docRefsFromClient The set of docRefs that were passed in from the caller
     *                          of this service. Can be null.
     * @return                  The surrogate docRefs, to act as placeholders for any
     *                          docRefs that don't appear in the Explorer Tree.
     */
    private Set<DocRef> findSurrogateDocRefs(final @Nullable Set<DocRef> docRefsFromClient)
            throws IOException {

        LOGGER.debug("Finding associated docRefs for {}", docRefsFromClient);
        final Set<DocRef> surrogates = new HashSet<>();
        if (docRefsFromClient != null) {
            for (final DocRef docRef : docRefsFromClient) {
                try {
                    final ImportExportActionHandler handler = importExportActionHandlers.getHandler(docRef.getType());
                    if (handler != null) {
                        if (handler instanceof final NonExplorerDocRefProvider docRefProvider) {
                            final DocRef nearestDocRef = docRefProvider.findNearestExplorerDocRef(docRef);

                            // findNearestExplorerDocRef() returns a DocRef with null for a name.
                            // This means the surrogate won't match the DocRefs found in the
                            // Explorer Tree, so we need to find the full DocRef from the Explorer Tree.

                            if (nearestDocRef != null) {
                                LOGGER.debug("Found nearest docRef: {}", nearestDocRef);

                                final Optional<ExplorerNode> surrogateNode = explorerNodeService.getNode(nearestDocRef);
                                if (surrogateNode.isEmpty()) {
                                    throw new IOException("Could not find an explorer node for the nearest DocRef '"
                                                          + nearestDocRef + "'");
                                } else {
                                    LOGGER.debug("Found node with docRef '{}' for surrogate node",
                                            surrogateNode.get().getDocRef());
                                    surrogates.add(surrogateNode.get().getDocRef());
                                }
                            }
                        }
                    }
                } catch (final PermissionException e) {
                    LOGGER.debug("Expected permission exception finding surrogate docRefs during export: {}",
                            e.getMessage(), e);
                }
            }
        }

        LOGGER.debug("Surrogate DocRefs are: {}", surrogates);
        return surrogates;
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
    private void searchForNodesToExport(final ExportInfo exportInfo,
                                        final Deque<ExplorerNode> pathToCurrentNode,
                                        final ExplorerNode currentNode) throws IOException {

        LOGGER.debug("Searching '{}' for nodes to export", currentNode.getDocRef());

        if (exportInfo.shouldExportDocRef(currentNode.getDocRef())) {
            LOGGER.debug("Exporting everything under '{}'", currentNode.getDocRef());
            exportEverything(exportInfo, pathToCurrentNode, currentNode);
        } else {
            LOGGER.debug("Not exporting '{}'; looking for children to export...", currentNode.getDocRef());

            final List<ExplorerNode> children = explorerNodeService.getChildren(currentNode.getDocRef());
            for (final ExplorerNode child : children) {
                LOGGER.debug("Looking at '{}'", child.getDocRef());
                //if (ExplorerConstants.isFolder(child)) {
                try {
                    // Recurse
                    pathToCurrentNode.addLast(child);
                    LOGGER.debug("Recursing search into '{}'", child.getDocRef());
                    searchForNodesToExport(exportInfo, pathToCurrentNode, child);
                } finally {
                    pathToCurrentNode.removeLast();
                }
                //} else {
                //    LOGGER.debug("'{}' is not a Folder so not recursing", child.getDocRef());
                //}
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
    private void exportEverything(final ExportInfo exportInfo,
                                  final Deque<ExplorerNode> pathToCurrentNode,
                                  final ExplorerNode currentNode)
            throws IOException {

        // Check security permissions
        boolean hasPermission = false;
        try {
            hasPermission = securityContext.hasDocumentPermission(
                    currentNode.getDocRef(),
                    DocumentPermission.VIEW);

        } catch (final RuntimeException e) {
            // May get a permission exception which is expected for some users
            LOGGER.debug("Exception checking document for View permission during export: {}", e.getMessage(), e);
        }

        // Export this node and path to it
        if (hasPermission) {
            exportCurrentNode(exportInfo, pathToCurrentNode);
        }

        // Recurse children and export them too
        final List<ExplorerNode> children = explorerNodeService.getChildren(currentNode.getDocRef());
        for (final ExplorerNode child : children) {
            if (!exportInfo.shouldIgnoreType(child.getType())) {
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
    private void exportCurrentNode(final ExportInfo exportInfo,
                                   final Deque<ExplorerNode> pathToCurrentNode)
            throws IOException {

        // Go through each path set in order, from root to currentNode
        for (int pathIter = 1; pathIter <= pathToCurrentNode.size(); ++pathIter) {

            // Create a list from the root the pathIter'th element
            final List<ExplorerNode> pathToIter = new ArrayList<>(pathToCurrentNode).subList(0, pathIter);
            if (!pathToIter.isEmpty()) {
                final ExplorerNode currentNode = pathToIter.getLast();
                final Path nodeParentPathOnDisk = getNodeParentPathOnDisk(exportInfo, pathToIter);

                if (ExplorerConstants.isFolder(currentNode)) {

                    // Only export this node if we haven't already
                    if (!exportInfo.alreadyExported(currentNode)) {
                        LOGGER.debug("Creating path to '{}'", pathToIter);
                        final Path nodePathOnDisk = foldersToNodeToDiskPath(exportInfo, pathToIter);
                        final File nodeFileOnDisk = nodePathOnDisk.toFile();
                        if (!nodeFileOnDisk.exists()) {
                            if (!nodeFileOnDisk.mkdirs()) {
                                throw new IOException("Could not create directories '"
                                                      + nodeFileOnDisk + "'");
                            } else {
                                LOGGER.debug("Created folder {}", nodePathOnDisk);
                            }

                            try {
                                // Create the associated .node file for the folder
                                writeNodeFile(
                                        pathToIter,
                                        currentNode.getDocRef(),
                                        currentNode.getTags(),
                                        nodeParentPathOnDisk);

                                // Create any other files associated with the Doc e.g. GitRepo stuff
                                writeHandlerFiles(
                                        exportInfo,
                                        currentNode.getDocRef(),
                                        nodeParentPathOnDisk);
                                exportInfo.successfullyExported(currentNode);
                            } catch (final IOException e) {
                                exportInfo.failedToExport(currentNode, e);
                            }
                        }
                    }
                } else {
                    // Not a folder so we need the parent directory
                    // to put files into

                    // Write node file and any other files
                    try {
                        // Don't write surrogates out - their job is to provide
                        // a location for the associated nodes
                        if (!exportInfo.isSurrogate(currentNode.getDocRef())) {
                            writeNodeFile(
                                    pathToIter,
                                    currentNode.getDocRef(),
                                    currentNode.getTags(),
                                    nodeParentPathOnDisk);
                            writeHandlerFiles(
                                    exportInfo,
                                    currentNode.getDocRef(),
                                    nodeParentPathOnDisk);
                        }

                        // Write any non-explorer nodes in the same place as
                        // the node they are associated with
                        writeAssociatedNonExplorerNodes(
                                exportInfo,
                                pathToIter,
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
     * Returns the path on disk to the parent of the node indicated by
     * the pathToIter list.
     * @param exportInfo Information about this export.
     * @param pathToNode Path to the node that we need a parent directory for.
     * @return The path on disk to the parent directory for the node.
     */
    private Path getNodeParentPathOnDisk(final ExportInfo exportInfo,
                                         final List<ExplorerNode> pathToNode) {
        int countToParent = pathToNode.size() - 1;
        if (countToParent < 0) {
            countToParent = 0;
        }
        return foldersToNodeToDiskPath(exportInfo, pathToNode.subList(0, countToParent));
    }

    /**
     * Works out a path on disk for the given pathToNode
     * @param pathToNode The path that we're creating
     * @param exportInfo Background info about the export.
     * @return A path where things should be put on disk.
     */
    private Path foldersToNodeToDiskPath(final ExportInfo exportInfo,
                                         final Collection<ExplorerNode> pathToNode) {
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
     * Writes a node file for the currentDocRef within the parentDirPath.
     * @param pathToCurrentNode Where the currentDocRef is within the
     *                          ExplorerNode structure. Includes the
     *                          currentNode at the end.
     * @param currentDocRef     The docRef we want a node file for.
     * @param tags              The set of tags associated with the node/docRef.
     *                          Can be null if none are present.
     * @param parentDirPath     Where we're going to write the node file
     *                          on disk.
     * @throws IOException      if something goes wrong.
     */
    private void writeNodeFile(final SequencedCollection<ExplorerNode> pathToCurrentNode,
                               final DocRef currentDocRef,
                               @Nullable final Set<String> tags,
                               final Path parentDirPath)
            throws IOException {

        // TODO May get security exceptions which must be ignored?
        if (securityContext.hasDocumentPermission(currentDocRef, DocumentPermission.VIEW)) {
            final Properties nodeProps = new Properties();
            nodeProps.setProperty(UUID_KEY, currentDocRef.getUuid());
            nodeProps.setProperty(TYPE_KEY, currentDocRef.getType());
            if (currentDocRef.getName() != null) {
                // Name can be null for a Processor Filter
                nodeProps.setProperty(NAME_KEY, currentDocRef.getName());
            }
            nodeProps.setProperty(VERSION_KEY, ImportExportVersion.V2.name());

            final String tagStr = NullSafe.get(tags, explorerService::nodeTagsToString);
            if (!NullSafe.isBlankString(tagStr)) {
                nodeProps.setProperty(TAGS_KEY, tagStr);
            }

            // Legacy path field
            final StringBuilder buf = new StringBuilder();
            for (final ExplorerNode node : pathToCurrentNode) {
                buf.append(PATH_DELIMITER);
                buf.append(node.getName());
            }
            nodeProps.setProperty(PATH_KEY, buf.toString());

            // Write the properties to disk
            final String nodeFileName = ImportExportFileNameUtil.createFilePrefix(currentDocRef) + ".node";
            try (final OutputStream nodeStream = Files.newOutputStream(parentDirPath.resolve(nodeFileName))) {
                PropertiesSerialiser.write(nodeProps, nodeStream);
                LOGGER.debug("Wrote node file '{}' with contents '{}'",
                        nodeFileName, nodeProps);
            }
        }
    }

    /**
     * Writes all the files associated with the Handler for the Doc.
     * @param exportInfo    Static info associated with the export
     * @param currentDocRef Current DocRef to export
     * @param parentDirPath Where to export the currentNode
     * @throws IOException  if something goes wrong.
     */
    private void writeHandlerFiles(final ExportInfo exportInfo,
                                   final DocRef currentDocRef,
                                   final Path parentDirPath)
            throws IOException {

        // TODO May get security exceptions which should be ignored?
        if (securityContext.hasDocumentPermission(currentDocRef, DocumentPermission.VIEW)) {
            final ImportExportActionHandler handler = importExportActionHandlers.getHandler(currentDocRef.getType());
            if (handler != null) {

                final List<Message> messages = new ArrayList<>();
                final Map<String, byte[]> dataMap =
                        handler.exportDocument(currentDocRef, exportInfo.isOmitAuditFields(), messages);

                final String filePrefix = ImportExportFileNameUtil.createFilePrefix(currentDocRef);
                for (final Map.Entry<String, byte[]> entry : dataMap.entrySet()) {
                    final String fileName = filePrefix + "." + entry.getKey();
                    try (final OutputStream handlerStream = Files.newOutputStream(parentDirPath.resolve(fileName))) {
                        handlerStream.write(entry.getValue());
                        LOGGER.debug("Wrote file '{}/{}'", parentDirPath, fileName);
                    }
                }
            }
        }
    }

    /**
     * Writes out any DocRefs associated with an ExplorerNode.
     * @param exportInfo        Info about this export.
     * @param pathToCurrentNode Node path to this node.
     * @param currentNode       The node we are exporting and want associated docRefs for
     * @param parentDirPath     Where we're going to write stuff on disk
     * @throws IOException      if something goes wrong.
     */
    private void writeAssociatedNonExplorerNodes(final ExportInfo exportInfo,
                                                 final SequencedCollection<ExplorerNode> pathToCurrentNode,
                                                 final ExplorerNode currentNode,
                                                 final Path parentDirPath)
            throws IOException {
        LOGGER.debug("Checking for associated docRefs");
        final ImportExportActionHandler handler = importExportActionHandlers.getHandler(currentNode.getType());
        if (handler != null) {
            final Set<DocRef> associatedNonExplorerDocRefs =
                    handler.findAssociatedNonExplorerDocRefs(currentNode.getDocRef());
            if (associatedNonExplorerDocRefs != null) {
                LOGGER.debug("Writing associated docRefs");
                for (final DocRef docRef : associatedNonExplorerDocRefs) {

                    // What kind of thing have we got?
                    if (docRef.getType().equals(ProcessorFilter.ENTITY_TYPE)) {
                        final Optional<ProcessorFilter> optProcessorFilter =
                                processorFilterService.fetchByUuid(docRef.getUuid());
                        if (optProcessorFilter.isEmpty()) {
                            throw new IOException("No Processor Filter found for document reference '"
                                                  + docRef + "'");
                        } else {
                            if (optProcessorFilter.get().isExport()) {
                                LOGGER.debug("Writing associated docRef '{}'", docRef);
                                writeNodeFile(
                                        pathToCurrentNode,
                                        docRef,
                                        null,
                                        parentDirPath);
                                writeHandlerFiles(
                                        exportInfo,
                                        docRef,
                                        parentDirPath);
                            } else {
                                LOGGER.info("DocRef '{}' is not marked for export", docRef);
                            }
                        }
                    } else {
                        LOGGER.debug("Writing associated docRef '{}'", docRef);
                        writeNodeFile(
                                pathToCurrentNode,
                                docRef,
                                null,
                                parentDirPath);
                        writeHandlerFiles(
                                exportInfo,
                                docRef,
                                parentDirPath);
                    }
                }
            }
        }
    }

    /**
     * Class to wrap all the static & state info about an export to avoid
     * passing lots of parameters down the stack.
     */
    @NullMarked
    private static class ExportInfo {

        /** Location on disk where we're going to export to */
        private final Path diskDirectory;

        /** The document references that we want to export */
        private @Nullable final Set<DocRef> docRefsToExport;

        /** Ignore these types */
        private final Set<String> typesToIgnore = new HashSet<>();

        /** Whether to omit the audit fields */
        private final boolean omitAuditFields;

        /** Whether the folder has already been created on disk */
        private final Set<ExplorerNode> alreadyExported = new HashSet<>();

        /** Used to place non-explorer docRefs in the explorer tree */
        private final Set<DocRef> surrogateDocRefs = new HashSet<>();

        /** Messages to return to the user */
        private final List<Message> messages = new ArrayList<>();

        /** Compatibility with ExportSummary */
        private final List<String> successTypes = new ArrayList<>();

        /** Compatibility with ExportSummary */
        private final List<String> failedTypes = new ArrayList<>();

        /**
         * Constructor.
         *
         * @param diskDirectory    Location on disk where we're going to export to.
         * @param docRefsToExport  The document references that we want to export.
         *                         Defensively copied. If null then everything
         *                         gets exported.
         * @param surrogateDocRefs DocRefs that act as a placeholder for docRefs that
         *                         aren't in the Explorer Tree.
         * @param typesToIgnore    Ignore these types. Defensively copied.
         * @param omitAuditFields  Whether to omit the audit fields.
         */
        public ExportInfo(final Path diskDirectory,
                          @Nullable final Set<DocRef> docRefsToExport,
                          final Set<DocRef> surrogateDocRefs,
                          final Set<String> typesToIgnore,
                          final boolean omitAuditFields) {
            this.diskDirectory = diskDirectory;
            if (docRefsToExport == null) {
                this.docRefsToExport = null;
            } else {
                this.docRefsToExport = new HashSet<>();
                this.docRefsToExport.addAll(docRefsToExport);
                //this.docRefsToExport.addAll(surrogateDocRefs);
            }
            this.typesToIgnore.addAll(typesToIgnore);
            this.omitAuditFields = omitAuditFields;
            this.surrogateDocRefs.addAll(surrogateDocRefs);
        }

        /**
         * @return The disk directory where we're going to export stuff to.
         */
        public Path getDiskDirectory() {
            return diskDirectory;
        }

        /**
         * Returns true if we should export the given docRef.
         * @param docRef The docRef to check.
         * @return true if we should export, false if not.
         */
        public boolean shouldExportDocRef(final DocRef docRef) {
            LOGGER.debug("Checking if docRef '{}' is in '{}' or '{}'",
                    docRef, docRefsToExport, surrogateDocRefs);
            return docRefsToExport == null
                   || docRefsToExport.contains(docRef)
                   || surrogateDocRefs.contains(docRef);
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
         * @return The set of docRefs that were specified by the client
         * but haven't been exported.
         */
        public Set<DocRef> getUnexportedDocRefs() {
            final Set<DocRef> unexportedDocRefs;
            if (this.docRefsToExport != null) {
                unexportedDocRefs = new HashSet<>(this.docRefsToExport);
            } else {
                unexportedDocRefs = new HashSet<>();
            }

            final List<DocRef> exportedDocRefs = this.alreadyExported.stream().map(ExplorerNode::getDocRef).toList();
            exportedDocRefs.forEach(unexportedDocRefs::remove);

            return unexportedDocRefs;
        }

        /**
         * Checks to see if the given node has already been exported.
         * If it has then return true. If not then returns false.
         * Must be used with successfullyExported() to mark the node as
         * exported.
         * @param node The node to check
         * @return false the first time a node is seen, true thereafter
         */
        public boolean alreadyExported(final ExplorerNode node) {
            return alreadyExported.contains(node);
        }

        /**
         * Call for each doc that is successfully exported. Used when
         * creating the ExportSummary.
         * @param node The node that was successfully exported.
         */
        public void successfullyExported(final ExplorerNode node) {
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
        public void failedToExport(final ExplorerNode node,
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

        /**
         * Returns true if the docRef is a surrogate. That is, it exists
         * as a placeholder in the ExplorerTree for things that are not
         * in the ExplorerTree such as Processor Filters.
         * @param docRef The docRef to check
         * @return true if the docRef is a surrogate, false if not.
         */
        public boolean isSurrogate(final DocRef docRef) {
            return surrogateDocRefs.contains(docRef)
                   && ! (docRefsToExport == null || docRefsToExport.contains(docRef));
        }

    }

}
