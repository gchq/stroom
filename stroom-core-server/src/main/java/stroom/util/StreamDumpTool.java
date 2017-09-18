/*
 * Copyright 2017 Crown Copyright
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

package stroom.util;

import org.apache.commons.lang.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import stroom.entity.shared.Period;
import stroom.feed.server.FeedServiceImpl;
import stroom.feed.shared.Feed;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ServerComponentScanConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTypeServiceImpl;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.spring.StroomSpringProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Handy tool to dump out content.
 */
public class StreamDumpTool extends AbstractCommandLineTool {
    private ApplicationContext appContext = null;

    private String feed;
    private String streamType;
    private String createPeriodFrom;
    private String createPeriodTo;
    private String outputDir;

    public static void main(final String[] args) throws Exception {
        new StreamDumpTool().doMain(args);
    }

    public void setFeed(final String feed) {
        this.feed = feed;
    }

    public void setStreamType(final String streamType) {
        this.streamType = streamType;
    }

    public void setCreatePeriodFrom(final String createPeriodFrom) {
        this.createPeriodFrom = createPeriodFrom;
    }

    public void setCreatePeriodTo(final String createPeriodTo) {
        this.createPeriodTo = createPeriodTo;
    }

    public void setOutputDir(final String outputDir) {
        this.outputDir = outputDir;
    }

    @Override
    public void run() {
        // Boot up spring
        final ApplicationContext appContext = getAppContext();

        final FindStreamCriteria criteria = new FindStreamCriteria();

        Long createPeriodFromMs = null;
        if (StringUtils.isNotBlank(createPeriodFrom)) {
            createPeriodFromMs = DateUtil.parseNormalDateTimeString(createPeriodFrom);
        }
        Long createPeriodToMs = null;
        if (StringUtils.isNotBlank(createPeriodTo)) {
            createPeriodToMs = DateUtil.parseNormalDateTimeString(createPeriodTo);
        }

        if (outputDir == null || outputDir.length() == 0) {
            throw new RuntimeException("Output directory must be specified");
        }

        final Path dir = Paths.get(outputDir);
        if (!Files.isDirectory(dir)) {
            System.out.println("Creating directory '" + outputDir + "'");
            try {
                Files.createDirectories(dir);
            } catch (final IOException e) {
                throw new RuntimeException("Unable to create output directory '" + outputDir + "'");
            }
        }

        criteria.setCreatePeriod(new Period(createPeriodFromMs, createPeriodToMs));

        final StreamStore streamStore = appContext.getBean(StreamStore.class);
        final FeedServiceImpl feedService = appContext.getBean(FeedServiceImpl.class);
        final StreamTypeServiceImpl streamTypeService = appContext.getBean(StreamTypeServiceImpl.class);

        Feed definition = null;
        if (feed != null) {
            definition = feedService.loadByName(feed);
            if (definition == null) {
                throw new RuntimeException("Unable to locate Feed " + feed);
            }
            criteria.obtainFeeds().obtainInclude().add(definition.getId());
        }

        if (streamType != null) {
            final StreamType type = streamTypeService.loadByName(streamType);
            if (type == null) {
                throw new RuntimeException("Unable to locate stream type " + streamType);
            }
            criteria.obtainStreamTypeIdSet().add(type.getId());
        } else {
            criteria.obtainStreamTypeIdSet().add(StreamType.RAW_EVENTS.getId());
        }

        // Query the stream store
        final List<Stream> results = streamStore.find(criteria);
        System.out.println("Starting dump of " + results.size() + " streams");

        int count = 0;
        for (final Stream stream : results) {
            count++;
            processFile(count, results.size(), streamStore, stream.getId(), dir);
        }

        System.out.println("Finished dumping " + results.size() + " streams");
    }

    private ApplicationContext getAppContext() {
        if (appContext == null) {
            appContext = buildAppContext();
        }
        return appContext;
    }

    private ApplicationContext buildAppContext() {
        System.setProperty("spring.profiles.active", StroomSpringProfiles.PROD);
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ScopeConfiguration.class, PersistenceConfiguration.class, ServerComponentScanConfiguration.class,
                ServerConfiguration.class);
        return context;
    }

    /**
     * Scan a file
     */
    private void processFile(final int count, final int total, final StreamStore streamStore, final long streamId,
                             final Path outputDir) {
        StreamSource streamSource = null;
        try {
            streamSource = streamStore.openStreamSource(streamId);
            if (streamSource != null) {
                InputStream inputStream = null;
                try {
                    inputStream = streamSource.getInputStream();
                    final Path outputFile = outputDir.resolve(streamId + ".dat");
                    System.out.println(
                            "Dumping stream " + count + " of " + total + " to file '" + FileUtil.getCanonicalPath(outputFile) + "'");
                    StreamUtil.streamToFile(inputStream, outputFile);
                } catch (final Exception ex) {
                    ex.printStackTrace();
                } finally {
                    inputStream.close();
                }
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
        } finally {
            if (streamSource != null) {
                streamStore.closeStreamSource(streamSource);
            }
        }
    }
}
