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

package stroom.pipeline.xml;


import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.parser.CombinedParser;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.textconverter.TextConverterStore;
import stroom.pipeline.writer.TestAppender;
import stroom.pipeline.xslt.XsltStore;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * <p>
 * Reusable Functions for the tests.
 * </p>
 */

public class F2XTestUtil {

    private final PipelineFactory pipelineFactory;
    private final FeedHolder feedHolder;
    private final TextConverterStore textConverterStore;
    private final XsltStore xsltStore;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final RecordCount recordCount;
    private final TaskContextFactory taskContextFactory;

    @Inject
    F2XTestUtil(final PipelineFactory pipelineFactory,
                final FeedHolder feedHolder,
                final TextConverterStore textConverterStore,
                final XsltStore xsltStore,
                final ErrorReceiverProxy errorReceiverProxy,
                final RecordCount recordCount,
                final TaskContextFactory taskContextFactory) {
        this.pipelineFactory = pipelineFactory;
        this.feedHolder = feedHolder;
        this.textConverterStore = textConverterStore;
        this.xsltStore = xsltStore;
        this.errorReceiverProxy = errorReceiverProxy;
        this.recordCount = recordCount;
        this.taskContextFactory = taskContextFactory;
    }

    /**
     * Run a XML and XSLT transform.
     */
    public String runFullTest(final FeedDoc feed,
                              final TextConverterType textConverterType,
                              final String textConverterLocation,
                              final String xsltLocation,
                              final String dataLocation,
                              final int expectedWarnings) {
        // Get the input stream.
        final InputStream in = StroomPipelineTestFileUtil.getInputStream(dataLocation);

        return taskContextFactory.contextResult("F2XTestUtil", taskContext ->
                runFullTest(feed,
                        textConverterType,
                        textConverterLocation,
                        xsltLocation,
                        in,
                        expectedWarnings,
                        taskContext)).get();
    }

    /**
     * Run a XML and XSLT transform.
     */
    private String runFullTest(final FeedDoc feed,
                               final TextConverterType textConverterType,
                               final String textConverterLocation,
                               final String xsltLocation,
                               final InputStream dataStream,
                               final int expectedWarnings,
                               final TaskContext taskContext) {
        // Create an output stream.
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Setup the feed.
        feedHolder.setFeedName(feed.getName());

//        // Setup the meta data holder.
//        metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(
//        metaHolder, streamProcessorService, pipelineStore));

        // Persist the text converter.
        final DocRef textConverterRef = textConverterStore.createDocument("TEST_TRANSLATION");
        final TextConverterDoc textConverterDoc = textConverterStore.readDocument(textConverterRef);
        textConverterDoc.setConverterType(textConverterType);
        textConverterDoc.setData(StroomPipelineTestFileUtil.getString(textConverterLocation));
        textConverterStore.writeDocument(textConverterDoc);

        // Persist the XSLT.
        final DocRef xsltRef = xsltStore.createDocument("TEST_TRANSLATION");
        final XsltDoc xsltDoc = xsltStore.readDocument(xsltRef);
        xsltDoc.setData(StroomPipelineTestFileUtil.getString(xsltLocation));
        xsltStore.writeDocument(xsltDoc);

        // Setup the error receiver.
        final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
        errorReceiverProxy.setErrorReceiver(loggingErrorReceiver);

        // Create the pipeline.
        final PipelineDoc pipelineDoc = PipelineTestUtil.createBasicPipeline(
                StroomPipelineTestFileUtil.getString("F2XTestUtil/f2xtest.Pipeline.json"));
        final PipelineData pipelineData = pipelineDoc.getPipelineData();
        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineData);

