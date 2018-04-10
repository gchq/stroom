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

package stroom.xml;

import org.junit.Assert;
import stroom.feed.shared.Feed;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.TextConverterStore;
import stroom.pipeline.XsltStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.parser.CombinedParser;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.writer.TestAppender;
import stroom.query.api.v2.DocRef;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

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

    @Inject
    F2XTestUtil(final PipelineFactory pipelineFactory,
                final FeedHolder feedHolder,
                final TextConverterStore textConverterStore,
                final XsltStore xsltStore,
                final ErrorReceiverProxy errorReceiverProxy,
                final RecordCount recordCount) {
        this.pipelineFactory = pipelineFactory;
        this.feedHolder = feedHolder;
        this.textConverterStore = textConverterStore;
        this.xsltStore = xsltStore;
        this.errorReceiverProxy = errorReceiverProxy;
        this.recordCount = recordCount;
    }

    /**
     * Run a XML and XSLT transform.
     */
    public String runFullTest(final Feed feed, final TextConverterType textConverterType,
                              final String textConverterLocation, final String xsltLocation, final String dataLocation,
                              final int expectedWarnings) {
        // Get the input stream.
        final InputStream in = StroomPipelineTestFileUtil.getInputStream(dataLocation);

        return runFullTest(feed, textConverterType, textConverterLocation, xsltLocation, in, expectedWarnings);
    }

    /**
     * Run a XML and XSLT transform.
     */
    private String runFullTest(final Feed feed, final TextConverterType textConverterType,
                               final String textConverterLocation, final String xsltLocation, final InputStream dataStream,
                               final int expectedWarnings) {
        // Create an output stream.
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Setup the feed.
        feedHolder.setFeed(feed);

        // Persist the text converter.
        final DocRef textConverterRef = textConverterStore.createDocument("TEST_TRANSLATION");
        final TextConverterDoc textConverterDoc = textConverterStore.readDocument(textConverterRef);
        textConverterDoc.setConverterType(textConverterType);
        textConverterDoc.setData(StroomPipelineTestFileUtil.getString(textConverterLocation));
        textConverterStore.update(textConverterDoc);

        // Persist the XSLT.
        final DocRef xsltRef = xsltStore.createDocument("TEST_TRANSLATION");
        final XsltDoc xsltDoc = xsltStore.readDocument(xsltRef);
        xsltDoc.setData(StroomPipelineTestFileUtil.getString(xsltLocation));
        xsltStore.update(xsltDoc);

        // Setup the error receiver.
        final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
        errorReceiverProxy.setErrorReceiver(loggingErrorReceiver);

        // Create the pipeline.
        final PipelineEntity pipelineEntity = PipelineTestUtil.createBasicPipeline(
                StroomPipelineTestFileUtil.getString("F2XTestUtil/f2xtest.Pipeline.data.xml"));
        final PipelineData pipelineData = pipelineEntity.getPipelineData();

        // final ElementType parserElementType = new ElementType("Parser");
        // final PropertyType textConverterPropertyType = new PropertyType(
        // parserElementType, "textConverter", "TextConverter", false);
        pipelineData.addProperty(
                PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverterRef));

        if (feed.isReference()) {
            // final ElementType schemaFilterElementType = new ElementType(
            // "SchemaFilter");
            // final PropertyType schemaGroupPropertyType = new PropertyType(
            // schemaFilterElementType, "schemaGroup", "String", false);
            pipelineData.addProperty(PipelineDataUtil.createProperty("schemaFilter", "schemaGroup", "REFERENCE_DATA"));
        } else {
            // final ElementType schemaFilterElementType = new ElementType(
            // "SchemaFilter");
            // final PropertyType schemaGroupPropertyType = new PropertyType(
            // schemaFilterElementType, "schemaGroup", "String", false);
            pipelineData.addProperty(PipelineDataUtil.createProperty("schemaFilter", "schemaGroup", "EVENTS"));
        }
        // final ElementType xsltFilterElementType = new
        // ElementType("XSLTFilter");
        // final PropertyType xsltPropertyType = new PropertyType(
        // xsltFilterElementType, "xslt", "XSLT", false);
        pipelineData.addProperty(PipelineDataUtil.createProperty("xsltFilter", "xslt", xsltRef));

        final Pipeline pipeline = pipelineFactory.create(pipelineData);

        final List<TestAppender> filters = pipeline.findFilters(TestAppender.class);
        final TestAppender testAppender = filters.get(0);
        testAppender.setOutputStream(out);

        pipeline.process(dataStream);
        Assert.assertTrue(errorReceiverProxy.toString(), recordCount.getRead() > 0);
        Assert.assertTrue(errorReceiverProxy.toString(), recordCount.getWritten() > 0);
        Assert.assertEquals(errorReceiverProxy.toString(), recordCount.getRead(), recordCount.getWritten());
        Assert.assertEquals(errorReceiverProxy.toString(), expectedWarnings,
                loggingErrorReceiver.getRecords(Severity.WARNING));
        Assert.assertEquals(errorReceiverProxy.toString(), 0, loggingErrorReceiver.getRecords(Severity.ERROR));
        Assert.assertEquals(errorReceiverProxy.toString(), 0, loggingErrorReceiver.getRecords(Severity.FATAL_ERROR));

        if (!loggingErrorReceiver.isAllOk()) {
            Assert.fail(errorReceiverProxy.toString());
        }

        return out.toString();
    }

    /**
     * Run a XML transform.
     */
    public String runF2XTest(final TextConverterType textConverterType, final String textConverterLocation,
                             final InputStream inputStream) {
        // Persist the text converter.
        final DocRef docRef = textConverterStore.createDocument("TEST_TRANSLATION");
        TextConverterDoc textConverter = textConverterStore.readDocument(docRef);
        textConverter.setConverterType(textConverterType);
        textConverter.setData(StroomPipelineTestFileUtil.getString(textConverterLocation));
        textConverterStore.update(textConverter);

        // Setup the error receiver.
        final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
        errorReceiverProxy.setErrorReceiver(loggingErrorReceiver);

        // Create the pipeline.
        final PipelineEntity pipelineEntity = PipelineTestUtil.createBasicPipeline(
                StroomPipelineTestFileUtil.getString("F2XTestUtil/f2xtest.Pipeline.data.xml"));
        final PipelineData pipelineData = pipelineEntity.getPipelineData();

        // final ElementType parserElementType = new ElementType("Parser");
        // final PropertyType textConverterPropertyType = new PropertyType(
        // parserElementType, "textConverter", "TextConverter", false);
        pipelineData.addProperty(
                PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", docRef));

        // final ElementType schemaFilterElementType = new ElementType(
        // "SchemaFilter");
        // final PropertyType schemaGroupPropertyType = new PropertyType(
        // schemaFilterElementType, "schemaGroup", "String", false);
        pipelineData.addProperty(PipelineDataUtil.createProperty("schemaFilter", "schemaGroup", "RECORDS"));

        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        final PipelineData mergedPipelineData = new PipelineDataMerger().merge(pipelineData).createMergedData();
        final Pipeline pipeline = pipelineFactory.create(mergedPipelineData);

        final List<TestAppender> filters = pipeline.findFilters(TestAppender.class);
        final TestAppender testAppender = filters.get(0);
        testAppender.setOutputStream(out);

        pipeline.process(inputStream);

        if (!loggingErrorReceiver.isAllOk()) {
            Assert.fail(errorReceiverProxy.toString());
        }

        return out.toString();
    }
}
