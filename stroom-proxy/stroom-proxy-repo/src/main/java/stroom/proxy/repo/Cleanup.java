/*
 * Copyright 2019 Crown Copyright
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

package stroom.proxy.repo;

import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceDao.Source;
import stroom.proxy.repo.dao.SourceEntryDao;
import stroom.util.io.FileUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Cleanup {

    private static final Logger LOGGER = LoggerFactory.getLogger(Cleanup.class);
    private static final int BATCH_SIZE = 1000000;

    private final SourceDao sourceDao;
    private final SourceEntryDao sourceEntryDao;
    private final Path repoDir;

    @Inject
    Cleanup(final SourceDao sourceDao,
            final SourceEntryDao sourceEntryDao,
            final RepoDirProvider repoDirProvider) {
        this.sourceDao = sourceDao;
        this.sourceEntryDao = sourceEntryDao;
        this.repoDir = repoDirProvider.get();
    }

    public synchronized int deleteUnusedSourceEntries() {
        return sourceEntryDao.deleteUnused();
    }

    public int deleteUnusedSources() {
        final AtomicInteger total = new AtomicInteger();

        boolean run = true;
        while (run) {
            final List<Source> sources = sourceDao.getDeletableSources(BATCH_SIZE);
            sources.forEach(source -> {
                try {
                    // Source path is the zip.
                    final Path sourceFile = repoDir.resolve(ProxyRepoFileNames.getZip(source.getSourcePath()));
                    LOGGER.debug("Deleting: " + FileUtil.getCanonicalPath(sourceFile));
                    Files.deleteIfExists(sourceFile);

                    final Path metaFile = repoDir.resolve(ProxyRepoFileNames.getMeta(source.getSourcePath()));
                    LOGGER.debug("Deleting: " + FileUtil.getCanonicalPath(metaFile));
                    Files.deleteIfExists(metaFile);

                    final int count = sourceDao.deleteSource(source.getSourceId());
                    total.addAndGet(count);

                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            });

            // Stop deleting if the last query did not return a result as big as the batch size.
            if (sources.size() < BATCH_SIZE || Thread.currentThread().isInterrupted()) {
                run = false;
            }
        }

        return total.get();
    }
}
