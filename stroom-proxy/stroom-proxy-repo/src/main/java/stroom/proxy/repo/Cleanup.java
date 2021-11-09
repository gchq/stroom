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

import stroom.proxy.repo.dao.AggregateDao;
import stroom.proxy.repo.dao.ForwardAggregateDao;
import stroom.proxy.repo.dao.ForwardSourceDao;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.util.io.FileUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Cleanup {

    private static final Logger LOGGER = LoggerFactory.getLogger(Cleanup.class);

    private final SourceDao sourceDao;
    private final SourceItemDao sourceItemDao;
    private final AggregateDao aggregateDao;
    private final ForwardSourceDao forwardSourceDao;
    private final ForwardAggregateDao forwardAggregateDao;

    private final RepoSources sources;
    private final Path repoDir;

    @Inject
    Cleanup(final RepoSources sources,
            final RepoDirProvider repoDirProvider,
            final SourceDao sourceDao,
            final SourceItemDao sourceItemDao,
            final AggregateDao aggregateDao,
            final ForwardSourceDao forwardSourceDao,
            final ForwardAggregateDao forwardAggregateDao) {
        this.sources = sources;
        this.repoDir = repoDirProvider.get();
        this.sourceDao = sourceDao;
        this.sourceItemDao = sourceItemDao;
        this.aggregateDao = aggregateDao;
        this.forwardSourceDao = forwardSourceDao;
        this.forwardAggregateDao = forwardAggregateDao;
    }

    public void cleanupSources() {
        final List<RepoSource> list = sources.getDeletableSources();
        for (final RepoSource source : list) {
            try {
                // Source path is the zip.
                final Path sourceFile = repoDir.resolve(ProxyRepoFileNames.getZip(source.getSourcePath()));
                LOGGER.debug("Deleting: " + FileUtil.getCanonicalPath(sourceFile));
                Files.deleteIfExists(sourceFile);

                final Path metaFile = repoDir.resolve(ProxyRepoFileNames.getMeta(source.getSourcePath()));
                LOGGER.debug("Deleting: " + FileUtil.getCanonicalPath(metaFile));
                Files.deleteIfExists(metaFile);

                sources.deleteSource(source);

            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    public void resetAggregateForwarder() {
        forwardAggregateDao.clear();
        sourceItemDao.clear();
        aggregateDao.clear();
        sourceDao.resetExamined();
    }

    public void resetSourceForwarder() {
        forwardSourceDao.clear();
    }
}
