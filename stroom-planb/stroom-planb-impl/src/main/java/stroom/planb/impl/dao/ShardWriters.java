/*
 * Copyright 2025 Crown Copyright
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

package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.meta.shared.Meta;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBNameValidator;
import stroom.planb.impl.dao.histogram.HistogramDb;
import stroom.planb.impl.dao.metric.MetricDb;
import stroom.planb.impl.dao.rangestate.RangeStateDb;
import stroom.planb.impl.dao.session.SessionDb;
import stroom.planb.impl.dao.state.StateDb;
import stroom.planb.impl.dao.temporalrangestate.TemporalRangeStateDb;
import stroom.planb.impl.dao.temporalstate.TemporalStateDb;
import stroom.planb.impl.dao.trace.TraceDb;
import stroom.planb.impl.data.FileDescriptor;
import stroom.planb.impl.data.FileHashUtil;
import stroom.planb.impl.data.FileTransferClient;
import stroom.planb.impl.data.RangeState;
import stroom.planb.impl.data.SequentialFileStore;
import stroom.planb.impl.data.Session;
import stroom.planb.impl.data.SpanKV;
import stroom.planb.impl.data.State;
import stroom.planb.impl.data.TemporalRangeState;
import stroom.planb.impl.data.TemporalState;
import stroom.planb.impl.data.TemporalValue;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.util.io.FileUtil;
import stroom.util.io.PathSegmentUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class ShardWriters {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ShardWriters.class);

    private final PlanBDocCache planBDocCache;
    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final StatePaths statePaths;
    private final FileTransferClient fileTransferClient;

    @Inject
    ShardWriters(final PlanBDocCache planBDocCache,
                 final ByteBuffers byteBuffers,
                 final ByteBufferFactory byteBufferFactory,
                 final StatePaths statePaths,
                 final FileTransferClient fileTransferClient) {
        this.planBDocCache = planBDocCache;
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;

        // Clear writer dir on startup since any remaining data must not have been sent so processing cannot have
        // completed.
        if (Files.isDirectory(statePaths.getWriterDir())) {
            FileUtil.deleteDir(statePaths.getWriterDir());
        }
    }

    public ShardWriter createWriter(final Meta meta) {
        final Path dir;
        try {
            dir = statePaths.getWriterDir()
                    .resolve(meta.getId() + "_" + UUID.randomUUID());
            Files.createDirectories(dir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ShardWriter(planBDocCache, byteBuffers, byteBufferFactory, fileTransferClient, dir, meta);
    }

    public static class ShardWriter implements AutoCloseable {

        private final PlanBDocCache planBDocCache;
        private final ByteBuffers byteBuffers;
        private final ByteBufferFactory byteBufferFactory;
        private final FileTransferClient fileTransferClient;
        private final Path dir;
        private final Meta meta;
        private final Map<String, WriterInstance> writers = new HashMap<>();
        private final Map<String, Optional<PlanBDoc>> stateDocMap = new HashMap<>();

        public ShardWriter(final PlanBDocCache planBDocCache,
                           final ByteBuffers byteBuffers,
                           final ByteBufferFactory byteBufferFactory,
                           final FileTransferClient fileTransferClient,
                           final Path dir,
                           final Meta meta) {
            this.planBDocCache = planBDocCache;
            this.byteBuffers = byteBuffers;
            this.byteBufferFactory = byteBufferFactory;
            this.fileTransferClient = fileTransferClient;
            this.dir = dir;
            this.meta = meta;
        }

        public Optional<PlanBDoc> getDoc(final String mapName, final Consumer<String> errorConsumer) {
            Optional<PlanBDoc> result = Optional.empty();

            try {
                if (NullSafe.isBlankString(mapName)) {
                    throw new RuntimeException("Null map name");
                }

                result = stateDocMap.computeIfAbsent(mapName, k -> {
                    PlanBDoc doc = null;

                    try {
                        if (!PlanBNameValidator.isValidName(k)) {
                            throw new RuntimeException("Bad map name: " + k);
                        } else {
                            doc = planBDocCache.get(k);
                            if (doc == null) {
                                throw new RuntimeException("Unable to find state doc for map name: " + k);
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e::getMessage, e);
                        errorConsumer.accept(e.getMessage());
                    }

                    return Optional.ofNullable(doc);
                });
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
                errorConsumer.accept(e.getMessage());
            }

            return result;
        }

        private static class WriterInstance implements AutoCloseable {

            private final Db<?, ?> lmdb;
            private final LmdbWriter writer;
            private final boolean synchroniseMerge;

            public WriterInstance(final Db<?, ?> lmdb, final boolean synchroniseMerge) {
                this.lmdb = lmdb;
                this.writer = lmdb.createWriter();
                this.synchroniseMerge = synchroniseMerge;
            }

            public void addState(final State state) {
                final StateDb db = (StateDb) lmdb;
                db.insert(writer, state);
            }

            public void addTemporalState(final TemporalState temporalState) {
                final TemporalStateDb db = (TemporalStateDb) lmdb;
                db.insert(writer, temporalState);
            }

            public void addRangeState(final RangeState rangeState) {
                final RangeStateDb db = (RangeStateDb) lmdb;
                db.insert(writer, rangeState);
            }

            public void addTemporalRangeState(final TemporalRangeState temporalRangeState) {
                final TemporalRangeStateDb db = (TemporalRangeStateDb) lmdb;
                db.insert(writer, temporalRangeState);
            }

            public void addSession(final Session session) {
                final SessionDb db = (SessionDb) lmdb;
                db.insert(writer, session);
            }

            public void addHistogramValue(final TemporalValue temporalValue) {
                final HistogramDb db = (HistogramDb) lmdb;
                db.insert(writer, temporalValue);
            }

            public void addMetricValue(final TemporalValue temporalValue) {
                final MetricDb db = (MetricDb) lmdb;
                db.insert(writer, temporalValue);
            }

            public void addSpanValue(final SpanKV spanKV) {
                final TraceDb db = (TraceDb) lmdb;
                db.insert(writer, spanKV);
            }

            public boolean isSynchroniseMerge() {
                return synchroniseMerge;
            }

            @Override
            public void close() {
                // writer.close() commits, so it can fail on a full disk or map. The env must
                // still be closed when it does, or the caller's cleanup deletes this dir with
                // an open env on it. The commit failure still propagates.
                RuntimeException commitFailure = null;
                try {
                    writer.close();
                } catch (final RuntimeException e) {
                    commitFailure = e;
                } finally {
                    try {
                        lmdb.close();
                    } catch (final RuntimeException e) {
                        // Don't let this hide why the commit failed, e.g. a full store.
                        if (commitFailure != null) {
                            commitFailure.addSuppressed(e);
                        } else {
                            commitFailure = e;
                        }
                    }
                }
                if (commitFailure != null) {
                    throw commitFailure;
                }
            }
        }

        public void addState(final PlanBDoc doc,
                             final State state) {
            getWriter(doc).addState(state);
        }

        public void addTemporalState(final PlanBDoc doc,
                                     final TemporalState temporalState) {
            getWriter(doc).addTemporalState(temporalState);
        }

        public void addRangeState(final PlanBDoc doc,
                                  final RangeState rangeState) {
            getWriter(doc).addRangeState(rangeState);
        }

        public void addTemporalRangeState(final PlanBDoc doc,
                                          final TemporalRangeState temporalRangeState) {
            getWriter(doc).addTemporalRangeState(temporalRangeState);
        }

        public void addSession(final PlanBDoc doc,
                               final Session session) {
            getWriter(doc).addSession(session);
        }

        public void addHistogramValue(final PlanBDoc doc,
                                      final TemporalValue temporalValue) {
            getWriter(doc).addHistogramValue(temporalValue);
        }

        public void addMetricValue(final PlanBDoc doc,
                                   final TemporalValue temporalValue) {
            getWriter(doc).addMetricValue(temporalValue);
        }

        public void addSpanValue(final PlanBDoc doc,
                                 final SpanKV spanKV) {
            getWriter(doc).addSpanValue(spanKV);
        }

        private WriterInstance getWriter(final PlanBDoc doc) {
            // Keyed on the doc uuid, not the doc. PlanBDoc has value based equality, so if a doc
            // is renamed mid stream both names resolve to one uuid and keying on the doc would
            // open a second env on the same dir.
            return writers.computeIfAbsent(doc.getUuid(), k -> {
                // Everything after the open must either be registered in the map or undone.
                // writeSourceMetaId commits, so it fails on a full disk or map exactly like the
                // close path does, and computeIfAbsent stores nothing when the mapping function
                // throws. The env would then be open with nothing referencing it, so close()
                // could not close it and the cleanup would delete this dir from under it.
                Db<?, ?> db = null;
                try {
                    db = PlanBDb.open(doc,
                            getLmdbEnvDir(doc),
                            byteBuffers,
                            byteBufferFactory,
                            false);
                    // Record the stream this part shard is written from, for provenance.
                    db.writeSourceMetaId(meta.getId());
                    return new WriterInstance(db,
                            NullSafe.getOrElse(
                                    doc,
                                    PlanBDoc::getSettings,
                                    AbstractPlanBSettings::getSynchroniseMerge,
                                    false));
                } catch (final Throwable t) {
                    if (db != null) {
                        try {
                            db.close();
                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                            t.addSuppressed(e);
                        }
                    }
                    throw t;
                }
            });
        }

        private Path getLmdbEnvDir(final PlanBDoc doc) {
            try {
                final Path path = dir.resolve(PathSegmentUtil.requireSafeSegment(doc.getUuid()));
                // createDirectories, not createDirectory: if a previous record for this map
                // failed after the dir was made, the retry must not fail with a misleading
                // "file already exists" that hides the original cause.
                Files.createDirectories(path);
                return path;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() {
            LOGGER.debug(() -> LogUtil.message("Plan B finished processing for {}", meta));
            final Path parent = dir.getParent();
            final Path zipFile = parent.resolve(dir.getFileName().toString() + SequentialFileStore.ZIP_EXTENSION);

            try {
                if (!writers.isEmpty()) {
                    // Close every writer even if one fails. WriterInstance.close() commits, so
                    // a full disk or map on one writer used to abort this loop, leaving that
                    // writer and every later one open — and the finally below then deleted the
                    // dir out from under them.
                    RuntimeException closeFailure = null;
                    for (final WriterInstance writerInstance : writers.values()) {
                        try {
                            writerInstance.close();
                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                            if (closeFailure == null) {
                                closeFailure = e;
                            }
                        }
                    }
                    if (closeFailure != null) {
                        // The part is incomplete, so don't zip and send it. The dir is cleaned
                        // up in the finally as before and the stream needs reprocessing, but
                        // every env is closed by now so nothing is unlinked from under one.
                        throw closeFailure;
                    }

                    final boolean synchroniseMerge = writers
                            .values()
                            .stream()
                            .anyMatch(WriterInstance::isSynchroniseMerge);

                    // Zip all.
                    LOGGER.debug(() -> LogUtil.message("Plan B zipping data for {}", meta));
                    LOGGER.trace(() -> {
                        try (final Stream<Path> stream = Files.list(dir)) {
                            final String paths = stream
                                    .map(Path::getFileName)
                                    .map(Path::toString)
                                    .collect(Collectors.joining(", "));
                            return "Dir contents = " + paths;
                        } catch (final IOException e) {
                            LOGGER.error(e::getMessage, e);
                        }
                        return null;
                    });

                    ZipUtil.zip(zipFile, dir);
                    final String fileHash = FileHashUtil.hash(zipFile);

                    final FileDescriptor fileDescriptor = new FileDescriptor(
                            System.currentTimeMillis(),
                            meta.getId(),
                            fileHash);
                    LOGGER.debug(() -> LogUtil.message(
                            "Plan B sending data {} for {}",
                            zipFile.getFileName().toString(),
                            meta));
                    fileTransferClient.storePart(fileDescriptor, zipFile, synchroniseMerge);
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);

            } finally {
                try {
                    // Cleanup.
                    FileUtil.deleteDir(dir);
                    Files.deleteIfExists(zipFile);
                } catch (final Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        }
    }
}
