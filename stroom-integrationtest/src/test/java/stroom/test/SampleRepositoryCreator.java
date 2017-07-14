/*
 * Copyright 2016 Crown Copyright
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

package stroom.test;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.feed.shared.FeedService;
import stroom.importexport.server.ImportExportSerializer;
import stroom.node.server.NodeCache;
import stroom.proxy.repo.ProxyRepositoryCreator;
import stroom.proxy.repo.StroomZipRepository;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.io.FileUtil;
import stroom.util.spring.StroomSpringProfiles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Script to create some base data for testing.
 */
public final class SampleRepositoryCreator {
    private final ApplicationContext appContext;
    private final NodeCache nodeCache;
    private final FeedService feedService;
    private final CommonTestControl commonTestControl;
    private final ImportExportSerializer importExportSerializer;
    private final File testDir;

    public ApplicationContext getAppContext() {
        return appContext;
    }

    public SampleRepositoryCreator() {
        FileSystemUtil.deleteContents(FileUtil.getTempDir());

        System.setProperty("spring.profiles.active", StroomSpringProfiles.TEST);
        final String[] context = new String[]{"classpath:META-INF/spring/stroomCoreServerContext.xml",
                "classpath:META-INF/spring/stroomDatabaseCommonTestControl.xml",
                "classpath:META-INF/spring/stroomCoreServerLocalTestingContext.xml"};
        appContext = new ClassPathXmlApplicationContext(context);

        nodeCache = (NodeCache) appContext.getBean("nodeCache");

        // Force nodes to be created
        nodeCache.getDefaultNode();

        feedService = (FeedService) appContext.getBean("feedService");

        commonTestControl = (CommonTestControl) appContext.getBean("commonTestControl");

        importExportSerializer = appContext.getBean(ImportExportSerializer.class);

        testDir = new File(StroomCoreServerTestFileUtil.getTestResourcesDir(), "samples/config");
    }

    public void run(final boolean shutdown) throws IOException {
        // Load config.
        importExportSerializer.read(testDir.toPath(), null, ImportMode.IGNORE_CONFIRMATION);

        final File repoDir = new File(StroomCoreServerTestFileUtil.getTestResourcesDir(), "SampleRepositoryCreator/repo");
        repoDir.mkdirs();
        FileSystemUtil.deleteContents(repoDir);

        final StroomZipRepository repository = new StroomZipRepository(repoDir.getAbsolutePath());

        // Add data.
        final ProxyRepositoryCreator creator = new ProxyRepositoryCreator(feedService, repository);

        // We spread the received time over 10 min intervals to help test repo
        // layout start 2 weeks ago.
        final long dayMs = 1000 * 60 * 60 * 24;
        final long tenMinMs = 1000 * 60 * 10;
        long startTime = System.currentTimeMillis() - (14 * dayMs);

        // Load each data item 5 times to create a reasonable amount to test.
        final Path p = testDir.toPath();
        for (int i = 0; i < 5; i++) {
            // Load reference data first.
            creator.read(p, true, startTime);
            startTime += tenMinMs;

            // Then load event data.
            creator.read(p, false, startTime);
            startTime += tenMinMs;
        }

        if (shutdown) {
            commonTestControl.shutdown();
        }
    }

    /**
     * Main.
     *
     * @param args NA
     * @throws Exception NA
     */
    public static void main(final String[] args) throws Exception {
        final SampleRepositoryCreator setupSampleData = new SampleRepositoryCreator();
        setupSampleData.run(true);
    }
}
