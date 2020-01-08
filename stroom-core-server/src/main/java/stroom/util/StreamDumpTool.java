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
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FindFeedCriteria;
import stroom.headless.HeadlessConfiguration;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.ProcessingUserIdentity;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.streamstore.server.StreamTypeService;
import stroom.streamstore.server.udload.StreamDownloadSettings;
import stroom.streamstore.server.udload.StreamDownloadTask;
import stroom.streamstore.server.udload.StreamDownloadTaskHandler;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamType;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.task.TaskScopeRunnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

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
    private String format;

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

    public void setFormat(final String format) {
        this.format = format;
    }

    @Override
    public void run() {
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

        try (final Stream<Path> stream = Files.list(dir)) {
            if (stream.count() > 0) {
                throw new RuntimeException("The output dir must be empty");
            }
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        if (format == null || format.trim().length() == 0) {
            System.out.println("Using default output format: \"${feed}/${pathId}/${id}\"");
            format = "${feed}/${pathId}/${id}";
        }

        // Boot up spring
        final ApplicationContext appContext = getAppContext();
        final FeedService feedService = (FeedService) appContext.getBean("cachedFeedService");
        final StreamTypeService streamTypeService = (StreamTypeService) appContext.getBean("cachedStreamTypeService");

        new TaskScopeRunnable(new StreamDownloadTask(ProcessingUserIdentity.INSTANCE, null, null, null, null)) {
            @Override
            protected void exec() {
                final StreamDownloadTaskHandler streamDownloadTaskHandler = appContext.getBean(StreamDownloadTaskHandler.class);

                if (feed == null || feed.trim().length() == 0) {
                    final FindFeedCriteria findFeedCriteria = new FindFeedCriteria();
                    findFeedCriteria.setPageRequest(null);
                    findFeedCriteria.setSort(FindFeedCriteria.FIELD_NAME);
                    final List<Feed> feedList = feedService.find(findFeedCriteria);
                    for (final Feed f : feedList) {
                        download(feedService, streamTypeService, f.getName(), streamType, createPeriodFrom, createPeriodTo, dir, format, streamDownloadTaskHandler);
                    }
                } else {
                    download(feedService, streamTypeService, feed, streamType, createPeriodFrom, createPeriodTo, dir, format, streamDownloadTaskHandler);
                }
            }
        }.run();
    }

    private void download(final FeedService feedService,
                          final StreamTypeService streamTypeService,
                          final String feedName,
                          final String streamType,
                          final String createPeriodFrom,
                          final String createPeriodTo,
                          final Path dir,
                          final String format,
                          final StreamDownloadTaskHandler streamDownloadTaskHandler) {
        System.out.println("Dumping data for " + feedName);

        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);

        if (StringUtils.isNotBlank(createPeriodFrom) && StringUtils.isNotBlank(createPeriodTo)) {
            builder.addTerm(StreamDataSource.CREATE_TIME, Condition.BETWEEN, createPeriodFrom + "," + createPeriodTo);
        } else if (StringUtils.isNotBlank(createPeriodFrom)) {
            builder.addTerm(StreamDataSource.CREATE_TIME, Condition.GREATER_THAN_OR_EQUAL_TO, createPeriodFrom);
        } else if (StringUtils.isNotBlank(createPeriodTo)) {
            builder.addTerm(StreamDataSource.CREATE_TIME, Condition.LESS_THAN_OR_EQUAL_TO, createPeriodTo);
        }

        Feed definition;
        if (feed != null) {
            definition = feedService.loadByName(feed);
            if (definition == null) {
                throw new RuntimeException("Unable to locate Feed " + feed);
            }
            builder.addTerm(StreamDataSource.FEED_NAME, Condition.EQUALS, definition.getName());
        }

        if (streamType != null) {
            final StreamType type = streamTypeService.loadByName(streamType);
            if (type == null) {
                throw new RuntimeException("Unable to locate stream type " + streamType);
            }
            builder.addTerm(StreamDataSource.STREAM_TYPE_NAME, Condition.EQUALS, type.getDisplayValue());
        }
//        else {
//            builder.addTerm(StreamDataSource.STREAM_TYPE_NAME, Condition.EQUALS, StreamType.RAW_EVENTS.getDisplayValue());
//        }

        final FindStreamCriteria criteria = new FindStreamCriteria();
        criteria.setExpression(builder.build());

        final StreamDownloadSettings streamDownloadSettings = new StreamDownloadSettings();
        streamDownloadSettings.setMultipleFiles(true);
        streamDownloadSettings.setMaxFileSize(ModelStringUtil.parseIECByteSizeString("2G"));
        streamDownloadSettings.setMaxFileParts(10000L);

        final StreamDownloadTask streamDownloadTask = new StreamDownloadTask(ProcessingUserIdentity.INSTANCE, criteria, dir, format, streamDownloadSettings);
        streamDownloadTaskHandler.exec(streamDownloadTask);

        System.out.println("Finished dumping streams");


//        final StreamStore streamStore = appContext.getBean(StreamStore.class);
//        final FeedService feedService = (FeedService) appContext.getBean("cachedFeedService");
//        final StreamTypeService streamTypeService = (StreamTypeService) appContext.getBean("cachedStreamTypeService");
//
//        new ThreadScopeRunnable() {
//            @Override
//            protected void exec() {
//                Feed definition = null;
//                if (feed != null) {
//                    definition = feedService.loadByName(feed);
//                    if (definition == null) {
//                        throw new RuntimeException("Unable to locate Feed " + feed);
//                    }
//                    criteria.obtainFeeds().obtainInclude().add(definition.getId());
//                }
//
//                if (streamType != null) {
//                    final StreamType type = streamTypeService.loadByName(streamType);
//                    if (type == null) {
//                        throw new RuntimeException("Unable to locate stream type " + streamType);
//                    }
//                    criteria.obtainStreamTypeIdSet().add(type.getId());
//                } else {
//                    criteria.obtainStreamTypeIdSet().add(StreamType.RAW_EVENTS.getId());
//                }
//
//                // Query the stream store
//                final List<Stream> results = streamStore.find(criteria);
//                System.out.println("Starting dump of " + results.size() + " streams");
//
//                int count = 0;
//                for (final Stream stream : results) {
//                    count++;
//                    processFile(count, results.size(), streamStore, stream.getId(), dir);
//                }
//
//                System.out.println("Finished dumping " + results.size() + " streams");
//            }
//        }.run();
    }

    private ApplicationContext getAppContext() {
        if (appContext == null) {
            appContext = buildAppContext();
        }
        return appContext;
    }

    private ApplicationContext buildAppContext() {
        System.setProperty("spring.profiles.active", StroomSpringProfiles.PROD + ", Headless");
        return new AnnotationConfigApplicationContext(
                ScopeConfiguration.class, PersistenceConfiguration.class,
                ServerConfiguration.class, HeadlessConfiguration.class);
    }

