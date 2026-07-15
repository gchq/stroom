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

package stroom.pipeline.stepping;

import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the base directory for persisted stepping IO and hands out a {@link StepDataStore} per
 * (session, stream). Session directories live under {@code {stroom.temp}/{storeSubDir}/{sessionId}} and
 * are deleted when a session closes; a scheduled sweep of orphaned session directories is added in a
 * later phase.
 * <p>
 * NOTE (Phase 4): {@link #getOrCreateStore} must not run concurrently with {@link #deleteSession} for
 * the same session id - a create racing a delete could re-create a store in a just-removed map (leaking
 * its channels) or write into a directory being deleted. The Phase 4 {@code SteppingService}/session
 * owns session lifecycle and will serialize creation against teardown; until then callers must not
 * create stores for a session while tearing it down.
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
     * @return the base directory under which all session directories are created.
     */
    public Path getBaseDir() {
        final Path baseDir = tempDirProvider.get().resolve(config.getStoreSubDir());
        FileUtil.mkdirs(baseDir);
        return baseDir;
    }

    Path getSessionDir(final String sessionId) {
        return getBaseDir().resolve(sessionId);
    }
}
