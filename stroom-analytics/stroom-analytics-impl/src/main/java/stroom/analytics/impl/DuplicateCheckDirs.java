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

package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbEnvDirFactory;
import stroom.query.common.v2.DuplicateCheckStoreConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DuplicateCheckDirs {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckDirs.class);

    private final LmdbEnvDirFactory lmdbEnvDirFactory;
    private final DuplicateCheckStoreConfig duplicateCheckStoreConfig;

    @Inject
    public DuplicateCheckDirs(final LmdbEnvDirFactory lmdbEnvDirFactory,
                              final DuplicateCheckStoreConfig duplicateCheckStoreConfig) {
        this.lmdbEnvDirFactory = lmdbEnvDirFactory;
        this.duplicateCheckStoreConfig = duplicateCheckStoreConfig;
    }

    public LmdbEnvDir getDir(final String analyticRuleUUID) {
        return lmdbEnvDirFactory
                .builder()
                .config(duplicateCheckStoreConfig.getLmdbConfig())
                .subDir(analyticRuleUUID)
                .build();
    }

    /**
     * Get a list of duplicate checking store UUID names that currently exist.
     *
     * @return A list of duplicate checking store UUID names that currently exist.
     */
    public List<String> getAnalyticRuleUUIDList() {
        final List<String> uuidList = new ArrayList<>();
        try {
            final Path dir = lmdbEnvDirFactory
                    .builder()
                    .config(duplicateCheckStoreConfig.getLmdbConfig())
                    .build()
                    .getEnvDir();
            if (Files.isDirectory(dir)) {
                try (final Stream<Path> stream = Files.list(dir)) {
                    stream.forEach(path -> {
                        if (Files.isDirectory(path)) {
                            uuidList.add(path.getFileName().toString());
                        }
                    });
                }
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
        return uuidList;
    }

    public List<String> deleteUnused(
            final List<String> duplicateStoreUuids,
            final List<AnalyticRuleDoc> analytics) {
        final List<String> deletedUuids = new ArrayList<>();
        try {
            LOGGER.debug(() -> LogUtil.message(
                    "deleteUnused() - duplicateStoreUuids.size: {}, analytics.size: {}",
                    NullSafe.size(duplicateStoreUuids), NullSafe.size(analytics)));
            if (NullSafe.hasItems(duplicateStoreUuids)) {
                final List<String> redundantDupStoreUuids;
                if (NullSafe.hasItems(analytics)) {
                    final Set<String> analyticUuids = analytics.stream()
                            .filter(Objects::nonNull)
                            .map(AnalyticRuleDoc::getUuid)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    // Find dup stores with no corresponding analytic
                    redundantDupStoreUuids = duplicateStoreUuids.stream()
                            .filter(uuid -> !analyticUuids.contains(uuid))
                            .toList();
                } else {
                    // No analytics so all redundant
                    redundantDupStoreUuids = duplicateStoreUuids;
                }

                // Delete unused duplicate stores.
                redundantDupStoreUuids.stream()
                        .map(this::deleteDuplicateStore)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .forEach(deletedUuids::add);

                if (!deletedUuids.isEmpty()) {
                    LOGGER.info("Deleted {} redundant duplicate check stores", deletedUuids.size());
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
        // Return this to ease testing
        return deletedUuids;
    }

    private Optional<String> deleteDuplicateStore(final String uuid) {
        try {
            final LmdbEnvDir lmdbEnvDir = getDir(uuid);
            lmdbEnvDir.delete();
            LOGGER.info("Deleted redundant duplicate check store with UUID: {}, path: {}",
                    uuid, LogUtil.path(lmdbEnvDir.getEnvDir()));
            return Optional.of(uuid);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message(
                    "Error deleting duplicateStore with UUID {}: {}",
                    uuid, LogUtil.exceptionMessage(e), e));
            return Optional.empty();
        }
    }
}