//    /**
//     * Scan a file
//     */
//    private void processFile(final int count, final int total, final StreamStore streamStore, final long streamId,
//                             final File outputDir) {
//        StreamSource streamSource = null;
//
//        try {
//            streamSource = streamStore.openStreamSource(streamId);
//            StreamSource metaStreamSource = streamSource.getChildStream(StreamType.META);
//            StreamSource contextStreamSource = streamSource.getChildStream(StreamType.CONTEXT);
//
//            dump(count, total, metaStreamSource, streamId, outputDir, ".meta", false);
//            dump(count, total, contextStreamSource, streamId, outputDir, ".ctx", false);
//            dump(count, total, streamSource, streamId, outputDir, ".dat", true);
//
//        } catch (final Exception ex) {
//            ex.printStackTrace();
//        } finally {
//            if (streamSource != null) {
//                streamStore.closeStreamSource(streamSource);
//            }
//        }
//    }
//
//    private void dump(final int count, final int total, final StreamSource streamSource, final long streamId,
//                      final File outputDir, final String extension, boolean close) {
//        if (streamSource != null) {
//            final InputStream inputStream = streamSource.getInputStream();
//            try {
//                final File outputFile = new File(outputDir, streamId + extension);
//                System.out.println(
//                        "Dumping stream " + count + " of " + total + " to file '" + outputFile.getName() + "'");
//                StreamUtil.streamToFile(inputStream, outputFile);
//            } catch (final Exception ex) {
//                ex.printStackTrace();
//            } finally {
//                try {
//                    if (close) {
//                        inputStream.close();
//                    }
//                } catch (final IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }
}