        // final ElementType parserElementType = new ElementType("Parser");
        // final PropertyType textConverterPropertyType = new PropertyType(
        // parserElementType, "textConverter", "TextConverter", false);
        builder.addProperty(
                PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverterRef));

        if (feed.isReference()) {
            // final ElementType schemaFilterElementType = new ElementType(
            // "SchemaFilter");
            // final PropertyType schemaGroupPropertyType = new PropertyType(
            // schemaFilterElementType, "schemaGroup", "String", false);
            builder.addProperty(PipelineDataUtil.createProperty("schemaFilter", "schemaGroup", "REFERENCE_DATA"));
        } else {
            // final ElementType schemaFilterElementType = new ElementType(
            // "SchemaFilter");
            // final PropertyType schemaGroupPropertyType = new PropertyType(
            // schemaFilterElementType, "schemaGroup", "String", false);
            builder.addProperty(PipelineDataUtil.createProperty("schemaFilter", "schemaGroup", "EVENTS"));
        }
        // final ElementType xsltFilterElementType = new
        // ElementType("XSLTFilter");
        // final PropertyType xsltPropertyType = new PropertyType(
        // xsltFilterElementType, "xslt", "XSLT", false);
        builder.addProperty(PipelineDataUtil.createProperty("xsltFilter", "xslt", xsltRef));

        final Pipeline pipeline = pipelineFactory.create(builder.build(), taskContext);

        final List<TestAppender> filters = pipeline.findFilters(TestAppender.class);
        final TestAppender testAppender = filters.getFirst();
        testAppender.setOutputStream(out);

        pipeline.process(dataStream);
        assertThat(recordCount.getRead() > 0)
                .as(errorReceiverProxy.toString())
                .isTrue();
        assertThat(recordCount.getWritten() > 0)
                .as(errorReceiverProxy.toString())
                .isTrue();
        assertThat(recordCount.getWritten())
                .as(errorReceiverProxy.toString())
                .isEqualTo(recordCount.getRead());
        assertThat(loggingErrorReceiver.getRecords(Severity.WARNING))
                .as(errorReceiverProxy.toString())
                .isEqualTo(expectedWarnings);
        assertThat(loggingErrorReceiver.getRecords(Severity.ERROR))
                .as(errorReceiverProxy.toString())
                .isEqualTo(0);
        assertThat(loggingErrorReceiver.getRecords(Severity.FATAL_ERROR))
                .as(errorReceiverProxy.toString())
                .isEqualTo(0);

        if (!loggingErrorReceiver.isAllOk()) {
            fail(errorReceiverProxy.toString());
        }

        return out.toString();
    }

    /**
     * Run a XML transform.
     */
    public String runF2XTest(final TextConverterType textConverterType,
                             final String textConverterLocation,
                             final InputStream inputStream) {
        return taskContextFactory.contextResult("F2XTestUtil", taskContext -> {
            // Persist the text converter.
            final DocRef docRef = textConverterStore.createDocument("TEST_TRANSLATION");
            final TextConverterDoc textConverter = textConverterStore.readDocument(docRef);
            textConverter.setConverterType(textConverterType);
            textConverter.setData(StroomPipelineTestFileUtil.getString(textConverterLocation));
            textConverterStore.writeDocument(textConverter);

            // Setup the error receiver.
            final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
            errorReceiverProxy.setErrorReceiver(loggingErrorReceiver);

            // Create the pipeline.
            final PipelineDoc pipelineDoc = PipelineTestUtil.createBasicPipeline(
                    StroomPipelineTestFileUtil.getString("F2XTestUtil/f2xtest.Pipeline.json"));
            final DocRef pipelineDocRef = DocRefUtil.create(pipelineDoc);
            final PipelineData pipelineData = pipelineDoc.getPipelineData();
            final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineData);

            // final ElementType parserElementType = new ElementType("Parser");
            // final PropertyType textConverterPropertyType = new PropertyType(
            // parserElementType, "textConverter", "TextConverter", false);
            builder.addProperty(
                    PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", docRef));

            // final ElementType schemaFilterElementType = new ElementType(
            // "SchemaFilter");
            // final PropertyType schemaGroupPropertyType = new PropertyType(
            // schemaFilterElementType, "schemaGroup", "String", false);
            builder.addProperty(PipelineDataUtil.createProperty(
                    "schemaFilter", "schemaGroup", "RECORDS"));

            final ByteArrayOutputStream out = new ByteArrayOutputStream();

            final PipelineData mergedPipelineData = new PipelineDataMerger()
                    .merge(new PipelineLayer(pipelineDocRef, builder.build()))
                    .createMergedData();
            final Pipeline pipeline = pipelineFactory.create(mergedPipelineData, taskContext);

            final List<TestAppender> filters = pipeline.findFilters(TestAppender.class);
            final TestAppender testAppender = filters.getFirst();
            testAppender.setOutputStream(out);

            pipeline.process(inputStream);

            if (!loggingErrorReceiver.isAllOk()) {
                fail(errorReceiverProxy.toString());
            }

            return out.toString();
        }).get();
    }
}
