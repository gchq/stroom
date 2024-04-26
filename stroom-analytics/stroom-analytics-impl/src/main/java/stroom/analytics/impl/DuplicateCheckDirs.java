package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbEnvDirFactory;
import stroom.query.common.v2.AnalyticResultStoreConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class DuplicateCheckDirs {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckDirs.class);

    private static final String DIR = "duplicate-check";

    private final LmdbEnvDirFactory lmdbEnvDirFactory;
    private final AnalyticResultStoreConfig analyticResultStoreConfig;

    @Inject
    public DuplicateCheckDirs(final LmdbEnvDirFactory lmdbEnvDirFactory,
                              final AnalyticResultStoreConfig analyticResultStoreConfig) {
        this.lmdbEnvDirFactory = lmdbEnvDirFactory;
        this.analyticResultStoreConfig = analyticResultStoreConfig;
    }

    public LmdbEnvDir getDir(final AnalyticRuleDoc analyticRuleDoc) {
        return getDir(analyticRuleDoc.getUuid());
    }

    public LmdbEnvDir getDir(final String analyticRuleUUID) {
        return lmdbEnvDirFactory
                .builder()
                .config(analyticResultStoreConfig.getLmdbConfig())
                .subDir(DIR + File.separator + analyticRuleUUID)
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
                    .config(analyticResultStoreConfig.getLmdbConfig())
                    .subDir(DIR)
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

    public void deleteUnused(final List<String> duplicateStoreDirs,
                             final List<AnalyticRuleDoc> analytics) {
        try {
            // Delete unused duplicate stores.
            final Set<String> remaining = new HashSet<>(duplicateStoreDirs);
            for (final AnalyticRuleDoc analyticRuleDoc : analytics) {
                remaining.remove(analyticRuleDoc.getUuid());
            }
            for (final String uuid : remaining) {
                try {
                    getDir(uuid).delete();
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }
}
