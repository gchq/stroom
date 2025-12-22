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

package stroom.data.store.util;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.AbstractCommandLineTool;
import stroom.util.io.StreamUtil;

import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    private final ToolInjector toolInjector;

    private String feed;
    private String streamType;
    private String match;
    private String createPeriodFrom;
    private String createPeriodTo;
    private String addLineBreak;

    public StreamGrepTool() {
        toolInjector = new ToolInjector();
    }

    // for testing
    StreamGrepTool(final ToolInjector toolInjector) {
        this.toolInjector = toolInjector;
    }

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

    public static void main(final String[] args) {
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
        // Boot up Guice
        process(toolInjector.getInjector());
    }

    private void process(final Injector injector) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder();

        if (createPeriodFrom != null
                && !createPeriodFrom.isEmpty()
                && createPeriodTo != null
                && !createPeriodTo.isEmpty()) {
            builder.addDateTerm(MetaFields.CREATE_TIME, Condition.BETWEEN, createPeriodFrom + "," + createPeriodTo);
        } else if (createPeriodFrom != null && !createPeriodFrom.isEmpty()) {
            builder.addDateTerm(MetaFields.CREATE_TIME, Condition.GREATER_THAN_OR_EQUAL_TO, createPeriodFrom);
        } else if (createPeriodTo != null && !createPeriodTo.isEmpty()) {
            builder.addDateTerm(MetaFields.CREATE_TIME, Condition.LESS_THAN_OR_EQUAL_TO, createPeriodTo);
        }

        final MetaService metaService = injector.getInstance(MetaService.class);
        final Store streamStore = injector.getInstance(Store.class);

        if (feed != null) {
            builder.addTextTerm(MetaFields.FEED, Condition.EQUALS, feed);
        }

        if (streamType != null) {
            builder.addTextTerm(MetaFields.TYPE, Condition.EQUALS, streamType);
        } else {
            builder.addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS);
        }

        // Query the stream store
        final FindMetaCriteria criteria = new FindMetaCriteria();
        criteria.setExpression(builder.build());
        final List<Meta> results = metaService.find(criteria).getValues();

        int count = 0;
        for (final Meta meta : results) {
            count++;
            LOGGER.info("processing() - " + count + "/" + results.size() + " " + meta);

            processFile(streamStore, meta.getId(), match);
        }
    }

    private void processFile(final Store streamStore, final long streamId, final String match) {
        try (final Source streamSource = streamStore.openSource(streamId)) {
            try (final InputStreamProvider inputStreamProvider = streamSource.get(0)) {
                try (final InputStream inputStream = inputStreamProvider.get()) {

                    // Build up 2 buffers so we can output the content either side
                    // of
                    // the matching line
                    final ArrayDeque<String> preBuffer = new ArrayDeque<>();
                    final ArrayDeque<String> postBuffer = new ArrayDeque<>();

                    final LineNumberReader lineNumberReader = new LineNumberReader(
                            new InputStreamReader(inputStream, StreamUtil.DEFAULT_CHARSET));

                    String aline;
                    while ((aline = lineNumberReader.readLine()) != null) {
                        String[] lines = {aline};
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
                }
            }
        } catch (final IOException | RuntimeException e) {
            e.printStackTrace();
        }
    }
}
