package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.meta.shared.Meta;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBNameValidator;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
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

    private final PlanBDocCache planBDocCache;
    private final TempDirProvider tempDirProvider;
    private final ByteBufferFactory byteBufferFactory;

    @Inject
    ShardWriters(final PlanBDocCache planBDocCache,
                 final TempDirProvider tempDirProvider,
                 final ByteBufferFactory byteBufferFactory) {
        this.planBDocCache = planBDocCache;
        this.tempDirProvider = tempDirProvider;
        this.byteBufferFactory = byteBufferFactory;
    }

    public ShardWriter createWriter(final Meta meta) {
        final Path dir;
        try {
            dir = tempDirProvider
                    .get()
                    .resolve("planb")
                    .resolve(meta.getId() + "_" + UUID.randomUUID());
            Files.createDirectories(dir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ShardWriter(planBDocCache, byteBufferFactory, dir);
    }

    public static class ShardWriter implements AutoCloseable {

        private final PlanBDocCache planBDocCache;
        private final ByteBufferFactory byteBufferFactory;
        private final Path dir;
        private final Map<String, AbstractLmdbWriter<?, ?>> writers = new HashMap<>();
        private final Map<String, Optional<PlanBDoc>> stateDocMap = new HashMap<>();
        private final boolean keepFirst = false;

        public ShardWriter(final PlanBDocCache planBDocCache,
                           final ByteBufferFactory byteBufferFactory,
                           final Path dir) {
            this.planBDocCache = planBDocCache;
            this.byteBufferFactory = byteBufferFactory;
            this.dir = dir;
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
            final StateWriter writer = (StateWriter) writers.computeIfAbsent(mapName, k ->
                    new StateWriter(getLmdbEnvDir(k), byteBufferFactory, keepFirst));
            writer.insert(state);
        }

        public void addTemporalState(final String mapName,
                                     final TemporalState temporalState) {
            final TemporalStateWriter writer = (TemporalStateWriter) writers.computeIfAbsent(mapName, k ->
                    new TemporalStateWriter(getLmdbEnvDir(k), byteBufferFactory, keepFirst));
            writer.insert(temporalState);
        }

        public void addRangedState(final String mapName,
                                   final RangedState rangedState) {
            final RangedStateWriter writer = (RangedStateWriter) writers.computeIfAbsent(mapName, k ->
                    new RangedStateWriter(getLmdbEnvDir(k), byteBufferFactory, keepFirst));
            writer.insert(rangedState);
        }

        public void addTemporalRangedState(final String mapName,
                                           final TemporalRangedState temporalRangedState) {
            final TemporalRangedStateWriter writer = (TemporalRangedStateWriter) writers.computeIfAbsent(mapName, k ->
                    new TemporalRangedStateWriter(getLmdbEnvDir(k), byteBufferFactory, keepFirst));
            writer.insert(temporalRangedState);
        }

        public void addSession(final String mapName,
                               final Session session) {
//            final SessionDao dao = (SessionDao) daoMap.computeIfAbsent(mapName, k ->
//                    new SessionDao(getLmdbEnvDir(k), byteBufferFactory, k));
//            dao.insert(session);
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
            try {
                writers.values().forEach(AbstractLmdbWriter::close);

                // Zip all and delete dir.
                final Path zipFile = dir.getParent().resolve(dir.getFileName().toString() + ".zip");
                ZipUtil.zip(zipFile, dir);
                FileUtil.deleteDir(dir);

                // Now post to leader.

            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
