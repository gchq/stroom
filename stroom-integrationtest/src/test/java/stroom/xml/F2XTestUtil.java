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
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.feed.shared.Feed;
import stroom.pipeline.server.PipelineTestUtil;
import stroom.pipeline.server.TextConverterService;
import stroom.pipeline.server.XSLTService;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.server.factory.Pipeline;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.server.parser.CombinedParser;
import stroom.pipeline.server.writer.TestAppender;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.RecordCount;
import stroom.streamstore.server.StreamStore;
import stroom.test.StroomProcessTestFileUtil;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * <p>
 * Reusable Functions for the tests.
 * </p>
 */
@Component
@Scope(StroomScope.TASK)
public class F2XTestUtil {
    @Resource
    private PipelineFactory pipelineFactory;
    @Resource
    private FeedHolder feedHolder;
    @Resource
    private TextConverterService textConverterService;
    @Resource
    private XSLTService xsltService;
    @Resource
    private ErrorReceiverProxy errorReceiverProxy;
    @Resource
    private RecordCount recordCount;
    @Resource
    private StreamStore streamStore;

    /**
     * <p>
     * Run a XML and XSLT transform.
     * </p>
     *
     * @param resourceFinder NA
     * @param xmlValidator   NA
     * @param parserFactory  NA
     * @param formatLocation NA
     * @param xsltLocation   NA
     * @param dataLocation   NA
     * @return xml
     */
    public String runFullTest(final Feed feed, final TextConverterType textConverterType,
                              final String textConverterLocation, final String xsltLocation, final String dataLocation,
                              final int expectedWarnings) {
        // Get the input stream.
        final InputStream in = StroomProcessTestFileUtil.getInputStream(dataLocation);

        return runFullTest(feed, textConverterType, textConverterLocation, xsltLocation, in, expectedWarnings);
    }

    /**
     * <p>
     * Run a XML and XSLT transform.
     * </p>
     *
     * @param resourceFinder NA
     * @param xmlValidator   NA
     * @param parserFactory  NA
     * @param formatLocation NA
     * @param xsltLocation   NA
     * @param dataStream     NA
     * @return xml
     */
    public String runFullTest(final Feed feed, final TextConverterType textConverterType,
                              final String textConverterLocation, final String xsltLocation, final InputStream dataStream,
                              final int expectedWarnings) {
        // Create an output stream.
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Setup the feed.
        feedHolder.setFeed(feed);

        // Persist the text converter.
        TextConverter textConverter = new TextConverter();
        textConverter.setName("TEST_TRANSLATION");
        textConverter.setConverterType(textConverterType);
        textConverter.setData(StroomProcessTestFileUtil.getString(textConverterLocation));
        textConverter = textConverterService.save(textConverter);

        // Persist the XSLT.
        XSLT xslt = new XSLT();
        xslt.setName("TEST_TRANSLATION");
        xslt.setData(StroomProcessTestFileUtil.getString(xsltLocation));
        xslt = xsltService.save(xslt);

        // Setup the error receiver.
        final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
        errorReceiverProxy.setErrorReceiver(loggingErrorReceiver);

        // Create the pipeline.
        final PipelineEntity pipelineEntity = PipelineTestUtil.createBasicPipeline(
                StroomProcessTestFileUtil.getString("F2XTestUtil/f2xtest.Pipeline.data.xml"));
        final PipelineData pipelineData = pipelineEntity.getPipelineData();

        // final ElementType parserElementType = new ElementType("Parser");
        // final PropertyType textConverterPropertyType = new PropertyType(
        // parserElementType, "textConverter", "TextConverter", false);
        pipelineData.addProperty(
                PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverter));

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
        pipelineData.addProperty(PipelineDataUtil.createProperty("xsltFilter", "xslt", xslt));

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
     * <p>
     * Run a XML transform.
     * </p>
     *
     * @param resourceFinder NA
     * @param xmlValidator   NA
     * @param parserFactory  NA
     * @param formatLocation NA
     * @param dataStream     NA
     * @return dataStream
     */
    public String runF2XTest(final TextConverterType textConverterType, final String textConverterLocation,
                             final InputStream inputStream) {
        // Persist the text converter.
        TextConverter textConverter = textConverterService.create("TEST_TRANSLATION");
        textConverter.setConverterType(textConverterType);
        textConverter.setData(StroomProcessTestFileUtil.getString(textConverterLocation));
        textConverter = textConverterService.save(textConverter);

        // Setup the error receiver.
        final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
        errorReceiverProxy.setErrorReceiver(loggingErrorReceiver);

        // Create the pipeline.
        final PipelineEntity pipelineEntity = PipelineTestUtil.createBasicPipeline(
                StroomProcessTestFileUtil.getString("F2XTestUtil/f2xtest.Pipeline.data.xml"));
        final PipelineData pipelineData = pipelineEntity.getPipelineData();

        // final ElementType parserElementType = new ElementType("Parser");
        // final PropertyType textConverterPropertyType = new PropertyType(
        // parserElementType, "textConverter", "TextConverter", false);
        pipelineData.addProperty(
                PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverter));

        // final ElementType schemaFilterElementType = new ElementType(
        // "SchemaFilter");
        // final PropertyType schemaGroupPropertyType = new PropertyType(
        // schemaFilterElementType, "schemaGroup", "String", false);
        pipelineData.addProperty(PipelineDataUtil.createProperty("schemaFilter", "schemaGroup", "RECORDS"));

        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        final Pipeline pipeline = pipelineFactory.create(pipelineData);

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
