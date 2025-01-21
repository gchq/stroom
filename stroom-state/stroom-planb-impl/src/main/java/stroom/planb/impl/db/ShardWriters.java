package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.meta.shared.Meta;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBNameValidator;
import stroom.planb.impl.data.FileDescriptor;
import stroom.planb.impl.data.FileHashUtil;
import stroom.planb.impl.data.FileTransferClient;
import stroom.planb.shared.PlanBDoc;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ShardWriters {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ShardWriters.class);

    private final PlanBDocCache planBDocCache;
    private final ByteBufferFactory byteBufferFactory;
    private final StatePaths statePaths;
    private final FileTransferClient fileTransferClient;

    @Inject
    ShardWriters(final PlanBDocCache planBDocCache,
                 final ByteBufferFactory byteBufferFactory,
                 final StatePaths statePaths,
                 final FileTransferClient fileTransferClient) {
        this.planBDocCache = planBDocCache;
        this.byteBufferFactory = byteBufferFactory;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;
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
        return new ShardWriter(planBDocCache, byteBufferFactory, fileTransferClient, dir, meta);
    }

    public static class ShardWriter implements AutoCloseable {

        private final PlanBDocCache planBDocCache;
        private final ByteBufferFactory byteBufferFactory;
        private final FileTransferClient fileTransferClient;
        private final Path dir;
        private final Meta meta;
        private final Map<PlanBDoc, WriterInstance> writers = new HashMap<>();
        private final Map<String, Optional<PlanBDoc>> stateDocMap = new HashMap<>();
        private final boolean overwrite = true;

        public ShardWriter(final PlanBDocCache planBDocCache,
                           final ByteBufferFactory byteBufferFactory,
                           final FileTransferClient fileTransferClient,
                           final Path dir,
                           final Meta meta) {
            this.planBDocCache = planBDocCache;
            this.byteBufferFactory = byteBufferFactory;
            this.fileTransferClient = fileTransferClient;
            this.dir = dir;
            this.meta = meta;
        }

        public Optional<PlanBDoc> getDoc(final String mapName, final Consumer<String> errorConsumer) {
            if (NullSafe.isBlankString(mapName)) {
                errorConsumer.accept("Null map key");
                return Optional.empty();
            }

            return stateDocMap.computeIfAbsent(mapName, k -> {
                PlanBDoc doc = null;

                if (!PlanBNameValidator.isValidName(k)) {
                    errorConsumer.accept("Bad map key: " + k);
                } else {
                    try {
                        doc = planBDocCache.get(k);
                        if (doc == null) {
                            errorConsumer.accept("Unable to find state doc for map key: " + k);
                        }
                    } catch (final RuntimeException e) {
                        errorConsumer.accept(e.getMessage());
                    }
                }

                return Optional.ofNullable(doc);
            });
        }

        private static class WriterInstance implements AutoCloseable {

            private final AbstractLmdb<?, ?> lmdb;
            private final AbstractLmdb.Writer writer;

            public WriterInstance(final AbstractLmdb<?, ?> lmdb) {
                this.lmdb = lmdb;
                this.writer = lmdb.createWriter();
            }

            public void addState(final State state) {
                final StateDb db = (StateDb) lmdb;
                db.insert(writer, state);
            }

            public void addTemporalState(final TemporalState temporalState) {
                final TemporalStateDb db = (TemporalStateDb) lmdb;
                db.insert(writer, temporalState);
            }

            public void addRangedState(final RangedState rangedState) {
                final RangedStateDb db = (RangedStateDb) lmdb;
                db.insert(writer, rangedState);
            }

            public void addTemporalRangedState(final TemporalRangedState temporalRangedState) {
                final TemporalRangedStateDb db = (TemporalRangedStateDb) lmdb;
                db.insert(writer, temporalRangedState);
            }

            public void addSession(final Session session) {
                final SessionDb db = (SessionDb) lmdb;
                db.insert(writer, session, session);
            }

            @Override
            public void close() {
                writer.close();
                lmdb.close();
            }
        }

        public void addState(final PlanBDoc doc,
                             final State state) {
            final WriterInstance writer = writers.computeIfAbsent(doc, k -> new WriterInstance(
                    new StateDb(getLmdbEnvDir(k), byteBufferFactory, overwrite, false)));
            writer.addState(state);
        }

        public void addTemporalState(final PlanBDoc doc,
                                     final TemporalState temporalState) {
            final WriterInstance writer = writers.computeIfAbsent(doc, k -> new WriterInstance(
                    new TemporalStateDb(getLmdbEnvDir(k), byteBufferFactory, overwrite, false)));
            writer.addTemporalState(temporalState);
        }

        public void addRangedState(final PlanBDoc doc,
                                   final RangedState rangedState) {
            final WriterInstance writer = writers.computeIfAbsent(doc, k -> new WriterInstance(
                    new RangedStateDb(getLmdbEnvDir(k), byteBufferFactory, overwrite, false)));
            writer.addRangedState(rangedState);
        }

        public void addTemporalRangedState(final PlanBDoc doc,
                                           final TemporalRangedState temporalRangedState) {
            final WriterInstance writer = writers.computeIfAbsent(doc, k -> new WriterInstance(
                    new TemporalRangedStateDb(getLmdbEnvDir(k), byteBufferFactory, overwrite, false)));
            writer.addTemporalRangedState(temporalRangedState);
        }

        public void addSession(final PlanBDoc doc,
                               final Session session) {
            final WriterInstance writer = writers.computeIfAbsent(doc, k -> new WriterInstance(
                    new SessionDb(getLmdbEnvDir(k), byteBufferFactory, overwrite, false)));
            writer.addSession(session);
        }

        private Path getLmdbEnvDir(final PlanBDoc doc) {
            try {
                final Path path = dir.resolve(doc.getUuid());
                Files.createDirectory(path);
                return path;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            if (!writers.isEmpty()) {
                Path zipFile = null;
                try {
                    writers.values().forEach(WriterInstance::close);

                    // Zip all and delete dir.
                    zipFile = dir.getParent().resolve(dir.getFileName().toString() + ".zip");
                    ZipUtil.zip(zipFile, dir);
                    FileUtil.deleteDir(dir);

                    final String fileHash = FileHashUtil.hash(zipFile);

                    final FileDescriptor fileDescriptor = new FileDescriptor(
                            System.currentTimeMillis(),
                            meta.getId(),
                            fileHash);
                    fileTransferClient.storePart(fileDescriptor, zipFile);

                } catch (final IOException e) {
                    throw new UncheckedIOException(e);

                } finally {
                    try {
                        FileUtil.deleteDir(dir);
                        if (zipFile != null) {
                            FileUtil.delete(zipFile);
                        }
                    } catch (final Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                }
            }
        }
    }
}
