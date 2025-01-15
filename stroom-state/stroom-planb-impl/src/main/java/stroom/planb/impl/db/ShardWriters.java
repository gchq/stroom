package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.meta.shared.Meta;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBNameValidator;
import stroom.planb.impl.data.FileDescriptor;
import stroom.planb.impl.data.FileHashUtil;
import stroom.planb.impl.data.FileTransferClient;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
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
        private final Map<String, AbstractLmdb<?, ?>> writers = new HashMap<>();
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

        public Optional<StateType> getStateType(final String mapName, final Consumer<String> errorConsumer) {
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
            }).map(PlanBDoc::getStateType);
        }

        public void addState(final String mapName,
                             final State state) {
            final StateDb writer = (StateDb) writers.computeIfAbsent(mapName, k ->
                    new StateDb(getLmdbEnvDir(k), byteBufferFactory, overwrite, false));
            writer.insert(state);
        }

        public void addTemporalState(final String mapName,
                                     final TemporalState temporalState) {
            final TemporalStateDb writer = (TemporalStateDb) writers.computeIfAbsent(mapName, k ->
                    new TemporalStateDb(getLmdbEnvDir(k), byteBufferFactory, overwrite, false));
            writer.insert(temporalState);
        }

        public void addRangedState(final String mapName,
                                   final RangedState rangedState) {
            final RangedStateDb writer = (RangedStateDb) writers.computeIfAbsent(mapName, k ->
                    new RangedStateDb(getLmdbEnvDir(k), byteBufferFactory, overwrite, false));
            writer.insert(rangedState);
        }

        public void addTemporalRangedState(final String mapName,
                                           final TemporalRangedState temporalRangedState) {
            final TemporalRangedStateDb writer = (TemporalRangedStateDb) writers.computeIfAbsent(mapName, k ->
                    new TemporalRangedStateDb(getLmdbEnvDir(k), byteBufferFactory, overwrite, false));
            writer.insert(temporalRangedState);
        }

        public void addSession(final String mapName,
                               final Session session) {
            final SessionDb writer = (SessionDb) writers.computeIfAbsent(mapName, k ->
                    new SessionDb(getLmdbEnvDir(k), byteBufferFactory, overwrite, false));
            writer.insert(session, session);
        }

        private Path getLmdbEnvDir(final String name) {
            try {
                final Path path = dir.resolve(name);
                Files.createDirectory(path);
                return path;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            Path zipFile = null;
            try {
                writers.values().forEach(AbstractLmdb::close);

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
