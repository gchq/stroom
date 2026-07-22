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

package stroom.pipeline.stepping.store;

import stroom.pipeline.stepping.session.SteppingSession;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Owns the base directory for persisted stepping IO and hands out a {@link StepDataStore} per
 * (session, stream). Session directories live under {@code {stroom.temp}/{storeSubDir}/{sessionId}} and
 * are deleted when a session closes. {@link #cleanupOrphans()} sweeps up anything left behind by a hard
 * shutdown, and is run on a schedule.
 * <p>
 * {@link #getOrCreateStore} must not run concurrently with {@link #deleteSession} for the same session id
 * - a create racing a delete could re-create a store in a just-removed map (leaking its channels) or
 * write into a directory being deleted. {@link SteppingSession} owns session lifecycle and serialises
 * creation against teardown under its own lock, so callers must go through it rather than creating stores
 * for a session directly.
 */
@Singleton
public class StepDataStoreManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StepDataStoreManager.class);

    private final TempDirProvider tempDirProvider;
    private final SteppingConfig config;

    // sessionId -> (metaId -> store)
    private final Map<String, Map<Long, StepDataStore>> sessions = new ConcurrentHashMap<>();

    @Inject
    public StepDataStoreManager(final TempDirProvider tempDirProvider,
                                final SteppingConfig config) {
        this.tempDirProvider = tempDirProvider;
        this.config = config;
    }

    /**
     * Get (creating if necessary) the store for a given stream within a session.
     */
    public StepDataStore getOrCreateStore(final String sessionId, final long metaId) {
        final Map<Long, StepDataStore> streamStores = sessions.computeIfAbsent(
                sessionId, k -> new ConcurrentHashMap<>());
        return streamStores.computeIfAbsent(metaId, id -> {
            final Path streamDir = getSessionDir(sessionId).resolve(Long.toString(id));
            FileUtil.mkdirs(streamDir);
            return new StepDataStore(streamDir, config);
        });
    }

    /**
     * Delete all persisted IO for a session and forget it.
     */
    public void deleteSession(final String sessionId) {
        final Map<Long, StepDataStore> streamStores = sessions.remove(sessionId);
        if (streamStores != null) {
            streamStores.values().forEach(store -> {
                try {
                    store.deleteAll();
                } catch (final RuntimeException e) {
                    LOGGER.debug(() -> LogUtil.message("Error deleting store for session {}", sessionId), e);
                }
            });
        }
        // Belt-and-braces: remove the session dir even if a store failed to.
        FileUtil.deleteDir(getSessionDir(sessionId));
    }

    /**
     * Delete session directories that no live session owns and that are older than
     * {@code orphanMaxAge}. A session normally deletes its own directory on close, so anything this finds
     * was stranded by a hard shutdown or a crash - stepping data is temporary, but it is not small, and
     * nothing else will ever remove it.
     * <p>
     * The age check is what makes this safe to run against a live system: a directory belonging to a
     * session created on another node, or one created in the moment between {@code mkdirs} and the session
     * being registered, is far younger than the threshold and is left alone.
     */
    public void cleanupOrphans() {
        final Path baseDir = getBaseDir();
        final Instant oldest = Instant.now().minus(config.getOrphanMaxAge().getDuration());

        try (final Stream<Path> children = Files.list(baseDir)) {
            children.filter(Files::isDirectory).forEach(dir -> {
                final String sessionId = dir.getFileName().toString();
                if (sessions.containsKey(sessionId)) {
                    return;
                }
                try {
                    if (Files.getLastModifiedTime(dir).toInstant().isBefore(oldest)) {
                        LOGGER.info(() -> LogUtil.message("Deleting orphaned stepping session dir {}",
                                FileUtil.getCanonicalPath(dir)));
                        FileUtil.deleteDir(dir);
                    }
                } catch (final IOException | RuntimeException e) {
                    LOGGER.debug(() -> LogUtil.message("Error checking stepping session dir {}",
                            FileUtil.getCanonicalPath(dir)), e);
                }
            });
        } catch (final IOException | RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Error listing stepping base dir {}",
                    FileUtil.getCanonicalPath(baseDir)), e);
        }
    }

    /**
     * Delete every session's data. Called on clean shutdown - nothing in the store survives a restart, as
     * the sessions that could read it are gone.
     */
    public void deleteAllSessions() {
        sessions.keySet().forEach(this::deleteSession);
        FileUtil.deleteDir(getBaseDir());
    }

    /**
     * @return the base directory under which all session directories are created.
     */
    public Path getBaseDir() {
        final Path baseDir = tempDirProvider.get().resolve(config.getStoreSubDir());
        FileUtil.mkdirs(baseDir);
        return baseDir;
    }

    /**
     * @return the directory holding all of a session's captured data, one sub-directory per swept stream.
     */
    public Path getSessionDir(final String sessionId) {
        return getBaseDir().resolve(sessionId);
    }
}
