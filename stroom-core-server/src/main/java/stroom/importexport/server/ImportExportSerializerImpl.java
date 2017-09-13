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

package stroom.importexport.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.ImportState;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.entity.shared.ImportState.State;
import stroom.entity.shared.PermissionException;
import stroom.entity.shared.PermissionInheritance;
import stroom.explorer.server.ExplorerNodeService;
import stroom.explorer.server.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.query.api.v1.DocRef;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
class ImportExportSerializerImpl implements ImportExportSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportSerializerImpl.class);

    private static final Charset CHARSET = StreamUtil.DEFAULT_CHARSET;
    public static final String FOLDER = ExplorerConstants.FOLDER;

    private final ExplorerService explorerService;
    private final ExplorerNodeService explorerNodeService;
    private final ImportExportActionHandlersImpl importExportActionHandlers;
    private final SecurityContext securityContext;
    private final ImportExportEventLog importExportEventLog;

    @Inject
    ImportExportSerializerImpl(final ExplorerService explorerService,
                               final ExplorerNodeService explorerNodeService,
                               final ImportExportActionHandlersImpl importExportActionHandlers,
                               final SecurityContext securityContext,
                               final ImportExportEventLog importExportEventLog) {
        this.explorerService = explorerService;
        this.explorerNodeService = explorerNodeService;
        this.importExportActionHandlers = importExportActionHandlers;
        this.securityContext = securityContext;
        this.importExportEventLog = importExportEventLog;
    }

    /**
     * IMPORT
     */
    @SuppressWarnings("unchecked")
    @Override
    public void read(final Path dir, List<ImportState> importStateList,
                     final ImportMode importMode) {
        if (ImportMode.IGNORE_CONFIRMATION.equals(importMode)) {
            importStateList = new ArrayList<>();
        }

        // Attempt content migration if possible.
        if (!ImportMode.ACTION_CONFIRMATION.equals(importMode)) {
            new ContentMigration().migrate(dir);
        }

        // Key the actionConfirmation's by their key
        final Map<DocRef, ImportState> confirmMap = importStateList.stream().collect(Collectors.toMap(ImportState::getDocRef, Function.identity()));

        // Find all of the paths to import.
        try (final Stream<Path> stream = Files.walk(dir)) {
            stream
                    .filter(path -> path.getFileName().toString().endsWith(".node"))
                    .forEach(path -> performImport(path, confirmMap, importMode));
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Rebuild the list
        importStateList.clear();
        importStateList.addAll(confirmMap.values());

        // Rebuild the tree,
        if (!ImportMode.CREATE_CONFIRMATION.equals(importMode)) {
            explorerService.rebuildTree();
        }
    }

    private void performImport(final Path nodeFile, final Map<DocRef, ImportState> confirmMap,
                               final ImportMode importMode) {
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
            final String tags = properties.getProperty("tags");

            // Create a doc ref.
            final DocRef docRef = new DocRef(type, uuid, name);
            // Create or get the import state.
            final ImportState importState = confirmMap.computeIfAbsent(docRef, k -> new ImportState(docRef, createPath(path, docRef.getName())));

            try {
                // Get other associated data.
                final Map<String, String> dataMap = new HashMap<>();
                final String filePrefix = ImportExportFileNameUtil.createFilePrefix(docRef);
                final Path dir = nodeFile.getParent();
                try (final Stream<Path> stream = Files.list(dir)) {
                    stream
                            .filter(p -> p.getFileName().toString().startsWith(filePrefix) && !p.equals(nodeFile))
                            .forEach(p -> {
                                try {
                                    final String key = p.getFileName().toString().substring(filePrefix.length() + 1);
                                    final byte[] bytes = Files.readAllBytes(p);
                                    final String string = new String(bytes, CHARSET);
                                    dataMap.put(key, string);
                                } catch (final IOException e) {
                                    LOGGER.error(e.getMessage(), e);
                                }
                            });
                }

                // See if we have an existing node for this item.
                final ExplorerNode existingNode = explorerNodeService.getNode(docRef);
                ExplorerNode parentNode = null;

                if (existingNode == null) {
                    importState.setState(State.NEW);
                    importState.setDestPath(createPath(path, docRef.getName()));

                    // Create a parent folder for the new node.
                    // Get the root node.
                    // TODO : Allow the user to specify what the parent folder should be for the import.
                    ExplorerNode parent = explorerNodeService.getRoot();
                    parentNode = getOrCreateParentFolder(parent, path, importState.ok(importMode));

                } else {
                    importState.setState(State.UPDATE);
                    importState.setDestPath(createPath(path, docRef.getName()));

                    // This is a pre existing item so make sure we are allowed to update it.
                    if (!securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.UPDATE)) {
                        throw new PermissionException("You do not have permission to update '" + docRef + "'");
                    }
                    if (!securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.IMPORT)) {
                        throw new PermissionException("You do not have permission to import '" + docRef + "'");
                    }

                    final List<ExplorerNode> parents = explorerNodeService.getPath(docRef);
                    if (parents.size() > 0) {
                        parentNode = parents.get(parents.size() - 1);
                    }
                }

                if (parentNode == null) {
                    throw new RuntimeException("Unable to locate a parent folder to import '" + docRef + "'");
                }

                // Check permissions on the parent folder.
                final DocRef folderRef = new DocRef(parentNode.getType(), parentNode.getUuid(), parentNode.getName());
                if (!securityContext.hasDocumentPermission(folderRef.getType(), folderRef.getUuid(), DocumentPermissionNames.getDocumentCreatePermission(type))) {
                    throw new PermissionException("You do not have permission to create '" + docRef + "' in '" + folderRef);
                }
                if (!securityContext.hasDocumentPermission(folderRef.getType(), folderRef.getUuid(), DocumentPermissionNames.IMPORT)) {
                    throw new PermissionException("You do not have permission to import '" + docRef + "' into '" + folderRef);
                }

                try {
                    // Import the item via the appropriate handler.
                    final ImportExportActionHandler importExportActionHandler = importExportActionHandlers.getHandler(type);
                    if (importExportActionHandler != null) {
                        final DocRef imported = importExportActionHandler.importDocument(docRef, dataMap, importState, importMode);

                        // Add explorer node afterwards on successful import as they won't be controlled by doc service.
                        if (existingNode == null && !ImportMode.CREATE_CONFIRMATION.equals(importMode)) {
                            explorerNodeService.createNode(imported, folderRef, PermissionInheritance.INHERIT);
                            importExportEventLog.importDocument(type, imported.getUuid(), name, null);
                        }
                    } else {
                        LOGGER.error("No import handler can be found for type '" + type + "'");
                    }

                } catch (final RuntimeException e) {
                    importExportEventLog.importDocument(docRef.getType(), docRef.getUuid(), docRef.getName(), e);
                    throw e;
                }

            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
                if (importState != null) {
                    importState.addMessage(Severity.ERROR, e.getMessage());
                }
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * EXPORT
     */
    @Override
    public void write(final Path dir, final DocRefs docRefs, final boolean omitAuditFields,
                      final List<Message> messageList) {
        // Create a set of all entities that we are going to try and export.
        DocRefs expandedDocRefs = expandDocRefSet(docRefs);

        for (final DocRef docRef : expandedDocRefs) {
            try {
                performExport(dir, docRef, omitAuditFields, messageList);
            } catch (final Exception e) {
                messageList.add(new Message(Severity.ERROR, "Error created while exporting (" + docRef.toString() + ") : " + e.getMessage()));
            }
        }
    }

    private ExplorerNode getOrCreateParentFolder(ExplorerNode parent, final String path, final boolean create) {
        // Create a parent folder for the new node.
        final String[] elements = path.split("/");

        for (final String element : elements) {
            if (element.length() > 0) {
                List<ExplorerNode> nodes = explorerNodeService.getNodesByName(parent, element);
                nodes = nodes.stream().filter(n -> FOLDER.equals(n.getType())).collect(Collectors.toList());

                if (nodes.size() == 0) {
                    // No parent node can be found for this element so create one if possible.
                    final DocRef folderRef = new DocRef(parent.getType(), parent.getUuid(), parent.getName());
                    if (!securityContext.hasDocumentPermission(folderRef.getType(), folderRef.getUuid(), DocumentPermissionNames.getDocumentCreatePermission(FOLDER))) {
                        throw new PermissionException("You do not have permission to create a folder in '" + folderRef);
                    }

                    // Go and create the folder if we are actually importing now.
                    if (create) {
                        // Go and create the folder.
                        final DocRef newFolder = explorerService.create(FOLDER, element, folderRef, PermissionInheritance.INHERIT);
                        parent = ExplorerNode.create(newFolder);
                    }

                } else {
                    parent = nodes.get(0);
                }
            }
        }

        return parent;
    }

    private DocRefs expandDocRefSet(final DocRefs set) {
        final DocRefs expandedDocRefs = new DocRefs();

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

    private void addDescendants(final DocRef docRef, DocRefs expandedDocRefs) {
        final List<ExplorerNode> descendants = explorerNodeService.getDescendants(docRef);
        for (final ExplorerNode descendant : descendants) {
            addDocRef(descendant.getDocRef(), expandedDocRefs);
        }
    }

    private void addDocRef(final DocRef docRef, final DocRefs docRefs) {
        try {
            if (securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.EXPORT)) {
                docRefs.add(docRef);
            }
        } catch (final Exception e) {
            // We might get a permission exception which is expected for some users.
            LOGGER.debug(e.getMessage(), e);
        }
    }

    private void performExport(final Path dir, final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) throws IOException {
        final ImportExportActionHandler importExportActionHandler = importExportActionHandlers.getHandler(docRef.getType());
        if (importExportActionHandler != null) {
            LOGGER.debug("Exporting: " + docRef);
            final Map<String, String> dataMap = importExportActionHandler.exportDocument(docRef, omitAuditFields, messageList);

            // Get an explorer node for this doc ref.
            final ExplorerNode explorerNode = explorerNodeService.getNode(docRef);
            // Get the explorer path to this doc ref.
            List<ExplorerNode> path = explorerNodeService.getPath(docRef);
            // Turn the path into a list of strings but ignore any nodes that aren't folders, e.g. the root.
            final List<String> pathElements = path.stream()
                    .filter(p -> ExplorerConstants.FOLDER.equals(p.getType()))
                    .map(ExplorerNode::getName).collect(Collectors.toList());
            // Create directories for the path if not already created by another entity.
            final Path parentDir = createDirs(dir, pathElements);

            // Ensure the parent directory exists.
            if (!Files.isDirectory(parentDir)) {
                // Don't output the full path here are we don't want users to see the full file system path.
                messageList.add(new Message(Severity.FATAL_ERROR, "Unable to create directory for folder: " + parentDir.getFileName()));

            } else {
                // Write a file for this explorer entry.
                final String filePrefix = ImportExportFileNameUtil.createFilePrefix(docRef);
                writeNodeProperties(explorerNode, pathElements, parentDir, filePrefix, messageList);

                // Write out all associated data.
                dataMap.forEach((k, v) -> {
                    final String fileName = filePrefix + "." + k;
                    try {
                        final OutputStream outputStream = Files.newOutputStream(parentDir.resolve(fileName));
                        final Writer writer = new OutputStreamWriter(outputStream, CHARSET);
                        writer.write(v);
                        writer.close();

                    } catch (final IOException e) {
                        messageList.add(new Message(Severity.ERROR, "Failed to write file '" + fileName + "'"));
                    }
                });

                importExportEventLog.exportDocument(docRef, null);
            }
        }
    }

    private void writeNodeProperties(final ExplorerNode explorerNode, final List<String> pathElements, final Path parentDir, final String filePrefix, final List<Message> messageList) {
        final String fileName = filePrefix + ".node";

        try {
            final Properties properties = new Properties();
            properties.setProperty("uuid", explorerNode.getUuid());
            properties.setProperty("type", explorerNode.getType());
            properties.setProperty("name", explorerNode.getName());
            properties.setProperty("path", String.join("/", pathElements));
            if (explorerNode.getTags() != null) {
                properties.setProperty("tags", explorerNode.getTags());
            }

            final OutputStream outputStream = Files.newOutputStream(parentDir.resolve(fileName));
            PropertiesSerialiser.write(properties, outputStream);
        } catch (final IOException e) {
            messageList.add(new Message(Severity.ERROR, "Failed to write file '" + fileName + "'"));
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
}