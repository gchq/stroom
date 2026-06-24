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

package stroom.docstore.impl.fs;

import stroom.docref.DocRef;
import stroom.docstore.api.RWLockFactory;
import stroom.docstore.impl.GenericDoc;
import stroom.docstore.impl.Persistence;
import stroom.docstore.shared.AuditAction;
import stroom.docstore.shared.DocAuditEntry;
import stroom.docstore.shared.DocAuditUser;
import stroom.docstore.shared.DocDataType;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.PredicateUtil;
import stroom.util.io.PathCreator;
import stroom.util.json.JsonUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.string.PatternUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class FSPersistence implements Persistence, Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FSPersistence.class);

    private static final String META = "meta";

    private final RWLockFactory lockFactory = new StripedLockFactory();
    private final Path dir;
    private final JsonMapper jsonMapper;

    @SuppressWarnings("unused")
    @Inject
    public FSPersistence(final FSPersistenceConfig config, final PathCreator pathCreator) {
        this(pathCreator.toAppPath(config.getPath()));
    }

    public FSPersistence(final Path absoluteDir) {
        try {
            this.dir = absoluteDir;
            LOGGER.debug("Using path {}", absoluteDir);
            Files.createDirectories(absoluteDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        jsonMapper = JsonUtil.getNoIndentMapper();
    }


    @Override
    public boolean exists(final DocRef docRef) {
        final Path filePath = getPath(docRef, META);
        return Files.isRegularFile(filePath);
    }

    @Override
    public ImportExportDocument read(final DocRef docRef) throws IOException {
        final ImportExportDocument importExportDocument = new ImportExportDocument();

        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(getPathForType(docRef.getType()),
                docRef.getUuid() + ".*")) {
            stream.forEach(file -> {
                try {
                    final String fileName = file.getFileName().toString();
                    final int index = fileName.indexOf(".");
//                    final String uuid = fileName.substring(0, index);
                    final String ext = fileName.substring(index + 1);

                    final byte[] bytes = Files.readAllBytes(file);
                    importExportDocument.addExtAsset(
                            new ByteArrayImportExportAsset(ext, DocDataType.BINARY, bytes));

                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        if (importExportDocument.getExtAssets().isEmpty()) {
            return null;
        }

        return importExportDocument;
    }

    @Override
    public void write(final DocRef docRef,
                      final AuditAction auditAction,
                      final ImportExportDocument importExportDocument) {
        final Path filePath = getPath(docRef, META);
        if (auditAction.isUpdate()) {
            if (!Files.isRegularFile(filePath)) {
                throw new RuntimeException("Document does not exist with uuid=" + docRef.getUuid());
            }
        } else if (auditAction.isCreate() && Files.isRegularFile(filePath)) {
            throw new RuntimeException("Document already exists with uuid=" + docRef.getUuid());
        }

        for (final ImportExportAsset asset : importExportDocument.getExtAssets()) {
            try {
                final byte[] data = asset.getInputData();
                if (data != null) {
                    Files.write(getPath(docRef, asset.getKey()), data);
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public void delete(final DocRef docRef) {
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(getPathForType(docRef.getType()),
                docRef.getUuid() + ".*")) {
            stream.forEach(file -> {
                try {
                    Files.delete(file);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<DocRef> list(final Collection<String> types) {
        if (NullSafe.isEmptyCollection(types)) {
            return Collections.emptyList();
        }
        final List<DocRef> list = new ArrayList<>();
        types.forEach(type -> {
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(getPathForType(type), "*." + META)) {
                stream.forEach(file -> {
                    final String fileName = file.getFileName().toString();
                    final int index = fileName.indexOf(".");
                    final String uuid = fileName.substring(0, index);
                    final Optional<String> name = getName(file);
                    list.add(new DocRef(type, uuid, name.orElse(null)));
                });
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return list;
    }

    @Override
    public List<DocRef> list(final String type) {
        return list(Collections.singleton(type));
    }

    /**
     * Find docRefs by name and type. Name can be optionally wild carded using '*' to match 0-many chars.
     */
    @Override
    public List<DocRef> find(final String type,
                             final String nameFilter,
                             final boolean allowWildCards) {
        // Default impl that does all filtering in java. Not efficient for DB impls.
        return nameFilter == null
                ? Collections.emptyList()
                : find(type, List.of(nameFilter), allowWildCards);
    }

    /**
     * Find docRefs by type and one or more nameFilters.
     * nameFilters can be optionally wild carded using '*' to match 0-many chars.
     */
    @Override
    public List<DocRef> find(final String type,
                             final List<String> nameFilters,
                             final boolean allowWildCards) {
        return find(List.of(type), nameFilters, allowWildCards);
    }

    /**
     * Find docRefs by name across multiple types. If types is null or empty, searches ALL types.
     * This is the cross-type variant used by caches and services.
     */
    @Override
    public List<DocRef> find(final Collection<String> types,
                             final List<String> nameFilters,
                             final boolean allowWildCards) {
        // Default impl that does all filtering in java. Not efficient for DB impls.
        if (NullSafe.isEmptyCollection(nameFilters)) {
            return Collections.emptyList();
        } else {
            // Merge the filters into one predicate
            final Predicate<DocRef> combinedPredicate = nameFilters.stream()
                    .map(nameFilter -> {
                        final Predicate<DocRef> predicate;
                        if (allowWildCards && PatternUtil.containsWildCards(nameFilter)) {
                            final Pattern pattern = PatternUtil.createPatternFromWildCardFilter(
                                    nameFilter, true);
                            predicate = docRef ->
                                    pattern.matcher(docRef.getName()).matches();
                        } else {
                            predicate = docRef ->
                                    nameFilter.equals(docRef.getName());
                        }
                        return predicate;
                    })
                    .reduce(PredicateUtil::orPredicates)
                    .orElse(val -> false); // no filters, no matches

            return list(types)
                    .stream()
                    .filter(combinedPredicate)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public Optional<String> getName(final DocRef docRef) {
        final Path filePath = getPath(docRef, META);
        return getName(filePath);
    }

//    @Override
//    public List<DocRef> findByName(final String name) {
//        final List<DocRef> list = new ArrayList<>();
//        try (final Stream<Path> typeStream = Files.list(dir)) {
//            typeStream.forEach(typeDir -> {
//                if (Files.isDirectory(typeDir)) {
//                    final String typeName = typeDir.getFileName().toString();
//                    try (final DirectoryStream<Path> stream = Files.newDirectoryStream(typeDir, "*." + META)) {
//                        stream.forEach(file -> {
//                            final String fileName = file.getFileName().toString();
//                            final int index = fileName.indexOf(".");
//                            final String uuid = fileName.substring(0, index);
//                            final Optional<String> docName = getName(file);
//                            if (docName.isPresent() && docName.get().equals(name)) {
//                                list.add(new DocRef(typeName, uuid, docName.get()));
//                            }
//                        });
//                    } catch (final IOException e) {
//                        throw new UncheckedIOException(e);
//                    }
//                }
//            });
//        } catch (final IOException e) {
//            throw new UncheckedIOException(e);
//        }
//        return list;
//    }

    @Override
    public ResultPage<DocAuditEntry> getAuditInfo(final DocRef docRef) {
        final Path filePath = getPath(docRef, META);
        final Optional<GenericDoc> optional = getGenericDoc(filePath);
        return optional
                .map(document -> {
                    final List<DocAuditEntry> list = new ArrayList<>();
                    list.add(new DocAuditEntry(document.getCreateTimeMs(),
                            new DocAuditUser(null, document.getCreateUser()), AuditAction.CREATE));
                    list.add(new DocAuditEntry(document.getUpdateTimeMs(),
                            new DocAuditUser(null, document.getUpdateUser()), AuditAction.UPDATE));
                    return ResultPage.createUnboundedList(list);
                })
                .orElse(ResultPage.empty());
    }

    @Override
    public RWLockFactory getLockFactory() {
        return lockFactory;
    }

    @Override
    public List<DocRef> findDocRefsEmbeddedIn(final DocRef parent) {
        throw new RuntimeException("Not yet implemented");
    }

    private Path getPath(final DocRef docRef, final String ext) {
        return getPathForType(docRef.getType()).resolve(docRef.getUuid() + "." + ext);
    }

    private Path getPathForType(final String type) {
        final Path path = dir.resolve(type);
        try {
            if (!Files.isDirectory(path)) {
                Files.createDirectories(path);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return path;
    }

    @Override
    public void clear() {
        recursiveDelete(dir);
    }

    private void recursiveDelete(final Path path) {
        try {
            final FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    if (!dir.equals(path)) {
                        Files.delete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            };
            Files.walkFileTree(path, visitor);
        } catch (final NotDirectoryException e) {
            // Ignore.
        } catch (final IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }

    private Optional<String> getName(final Path metaFile) {
        return getGenericDoc(metaFile).map(GenericDoc::getName);
    }

    private Optional<GenericDoc> getGenericDoc(final Path metaFile) {
        if (Files.exists(metaFile)) {
            try {
                final byte[] data = Files.readAllBytes(metaFile);
                final GenericDoc genericDoc = jsonMapper.readValue(data, GenericDoc.class);
                return Optional.ofNullable(genericDoc);

            } catch (final IOException | RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        return Optional.empty();
    }
}
