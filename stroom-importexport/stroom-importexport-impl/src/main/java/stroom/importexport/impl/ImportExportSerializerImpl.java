/*
 * Copyright 2017 Crown Copyright
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
 *
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
import stroom.importexport.api.NonExplorerDocRefProvider;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.NullSafe;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.logging.LogUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.Message;
import stroom.util.shared.PermissionException;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class ImportExportSerializerImpl implements ImportExportSerializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportSerializerImpl.class);

    public static final String FOLDER = ExplorerConstants.FOLDER;

    private final ExplorerService explorerService;
    private final ExplorerNodeService explorerNodeService;
    private final ImportExportActionHandlers importExportActionHandlers;
    private final SecurityContext securityContext;
    private final ImportExportDocumentEventLog importExportDocumentEventLog;
    private static final byte[] LINE_END_CHAR_BYTES = "\n".getBytes(Charset.defaultCharset());

    @Inject
    ImportExportSerializerImpl(final ExplorerService explorerService,
                               final ExplorerNodeService explorerNodeService,
                               final ImportExportActionHandlers importExportActionHandlers,
                               final SecurityContext securityContext,
                               final ImportExportDocumentEventLog importExportDocumentEventLog) {
        this.explorerService = explorerService;
        this.explorerNodeService = explorerNodeService;
        this.importExportActionHandlers = importExportActionHandlers;
        this.securityContext = securityContext;
        this.importExportDocumentEventLog = importExportDocumentEventLog;
    }

    /**
     * IMPORT
     */
    @Override
    public Set<DocRef> read(final Path dir,
                            List<ImportState> importStateList,
                            final ImportSettings importSettings) {
        if (importStateList == null || ImportMode.IGNORE_CONFIRMATION.equals(importSettings.getImportMode())) {
            importStateList = new ArrayList<>();
        }

        // Attempt content migration if possible.
        new ContentMigration().migrate(dir);

        // Key the actionConfirmation's by their key
        final Map<DocRef, ImportState> confirmMap = importStateList.stream()
                .collect(Collectors.toMap(ImportState::getDocRef, Function.identity()));

        // Find all of the paths to import.
        final Set<DocRef> result = processDir(dir, confirmMap, importSettings);

        // Rebuild the list
        importStateList.clear();
        importStateList.addAll(confirmMap.values());

        // Rebuild the tree,
        if (!ImportMode.CREATE_CONFIRMATION.equals(importSettings.getImportMode())) {
            explorerService.rebuildTree();
        }

        //Add the root of the explorer tree
        result.add(ExplorerConstants.SYSTEM_DOC_REF);

        return result;
    }

    private Set<DocRef> processDir(final Path dir,
                                   final Map<DocRef, ImportState> confirmMap,
                                   final ImportSettings importSettings) {
        HashSet<DocRef> result = new HashSet<>();

        try {
            Files.walkFileTree(dir,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            try {
                                final String fileName = file.getFileName().toString();
                                if (fileName.endsWith(".node") && !fileName.startsWith(".")) {
                                    final DocRef imported = performImport(file, confirmMap, importSettings);
                                    if (imported != null) {
                                        result.add(imported);
                                    }
                                }
                            } catch (final RuntimeException e) {
                                LOGGER.error(e.getMessage(), e);
                            }
                            return super.visitFile(file, attrs);
                        }
                    });
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return result;
    }

    private DocRef performImport(final Path nodeFile,
                                 final Map<DocRef, ImportState> confirmMap,
                                 final ImportSettings importSettings) {
        DocRef imported = null;
        try {
            // Read the node file.
            final InputStream inputStream = Files.newInputStream(nodeFile);
            final Properties properties = PropertiesSerialiser.read(inputStream);

            // Get the uuid.
            final String uuid = properties.getProperty("uuid");
            // Get the type.
            final String type = properties.getProperty("type");
            // Get the name.
            final String name = properties.getProperty("name");
            // Get the path.
            final String path = properties.getProperty("path");
            // Get the tags.
            final Set<String> tags = explorerService.parseNodeTags(properties.getProperty("tags"));


            // Create a doc ref.
            final DocRef docRef = new DocRef(type, uuid, name);
            // Create or get the import state.
            final ImportState importState = confirmMap.computeIfAbsent(
                    docRef,
                    k -> new ImportState(docRef, createPath(path, docRef.getName())));

            try {
                // Get other associated data.
                final Map<String, byte[]> dataMap = new HashMap<>();
                final String filePrefix = ImportExportFileNameUtil.createFilePrefix(docRef);
                final Path dir = nodeFile.getParent();
                try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filePrefix + "*")) {
                    stream.forEach(file -> {
                        try {
                            final String fileName = file.getFileName().toString();
                            if (!file.equals(nodeFile) && !fileName.startsWith(".")) {
                                final String key = fileName.substring(filePrefix.length() + 1);
                                final byte[] bytes = Files.readAllBytes(file);
                                dataMap.put(key, bytes);
                            }
                        } catch (final IOException e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    });
                }

                // Find the appropriate handler
                final ImportExportActionHandler importExportActionHandler = importExportActionHandlers.getHandler(type);
                if (importExportActionHandler instanceof NonExplorerDocRefProvider) {
                    imported = importNonExplorerDoc(
                            importExportActionHandler,
                            nodeFile,
                            docRef,
                            path,
                            dataMap,
                            importState,
                            confirmMap,
                            importSettings);

                } else {
                    imported = importExplorerDoc(
                            importExportActionHandler,
                            nodeFile,
                            docRef,
                            tags,
                            path,
                            dataMap,
                            importState,
                            confirmMap,
                            importSettings);
                }
            } catch (final IOException | PermissionException e) {
                LOGGER.error("Error importing file {}", nodeFile.toAbsolutePath(), e);
                importState.addMessage(Severity.ERROR, e.getMessage());
            }
        } catch (final IOException e) {
            LOGGER.error("Error importing file {}", nodeFile.toAbsolutePath(), e);
        }

        return imported;
    }

    private DocRef importNonExplorerDoc(final ImportExportActionHandler importExportActionHandler,
                                        final Path nodeFile,
                                        final DocRef docRef,
                                        final String path,
                                        final Map<String, byte[]> dataMap,
                                        final ImportState importState,
                                        final Map<DocRef, ImportState> confirmMap,
                                        final ImportSettings importSettings) {
        final NonExplorerDocRefProvider nonExplorerDocRefProvider =
                (NonExplorerDocRefProvider) importExportActionHandler;

        final String importPath = resolvePath(path, importSettings);

        final DocRef ownerDocument = nonExplorerDocRefProvider.getOwnerDocument(docRef, dataMap);
        final Optional<ExplorerNode> existingExplorerNode = explorerNodeService.getNode(ownerDocument);
        String destPath = importPath;
        String destName = ownerDocument.getName();
        if (existingExplorerNode.isPresent()) {
            final List<ExplorerNode> parents = explorerNodeService.getPath(ownerDocument);
            if (!importSettings.isUseImportNames()) {
                destName = existingExplorerNode.get().getName();
            }
            if (!importSettings.isUseImportFolders()) {
                destPath = getParentPath(parents);
            }
        }
        final String docRefName = docRef.getType() + " " + docRef.getUuid();
        final String nameSuffix = " - (" + docRefName + ")";
        importState.setSourcePath(createPath(path, ownerDocument.getName()) + nameSuffix);
        importState.setDestPath(createPath(destPath, destName) + nameSuffix);

        try {
            // Import the item via the appropriate handler.
            if (ImportMode.CREATE_CONFIRMATION.equals(importSettings.getImportMode()) ||
                    ImportMode.IGNORE_CONFIRMATION.equals(importSettings.getImportMode()) ||
                    importState.isAction()) {

                final DocRef imported = importExportActionHandler.importDocument(
                        docRef,
                        dataMap,
                        importState,
                        importSettings);

                if (imported == null) {
                    throw new RuntimeException("Import failed - no DocRef returned");
                }

                // Add explorer node afterwards on successful import as they won't be controlled by
                // doc service.
                if (ImportSettings.ok(importSettings, importState)) {
                    importExportDocumentEventLog.importDocument(
                            docRef.getType(),
                            imported.getUuid(),
                            docRef.getName(),
                            null);
                }
            } else {
                // We can't import this item so remove it from the map.
                confirmMap.remove(docRef);
            }
        } catch (final RuntimeException e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
            LOGGER.error("Error importing file {}", nodeFile.toAbsolutePath(), e);
            importExportDocumentEventLog.importDocument(
                    docRef.getType(),
                    docRef.getUuid(),
                    docRef.getName(),
                    e);
            throw e;
        }

        return docRef;
    }

    private DocRef importExplorerDoc(final ImportExportActionHandler importExportActionHandler,
                                     final Path nodeFile,
                                     final DocRef docRef,
                                     final Set<String> tags,
                                     final String path,
                                     final Map<String, byte[]> dataMap,
                                     final ImportState importState,
                                     final Map<DocRef, ImportState> confirmMap,
                                     final ImportSettings importSettings) {
        final String importPath = resolvePath(path, importSettings);

        String destPath = importPath;
        String destName = docRef.getName();

        // See if we have an existing node for this item.
        final Optional<ExplorerNode> existingNode = explorerNodeService.getNode(docRef);
        final boolean docExists = existingNode.isPresent();
        boolean moving = false;

        if (docExists) {
            // This is a pre-existing item so make sure we are allowed to update it.
            if (!securityContext.hasDocumentPermission(docRef,
                    DocumentPermission.EDIT)) {
                throw new PermissionException(securityContext.getUserRef(),
                        "You do not have permission to update '" + docRef + "'");
            }

            importState.setState(State.UPDATE);
            final List<ExplorerNode> parents = explorerNodeService.getPath(docRef);
            final String currentPath = getParentPath(parents);
            if (!importSettings.isUseImportNames()) {
                destName = existingNode.get().getName();
            }
            if (!importSettings.isUseImportFolders()) {
                destPath = currentPath;
            }
            if (!destPath.equals(currentPath)) {
                moving = true;
            }
        } else {
            importState.setState(State.NEW);
        }
        importState.setDestPath(createPath(destPath, destName));

        // If we are creating a new node or moving an existing one then create the destination folders and check
        // permissions.
        DocRef folderRef = null;
        ExplorerNode parentNode = null;
        if (importState.getState() == State.NEW || moving) {
            // Create a parent folder for the new node.
            final ExplorerNode parent = explorerNodeService.getRoot();
            parentNode = getOrCreateParentFolder(parent,
                    importPath,
                    ImportSettings.ok(importSettings, importState));

            // Check permissions on the parent folder.
            folderRef = new DocRef(parentNode.getType(), parentNode.getUuid(), parentNode.getName());
            if (!securityContext.hasDocumentCreatePermission(folderRef, docRef.getType())) {
                throw new PermissionException(securityContext.getUserRef(),
                        "You do not have permission to create '" + docRef + "' in '" + folderRef);
            }
        }

        try {
            // Import the item via the appropriate handler.
            if (importExportActionHandler != null && (
                    ImportMode.CREATE_CONFIRMATION.equals(importSettings.getImportMode()) ||
                            ImportMode.IGNORE_CONFIRMATION.equals(importSettings.getImportMode()) ||
                            importState.isAction())) {

                final DocRef imported = importExportActionHandler.importDocument(
                        docRef,
                        dataMap,
                        importState,
                        importSettings);

                if (imported == null) {
                    throw new RuntimeException("Import failed - no DocRef returned");
                }

                // Add explorer node afterwards on successful import as they won't be controlled by
                // doc service.
                if (ImportSettings.ok(importSettings, importState)) {
                    final ExplorerNode explorerNode = ExplorerNode
                            .builder()
                            .docRef(docRef)
                            .build();

                    // Create, rename and/or move explorer node.
                    if (existingNode.isEmpty()) {
                        explorerNodeService.createNode(
                                imported,
                                folderRef,
                                PermissionInheritance.DESTINATION);
                        explorerService.rebuildTree();
                    } else {
                        if (importSettings.isUseImportNames()) {
                            explorerService.rename(explorerNode, docRef.getName());
                        }
                        if (moving) {
                            explorerService.move(
                                    Collections.singletonList(explorerNode),
                                    parentNode,
                                    PermissionInheritance.DESTINATION);
                        }
                    }

                    importExportDocumentEventLog.importDocument(
                            docRef.getType(),
                            imported.getUuid(),
                            docRef.getName(),
                            null);
                }
            } else {
                // We can't import this item so remove it from the map.
                confirmMap.remove(docRef);
            }
        } catch (final RuntimeException e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
            LOGGER.error("Error importing file {}", nodeFile.toAbsolutePath(), e);
            importExportDocumentEventLog.importDocument(docRef.getType(),
                    docRef.getUuid(),
                    docRef.getName(),
                    e);
            throw e;
        }

        return docRef;
    }

    private String resolvePath(final String path, final ImportSettings importSettings) {
        String result = path;
        if (importSettings.getRootDocRef() != null) {
            final Optional<ExplorerNode> optionalExplorerNode =
                    explorerNodeService.getNode(importSettings.getRootDocRef());
            if (optionalExplorerNode.isPresent()) {
                final ExplorerNode rootNode = optionalExplorerNode.get();
                final List<ExplorerNode> nodes = explorerNodeService.getPath(importSettings.getRootDocRef());
                nodes.add(rootNode);
                // Remove root node.

                final ExplorerNode root = explorerNodeService.getRoot();
                if (nodes.get(0).equals(root)) {
                    nodes.remove(0);
                }
                if (nodes.size() > 0) {
                    final String rootPath = getParentPath(nodes);
                    result = createPath(rootPath, path);
                }
            }
        }
        return result;
    }

    /**
     * EXPORT
     *
     * @return
     */
    @Override
    public ExportSummary write(final Path dir,
                               final Set<DocRef> docRefs,
                               final boolean omitAuditFields) {
        // Create a set of all entities that we are going to try and export.
        final Set<DocRef> expandedDocRefs = expandDocRefSet(docRefs);

        if (expandedDocRefs.size() == 0) {
            throw new EntityServiceException("No documents were found that could be exported");
        }

        final ExportSummary exportSummary = new ExportSummary();
        final List<Message> messageList = new ArrayList<>();
        for (final DocRef docRef : expandedDocRefs) {
            try {
                LOGGER.debug("Exporting {} to {}, omitAuditFields: {}", docRef, dir, omitAuditFields);
                performExport(dir, docRef, omitAuditFields, messageList);
                exportSummary.addSuccess(docRef.getType());
            } catch (final IOException | RuntimeException e) {
                messageList.add(new Message(Severity.ERROR,
                        "Error created while exporting (" + docRef.toString() + ") : "
                                + LogUtil.exceptionMessage(e)));
                exportSummary.addFailure(docRef.getType());
            }
        }
        exportSummary.setMessages(messageList);
        return exportSummary;
    }

    private ExplorerNode getOrCreateParentFolder(ExplorerNode parent,
                                                 final String path,
                                                 final boolean create) {
        // Create a parent folder for the new node.
        final String[] elements = path.split("/");

        for (final String element : elements) {
            if (element.length() > 0) {
                List<ExplorerNode> nodes = explorerNodeService.getNodesByName(parent, element);
                nodes = nodes.stream().filter(n -> FOLDER.equals(n.getType())).collect(Collectors.toList());

                if (nodes.size() == 0) {
                    // No parent node can be found for this element so create one if possible.
                    final DocRef folderRef = new DocRef(parent.getType(), parent.getUuid(), parent.getName());
                    if (!securityContext.hasDocumentCreatePermission(folderRef, FOLDER)) {
                        throw new PermissionException(securityContext.getUserRef(),
                                "You do not have permission to create a folder in '" + folderRef);
                    }

                    // Go and create the folder if we are actually importing now.
                    if (create) {
                        // Go and create the folder.
                        parent = explorerService.create(
                                FOLDER,
                                element,
                                parent,
                                PermissionInheritance.DESTINATION);
                    }

                } else {
                    parent = nodes.get(0);
                }
            }
        }

        return parent;
    }

    private Set<DocRef> expandDocRefSet(final Set<DocRef> set) {
        final Set<DocRef> expandedDocRefs = new HashSet<>();

        if (set == null) {
            // If the set is null then add the whole tree of exportable items.
            addDescendants(null, expandedDocRefs);
        } else {
            for (final DocRef docRef : set) {
                addDocRef(docRef, expandedDocRefs);
                addDescendants(docRef, expandedDocRefs);
            }
        }

        return expandedDocRefs;
    }

    private void addDescendants(final DocRef docRef, Set<DocRef> expandedDocRefs) {
        final List<ExplorerNode> descendants = explorerNodeService.getDescendants(docRef);
        for (final ExplorerNode descendant : descendants) {
            addDocRef(descendant.getDocRef(), expandedDocRefs);
        }
    }

    private void addDocRef(final DocRef docRef, final Set<DocRef> docRefs) {
        try {
            final ImportExportActionHandler importExportActionHandler = importExportActionHandlers.getHandler(
                    docRef.getType());
            if (importExportActionHandler != null) {
                if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                    docRefs.add(docRef);

                    final Set<DocRef> associatedNonExplorerDocRefs =
                            importExportActionHandler.findAssociatedNonExplorerDocRefs(docRef);
                    if (associatedNonExplorerDocRefs != null) {
                        docRefs.addAll(associatedNonExplorerDocRefs);
                    }
                }
            }
        } catch (final RuntimeException e) {
            // We might get a permission exception which is expected for some users.
            LOGGER.debug(e.getMessage(), e);
        }
    }

    private void performExport(final Path dir,
                               final DocRef initialDocRef,
                               final boolean omitAuditFields,
                               final List<Message> messageList) throws IOException {
        final ImportExportActionHandler importExportActionHandler = importExportActionHandlers.getHandler(
                initialDocRef.getType());
        final List<Message> localMessageList = new ArrayList<>();
        if (importExportActionHandler != null) {

            LOGGER.debug("Exporting: " + initialDocRef);
            final Map<String, byte[]> dataMap = importExportActionHandler.exportDocument(initialDocRef,
                    omitAuditFields,
                    localMessageList);
            final DocRef explorerDocRef;
            final ExplorerNode explorerNode;
            final DocRef docRef;
            if (importExportActionHandler instanceof NonExplorerDocRefProvider) {

                // Find the closest DocRef to this one to give a location to export it.
                NonExplorerDocRefProvider docRefProvider = (NonExplorerDocRefProvider) importExportActionHandler;
                explorerDocRef = docRefProvider.findNearestExplorerDocRef(initialDocRef);

                if (explorerDocRef == null) {
                    throw new RuntimeException(
                            "Unable to locate suitable location for export, whilst exporting " +
                                    initialDocRef);
                }

                final String docRefName = docRefProvider.findNameOfDocRef(initialDocRef);
                docRef = new DocRef(initialDocRef.getType(), initialDocRef.getUuid(), docRefName);

                explorerNode = ExplorerNode
                        .builder()
                        .type(docRef.getType())
                        .uuid(docRef.getUuid())
                        .name(docRefName)
                        .build();
            } else {
                docRef = initialDocRef;
                explorerDocRef = docRef;
                explorerNode = explorerNodeService.getNode(explorerDocRef).orElse(null);
            }

            try {
                // Get the explorer path to this doc ref.
                List<ExplorerNode> path = explorerNodeService.getPath(explorerDocRef);
                List<String> pathElements = path.stream()
                        .filter(p -> ExplorerConstants.FOLDER.equals(p.getType()))
                        .map(ExplorerNode::getName).collect(Collectors.toList());

                // Turn the path into a list of strings but ignore any nodes that aren't folders, e.g. the root.

                // Create directories for the path if not already created by another entity.
                final Path parentDir = createDirs(dir, pathElements);

                // Ensure the parent directory exists.
                if (!Files.isDirectory(parentDir)) {
                    // Don't output the full path here are we don't want users to see the full file system path.
                    localMessageList.add(new Message(Severity.FATAL_ERROR,
                            "Unable to create directory for folder: " + parentDir.getFileName()));

                } else {
                    // Write a file for this explorer entry.
                    final String filePrefix = ImportExportFileNameUtil.createFilePrefix(docRef);

                    writeNodeProperties(explorerNode, pathElements, parentDir, filePrefix, localMessageList);

                    // Write out all associated data.
                    dataMap.forEach((k, v) -> {
                        final String fileName = filePrefix + "." + k;
                        try {
                            final OutputStream outputStream = Files.newOutputStream(parentDir.resolve(fileName));
                            outputStream.write(v);
                            // POSIX standard is for all files to end with a line end (\n) so add one if not there
                            if (isMissingLineEndAsLastChar(v)) {
                                outputStream.write(LINE_END_CHAR_BYTES);
                            }
                            outputStream.close();

                        } catch (final IOException e) {
                            localMessageList.add(new Message(
                                    Severity.ERROR,
                                    "Failed to write file '" + fileName + "': "
                                            + LogUtil.exceptionMessage(e)));
                        }
                    });

                    final List<Message> errors = localMessageList.stream()
                            .filter(message -> Severity.FATAL_ERROR.equals(message.getSeverity())
                                    || Severity.ERROR.equals(message.getSeverity()))
                            .collect(Collectors.toList());

                    if (errors.isEmpty()) {
                        importExportDocumentEventLog.exportDocument(docRef, null);
                    } else {
                        final String errorText = errors.stream()
                                .map(error -> error.getSeverity() + ": " + error.getMessage())
                                .collect(Collectors.joining(", "));

                        importExportDocumentEventLog.exportDocument(docRef, new RuntimeException(errorText));
                    }
                }
            } catch (Exception e) {
                importExportDocumentEventLog.exportDocument(docRef, e);
                localMessageList.add(new Message(
                        Severity.ERROR,
                        "Error exporting directory '"
                                + NullSafe.get(dir, Path::toAbsolutePath, Path::normalize) + "': "
                                + LogUtil.exceptionMessage(e)));
            } finally {
                messageList.addAll(localMessageList);
            }
        }
    }

    private boolean isMissingLineEndAsLastChar(final byte[] bytes) {
        if (bytes.length < LINE_END_CHAR_BYTES.length) {
            return false;
        } else {
            byte[] lastChar = Arrays.copyOfRange(bytes, bytes.length - LINE_END_CHAR_BYTES.length, bytes.length);
            return !Arrays.equals(LINE_END_CHAR_BYTES, lastChar);
        }
    }

    private void writeNodeProperties(final ExplorerNode explorerNode,
                                     final List<String> pathElements,
                                     final Path parentDir,
                                     final String filePrefix,
                                     final List<Message> messageList) {
        final String fileName = filePrefix + ".node";

        try {
            final Properties properties = new Properties();
            properties.setProperty("uuid", explorerNode.getUuid());
            properties.setProperty("type", explorerNode.getType());
            properties.setProperty("name", explorerNode.getName());
            properties.setProperty("path", String.join("/", pathElements));
            final String tagsStr = NullSafe.get(explorerNode.getTags(), explorerService::nodeTagsToString);
            if (!NullSafe.isBlankString(tagsStr)) {
                properties.setProperty("tags", tagsStr);
            }

            final OutputStream outputStream = Files.newOutputStream(parentDir.resolve(fileName));
            PropertiesSerialiser.write(properties, outputStream);
        } catch (final Exception e) {
            messageList.add(new Message(
                    Severity.ERROR,
                    "Error writing properties to file '" + fileName + "': " + LogUtil.exceptionMessage(e)));
        }
    }

    private Path createDirs(final Path dir, final List<String> pathElements) throws IOException {
        Path parentDir = dir;
        for (final String pathElement : pathElements) {
            final String safeName = ImportExportFileNameUtil.toSafeFileName(pathElement, 100);
            final Path child = parentDir.resolve(safeName);

            // If this folder hasn't been created yet then output data for the folder and create it.
            if (!Files.isDirectory(child)) {
                Files.createDirectories(child);
            }

            parentDir = child;
        }

        return parentDir;
    }

    private String createPath(final String parent, final String child) {
        if (parent == null || parent.length() == 0) {
            return child;
        }
        return parent + "/" + child;
    }

    private String getParentPath(final List<ExplorerNode> parents) {
        if (parents != null && parents.size() > 0) {
            String parentPath = parents.stream()
                    .map(ExplorerNode::getName)
                    .collect(Collectors.joining("/"));
            int index = parentPath.indexOf("System");
            if (index == 0) {
                parentPath = parentPath.substring(index + "System".length());
            }
            index = parentPath.indexOf("/");
            if (index == 0) {
                parentPath = parentPath.substring(1);
            }
            return parentPath;
        }
        return "";
    }

}
