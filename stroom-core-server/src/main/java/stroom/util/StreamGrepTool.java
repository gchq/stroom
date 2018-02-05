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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTypeService;
import stroom.streamstore.server.fs.FileSystemStreamTypeUtil;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamType;
import stroom.util.io.StreamUtil;
import stroom.util.spring.StroomSpringProfiles;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Handy tool to grep out content.
 */
public class StreamGrepTool extends AbstractCommandLineTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamGrepTool.class);

    private ApplicationContext appContext = null;

    private String feed;
    private String streamType;
    private String match;
    private String createPeriodFrom;
    private String createPeriodTo;
    private String addLineBreak;

    private static void checkMatch(final String match, final ArrayDeque<String> preBuffer,
                                   final ArrayDeque<String> postBuffer, final String searchLine) {
        if (searchLine.toLowerCase().contains(match)) {
            final StringBuilder matchArea = new StringBuilder();
            for (final String preBufferLine : preBuffer) {
                matchArea.append(preBufferLine);
                matchArea.append("\n");

            }
            matchArea.append(">>");
            matchArea.append(searchLine);
            matchArea.append("\n");
            for (final String postBufferLine : postBuffer) {
                matchArea.append(postBufferLine);
                matchArea.append("\n");
            }

            LOGGER.info("foundMatch() " + match + "\n\n" + matchArea.toString() + "\n\n");
        }
    }

    public static void main(final String[] args) throws Exception {
        new StreamGrepTool().doMain(args);
    }

    public void setFeed(final String feed) {
        this.feed = feed;
    }

    public void setStreamType(final String streamType) {
        this.streamType = streamType;
    }

    public void setMatch(final String match) {
        this.match = match;
    }

    public void setCreatePeriodFrom(final String createPeriodFrom) {
        this.createPeriodFrom = createPeriodFrom;
    }

    public void setCreatePeriodTo(final String createPeriodTo) {
        this.createPeriodTo = createPeriodTo;
    }

    public void setAddLineBreak(final String addLineBreak) {
        this.addLineBreak = addLineBreak;
    }

    @Override
    public void run() {
        // Boot up spring
        final ApplicationContext appContext = getAppContext();

        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);

        String createStartTime = null;
        if (StringUtils.isNotBlank(createPeriodFrom)) {
            createStartTime = createPeriodFrom;
        }
        String createEndTime = null;
        if (StringUtils.isNotBlank(createPeriodTo)) {
            createEndTime = createPeriodTo;
        }

        builder.addTerm(StreamDataSource.CREATE_TIME, Condition.BETWEEN, createStartTime + "," + createEndTime);

        final StreamStore streamStore = appContext.getBean(StreamStore.class);
        final FeedService feedService = (FeedService) appContext.getBean("cachedFeedService");
        final StreamTypeService streamTypeService = (StreamTypeService) appContext.getBean("cachedStreamTypeService");

        Feed definition = null;
        if (feed != null) {
            definition = feedService.loadByName(feed);
            if (definition == null) {
                throw new RuntimeException("Unable to locate Feed " + feed);
            }
            builder.addTerm(StreamDataSource.FEED, Condition.EQUALS, definition.getName());
        }

        if (streamType != null) {
            final StreamType type = streamTypeService.loadByName(streamType);
            if (type == null) {
                throw new RuntimeException("Unable to locate stream type " + streamType);
            }
            builder.addTerm(StreamDataSource.STREAM_TYPE, Condition.EQUALS, type.getDisplayValue());
        } else {
            builder.addTerm(StreamDataSource.STREAM_TYPE, Condition.EQUALS, StreamType.RAW_EVENTS.getDisplayValue());
        }

        // Query the stream store
        final FindStreamCriteria criteria = new FindStreamCriteria();
        criteria.setExpression(builder.build());
        final List<Stream> results = streamStore.find(criteria);

        int count = 0;
        for (final Stream stream : results) {
            final StreamType streamType = streamTypeService.load(stream.getStreamType());
            count++;
            LOGGER.info("processing() - " + count + "/" + results.size() + " "
                    + FileSystemStreamTypeUtil.getDirectory(stream, streamType) + " "
                    + FileSystemStreamTypeUtil.getBaseName(stream));

            processFile(streamStore, stream.getId(), match);
        }
    }

    private ApplicationContext getAppContext() {
        if (appContext == null) {
            appContext = buildAppContext();
        }
        return appContext;
    }

    private ApplicationContext buildAppContext() {
        System.setProperty("spring.profiles.active", StroomSpringProfiles.PROD + ", Headless");
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ScopeConfiguration.class, PersistenceConfiguration.class,
                ServerConfiguration.class, HeadlessConfiguration.class);
        return context;
    }

    private void processFile(final StreamStore streamStore, final long streamId, final String match) {
        try {
            final StreamSource streamSource = streamStore.openStreamSource(streamId);
            if (streamSource != null) {
                final InputStream inputStream = streamSource.getInputStream();

                // Build up 2 buffers so we can output the content either side
                // of
                // the matching line
                final ArrayDeque<String> preBuffer = new ArrayDeque<>();
                final ArrayDeque<String> postBuffer = new ArrayDeque<>();

                final LineNumberReader lineNumberReader = new LineNumberReader(
                        new InputStreamReader(inputStream, StreamUtil.DEFAULT_CHARSET));

                String aline = null;
                while ((aline = lineNumberReader.readLine()) != null) {
                    String lines[] = new String[]{aline};
                    if (addLineBreak != null) {
                        lines = aline.split(addLineBreak);

                    }

                    for (final String line : lines) {
                        if (match == null) {
                            System.out.println(lineNumberReader.getLineNumber() + ":" + line);
                        } else {
                            postBuffer.add(lineNumberReader.getLineNumber() + ":" + line);

                            if (postBuffer.size() > 5) {
                                final String searchLine = postBuffer.pop();

                                checkMatch(match, preBuffer, postBuffer, searchLine);

                                preBuffer.add(searchLine);

                                if (preBuffer.size() > 5) {
                                    preBuffer.pop();
                                }
                            }
                        }
                    }

                }

                // Look at the end
                while (postBuffer.size() > 0) {
                    final String searchLine = postBuffer.pop();

                    checkMatch(match, preBuffer, postBuffer, searchLine);

                    preBuffer.add(searchLine);

                    if (preBuffer.size() > 5) {
                        preBuffer.pop();
                    }
                }

                inputStream.close();
                streamStore.closeStreamSource(streamSource);
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
        }

    }
}
