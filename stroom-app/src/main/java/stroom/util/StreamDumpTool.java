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

import com.google.inject.Injector;
import stroom.persist.PersistService;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.data.store.api.StreamSource;
import stroom.data.store.api.StreamStore;
import stroom.data.meta.api.FindDataCriteria;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataMetaService;
import stroom.data.meta.api.MetaDataSource;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Handy tool to dump out content.
 */
public class StreamDumpTool extends AbstractCommandLineTool {
    private final ToolInjector toolInjector = new ToolInjector();

    private String feed;
    private String streamType;
    private String createPeriodFrom;
    private String createPeriodTo;
    private String outputDir;

    public static void main(final String[] args) {
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
        // Boot up Guice
        final Injector injector = toolInjector.getInjector();
        // Start persistance.
        injector.getInstance(PersistService.class).start();
        try {
            process(injector);
        } finally {
            // Stop persistance.
            injector.getInstance(PersistService.class).stop();
        }
    }

    private void process(final Injector injector) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);

        if (createPeriodFrom != null && !createPeriodFrom.isEmpty() && createPeriodTo != null && !createPeriodTo.isEmpty()) {
            builder.addTerm(MetaDataSource.CREATE_TIME, Condition.BETWEEN, createPeriodFrom + "," + createPeriodTo);
        } else if (createPeriodFrom != null && !createPeriodFrom.isEmpty()) {
            builder.addTerm(MetaDataSource.CREATE_TIME, Condition.GREATER_THAN_OR_EQUAL_TO, createPeriodFrom);
        } else if (createPeriodTo != null && !createPeriodTo.isEmpty()) {
            builder.addTerm(MetaDataSource.CREATE_TIME, Condition.LESS_THAN_OR_EQUAL_TO, createPeriodTo);
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
                throw new UncheckedIOException(e);
            }
        }

        final StreamStore streamStore = injector.getInstance(StreamStore.class);
        final DataMetaService streamMetaService = injector.getInstance(DataMetaService.class);

        if (feed != null) {
            builder.addTerm(MetaDataSource.FEED, Condition.EQUALS, feed);
        }

        if (streamType != null) {
            builder.addTerm(MetaDataSource.STREAM_TYPE, Condition.EQUALS, streamType);
        } else {
            builder.addTerm(MetaDataSource.STREAM_TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS);
        }

        // Query the stream store
        final FindDataCriteria criteria = new FindDataCriteria();
        criteria.setExpression(builder.build());
        final List<Data> results = streamMetaService.find(criteria);
        System.out.println("Starting dump of " + results.size() + " streams");

        int count = 0;
        for (final Data stream : results) {
            count++;
            processFile(count, results.size(), streamStore, stream.getId(), dir);
        }

        System.out.println("Finished dumping " + results.size() + " streams");
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
                } catch (final RuntimeException e) {
                    e.printStackTrace();
                } finally {
                    inputStream.close();
                }
            }
        } catch (final IOException | RuntimeException e) {
            e.printStackTrace();
        } finally {
            if (streamSource != null) {
                streamStore.closeStreamSource(streamSource);
            }
        }
    }
}
