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

package stroom.streamstore.server.tools;

import org.junit.Assert;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.entity.shared.BaseResultList;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.feed.shared.Feed.FeedStatus;
import stroom.index.server.IndexService;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexFields;
import stroom.pipeline.server.PipelineService;
import stroom.pipeline.server.PipelineTestUtil;
import stroom.pipeline.server.TextConverterService;
import stroom.pipeline.server.XSLTService;
import stroom.pipeline.server.parser.CombinedParser;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.FindTextConverterCriteria;
import stroom.pipeline.shared.FindXSLTCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntity.PipelineType;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.server.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.server.fs.serializable.RawInputSegmentWriter;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.server.StreamProcessorFilterService;
import stroom.streamtask.server.StreamProcessorService;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.spring.StroomSpringProfiles;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A tool used to add data to a stream store.
 */
@Component
@Profile(StroomSpringProfiles.IT)
public final class StoreCreationTool {
    private static final int OLD_YEAR = 2006;
    private static final File eventDataPipeline = StroomCoreServerTestFileUtil
            .getFile("samples/config/Standard_Pipelines/7740cfc4-3443-4001-bf0b-6adc77d5a3cf.Event_Data.Pipeline.data.xml");
    private static final File referenceDataPipeline = StroomCoreServerTestFileUtil
            .getFile("samples/config/Standard_Pipelines/b15e0cc8-3f82-446d-b106-04f43c38e19c.Reference_Data.Pipeline.data.xml");
    private static final File referenceLoaderPipeline = StroomCoreServerTestFileUtil
            .getFile("samples/config/Standard_Pipelines/da1c7351-086f-493b-866a-b42dbe990700.Reference_Loader.Pipeline.data.xml");
    private static final File contextDataPipeline = StroomCoreServerTestFileUtil
            .getFile("samples/config/Standard_Pipelines/fc281170-360d-4773-ad79-5378c5dcf52e.Context_Data.Pipeline.data.xml");
    private static final File indexingPipeline = StroomCoreServerTestFileUtil
            .getFile("samples/config/Standard_Pipelines/fcef1b20-083e-436c-ab95-47a6ce453435.Indexing.Pipeline.data.xml");
    private static final File searchExtractionPipeline = StroomCoreServerTestFileUtil
            .getFile("samples/config/Standard_Pipelines/3d9d60e9-61c2-4c88-a57b-7bc584dd970e.Search_Extraction.Pipeline.data.xml");
    private static long effectiveMsOffset = 0;
    @Resource
    private StreamStore streamStore;
    @Resource
    private FeedService feedService;
    @Resource
    private TextConverterService textConverterService;
    @Resource
    private XSLTService xsltService;
    @Resource
    private PipelineService pipelineService;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private CommonTestControl commonTestControl;
    @Resource
    private StreamProcessorService streamProcessorService;
    @Resource
    private StreamProcessorFilterService streamProcessorFilterService;
    @Resource
    private IndexService indexService;

    /**
     * Adds reference data to a stream store.
     *
     * @param feedName          The feed name to use.
     * @param formatDefLocation The XML conversion to use.
     * @param xsltLocation      The XSLT location.
     * @param dataLocation      The reference data location.
     * @return A reference feed definition.
     * @throws IOException Thrown if files not found.
     */
    public Feed addReferenceData(final String feedName, final TextConverterType textConverterType,
                                 final File textConverterLocation, final File xsltLocation, final File dataLocation) throws IOException {
        commonTestControl.createRequiredXMLSchemas();

        final Feed referenceFeed = getRefFeed(feedName, textConverterType, textConverterLocation, xsltLocation);

        // We need to ensure the reference data is older then the earliest event
        // we are going to see.
        ZonedDateTime dateTime = ZonedDateTime.now(ZoneOffset.UTC);
        dateTime = dateTime.withYear(OLD_YEAR);
        long effectiveMs = dateTime.toInstant().toEpochMilli();

        // Always make sure effective date is unique.
        effectiveMs += effectiveMsOffset++;

        // Add the associated data to the stream store.
        final Stream stream = Stream.createStreamForTesting(StreamType.RAW_REFERENCE, referenceFeed, effectiveMs,
                effectiveMs);

        final String data = StreamUtil.fileToString(dataLocation);

        final StreamTarget target = streamStore.openStreamTarget(stream);

        final InputStream inputStream = new ByteArrayInputStream(data.getBytes());
        final RASegmentOutputStream outputStream = new RASegmentOutputStream(target);

        final RawInputSegmentWriter writer = new RawInputSegmentWriter();
        writer.write(inputStream, outputStream);

        streamStore.closeStreamTarget(target);

        final StreamSource checkSource = streamStore.openStreamSource(stream.getId());
        Assert.assertEquals(data, StreamUtil.streamToString(checkSource.getInputStream()));
        streamStore.closeStreamSource(checkSource);

        return referenceFeed;
    }

    private Feed getRefFeed(final String feedName, final TextConverterType textConverterType,
                            final File textConverterLocation, final File xsltLocation) {
        Feed referenceFeed = feedService.loadByName(feedName);

        if (referenceFeed == null) {
            // Setup the feeds in mock feed configuration manager.
            referenceFeed = feedService.readDocument(feedService.createDocument(feedName, null));
            referenceFeed.setReference(true);
            referenceFeed.setDescription("Description " + feedName);
            referenceFeed.setStatus(FeedStatus.RECEIVE);
            referenceFeed = feedService.save(referenceFeed);

            // Setup the pipeline.
            final PipelineEntity pipeline = getReferencePipeline(feedName, referenceFeed, textConverterType,
                    textConverterLocation, xsltLocation);

            // Setup the stream processor.
            final BaseResultList<StreamProcessor> processors = streamProcessorService
                    .find(new FindStreamProcessorCriteria(pipeline));
            StreamProcessor streamProcessor = processors.getFirst();
            if (streamProcessor == null) {
                streamProcessor = new StreamProcessor();
                streamProcessor.setEnabled(true);
                streamProcessor.setPipeline(pipeline);
                streamProcessor = streamProcessorService.save(streamProcessor);
            }

            // Setup the stream processor filter.
            final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
            findStreamCriteria.obtainFeeds().obtainInclude().add(referenceFeed.getId());
            findStreamCriteria.obtainStreamTypeIdSet().add(StreamType.RAW_REFERENCE.getId());
            streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 2, findStreamCriteria);
        }

        return referenceFeed;
    }

    private PipelineEntity getReferencePipeline(final String feedName, final Feed referenceFeed,
                                                final TextConverterType textConverterType, final File textConverterLocation, final File xsltLocation) {
        // Setup the pipeline.
        final String data = StreamUtil.fileToString(referenceDataPipeline);
        final PipelineEntity pipeline = getPipeline(feedName, data);

        // Setup the text converter.
        final TextConverter textConverter = getTextConverter(feedName, textConverterType, textConverterLocation);
        if (textConverter != null) {
            pipeline.getPipelineData().addProperty(
                    PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverter));
        }
        // Setup the xslt.
        final XSLT xslt = getXSLT(feedName, xsltLocation);
        pipeline.getPipelineData().addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xslt));
        pipeline.getPipelineData().addProperty(PipelineDataUtil.createProperty("storeAppender", "feed", referenceFeed));
        pipeline.getPipelineData()
                .addProperty(PipelineDataUtil.createProperty("storeAppender", "streamType", StreamType.REFERENCE));
        return pipelineService.save(pipeline);
    }

    /**
     * Adds event data to a stream store.
     *
     * @param feedName          The feed name to use.
     * @param formatDefLocation The XML conversion to use.
     * @param xsltLocation      The XSLT location.
     * @param dataLocation      The event data location.
     * @param referenceFeeds    The reference feeds used.
     * @return An event feed definition.
     * @throws IOException Thrown if files not found.
     */
    public Feed addEventData(final String feedName, final TextConverterType translationTextConverterType,
                             final File translationTextConverterLocation, final File translationXsltLocation, final File dataLocation,
                             final Set<Feed> referenceFeeds) throws IOException {
        return addEventData(feedName, translationTextConverterType, translationTextConverterLocation,
                translationXsltLocation, null, null, null, null, dataLocation, null, referenceFeeds);
    }

    /**
     * Adds event data to a stream store.
     *
     * @param feedName          The feed name to use.
     * @param formatDefLocation The XML conversion to use.
     * @param xsltLocation      The XSLT location.
     * @param dataLocation      The event data location.
     * @param referenceFeeds    The reference feeds used.
     * @return An event feed definition.
     * @throws IOException Thrown if files not found.
     */
    public Feed addEventData(final String feedName, final TextConverterType translationTextConverterType,
                             final File translationTextConverterLocation, final File translationXsltLocation,
                             final TextConverterType contextTextConverterType, final File contextTextConverterLocation,
                             final File contextXsltLocation, final File flatteningXsltLocation, final File dataLocation,
                             final File contextLocation, final Set<Feed> referenceFeeds) throws IOException {
        commonTestControl.createRequiredXMLSchemas();

        final Feed eventFeed = getEventFeed(feedName, translationTextConverterType, translationTextConverterLocation,
                translationXsltLocation, contextTextConverterType, contextTextConverterLocation, contextXsltLocation,
                flatteningXsltLocation, referenceFeeds);

        // Add the associated data to the stream store.
        final Stream stream = Stream.createStreamForTesting(StreamType.RAW_EVENTS, eventFeed, null,
                System.currentTimeMillis());

        final StreamTarget dataTarget = streamStore.openStreamTarget(stream);

        final InputStream dataInputStream = StreamUtil.fileToStream(dataLocation);

        final RASegmentOutputStream dataOutputStream = new RASegmentOutputStream(dataTarget);

        final RawInputSegmentWriter dataWriter = new RawInputSegmentWriter();
        dataWriter.write(dataInputStream, dataOutputStream);

        if (contextLocation != null) {
            final StreamTarget contextTarget = dataTarget.addChildStream(StreamType.CONTEXT);

            final InputStream contextInputStream = StreamUtil.fileToStream(contextLocation);

            final RASegmentOutputStream contextOutputStream = new RASegmentOutputStream(contextTarget);

            final RawInputSegmentWriter contextWriter = new RawInputSegmentWriter();
            contextWriter.write(contextInputStream, contextOutputStream);
        }

        streamStore.closeStreamTarget(dataTarget);

        // Check that the data was written ok.
        final String data = StreamUtil.fileToString(dataLocation);
        final StreamSource checkSource = streamStore.openStreamSource(stream.getId());
        Assert.assertEquals(data, StreamUtil.streamToString(checkSource.getInputStream()));
        streamStore.closeStreamSource(checkSource);

        return eventFeed;
    }

    private Feed getEventFeed(final String feedName, final TextConverterType translationTextConverterType,
                              final File translationTextConverterLocation, final File translationXsltLocation,
                              final TextConverterType contextTextConverterType, final File contextTextConverterLocation,
                              final File contextXsltLocation, final File flatteningXsltLocation, final Set<Feed> referenceFeeds) {
        final List<PipelineReference> pipelineReferences = new ArrayList<>();

        Feed eventFeed = feedService.loadByName(feedName);

        if (eventFeed == null) {
            // Setup the feeds in mock feed configuration manager.
            eventFeed = feedService.create(feedName);
            eventFeed.setStatus(FeedStatus.RECEIVE);
            eventFeed.setDescription("Description " + feedName);
            eventFeed = feedService.save(eventFeed);
        }

        // Add context data loader pipeline.
        final PipelineEntity contextPipeline = getContextPipeline(eventFeed, contextTextConverterType,
                contextTextConverterLocation, contextXsltLocation);
        pipelineReferences.add(PipelineDataUtil.createReference("translationFilter", "pipelineReference",
                contextPipeline, eventFeed, StreamType.CONTEXT.getName()));

        // Add reference data loader pipelines.
        if (referenceFeeds != null && referenceFeeds.size() > 0) {
            final PipelineEntity referenceLoaderPipeline = getReferenceLoaderPipeline();
            for (final Feed refFeed : referenceFeeds) {
                pipelineReferences.add(PipelineDataUtil.createReference("translationFilter", "pipelineReference",
                        referenceLoaderPipeline, refFeed, StreamType.REFERENCE.getName()));
            }
        }

        // Create the event pipeline.
        final PipelineEntity pipeline = getEventPipeline(eventFeed, translationTextConverterType,
                translationTextConverterLocation, translationXsltLocation, flatteningXsltLocation, pipelineReferences);

        StreamProcessor streamProcessor = streamProcessorService.find(new FindStreamProcessorCriteria(pipeline))
                .getFirst();
        if (streamProcessor == null) {
            // Setup the stream processor.
            streamProcessor = new StreamProcessor();
            streamProcessor.setEnabled(true);
            streamProcessor.setPipeline(pipeline);
            streamProcessor = streamProcessorService.save(streamProcessor);

            // Setup the stream processor filter.
            final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
            findStreamCriteria.obtainFeeds().obtainInclude().add(eventFeed.getId());
            findStreamCriteria.obtainStreamTypeIdSet().add(StreamType.RAW_EVENTS.getId());
            streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, findStreamCriteria);
        }

        return eventFeed;
    }

    private PipelineEntity getContextPipeline(final Feed feed, final TextConverterType textConverterType,
                                              final File contextTextConverterLocation, final File contextXsltLocation) {
        final TextConverter contextTextConverter = getTextConverter(feed.getName() + "_CONTEXT", textConverterType,
                contextTextConverterLocation);
        final XSLT contextXSLT = getXSLT(feed.getName() + "_CONTEXT", contextXsltLocation);

        // Setup the pipeline.
        final String data = StreamUtil.fileToString(contextDataPipeline);
        final PipelineEntity pipeline = getPipeline(feed.getName() + "_CONTEXT", data);

        if (contextTextConverter != null) {
            pipeline.getPipelineData().addProperty(PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME,
                    "textConverter", contextTextConverter));
        }
        if (contextXSLT != null) {
            pipeline.getPipelineData()
                    .addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", contextXSLT));
        }

        return pipelineService.save(pipeline);
    }

    private PipelineEntity getReferenceLoaderPipeline() {
        // Setup the pipeline.
        return getPipeline("ReferenceLoader", StreamUtil.fileToString(referenceLoaderPipeline));
    }

    private PipelineEntity getEventPipeline(final Feed feed, final TextConverterType textConverterType,
                                            final File translationTextConverterLocation, final File translationXsltLocation,
                                            final File flatteningXsltLocation, final List<PipelineReference> pipelineReferences) {
        final PipelineEntity pipeline = getPipeline(feed.getName(), StreamUtil.fileToString(eventDataPipeline));

        // Setup the text converter.
        final TextConverter translationTextConverter = getTextConverter(feed.getName(), textConverterType,
                translationTextConverterLocation);

        // Setup the xslt.
        final XSLT translationXSLT = getXSLT(feed.getName(), translationXsltLocation);
        final XSLT flatteningXSLT = getXSLT(feed.getName() + "_FLATTENING", flatteningXsltLocation);

        // Read the pipeline data.
        final PipelineData pipelineData = pipeline.getPipelineData();

        // Change some properties.
        if (translationTextConverter != null) {
            // final ElementType elementType = new ElementType("Parser");
            // final PropertyType propertyType = new PropertyType(elementType,
            // "textConverter", "TextConverter", false);
            pipelineData.addProperty(PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter",
                    translationTextConverter));
        }
        if (translationXSLT != null) {
            // final ElementType elementType = new ElementType("XSLTFilter");
            // final PropertyType propertyType = new PropertyType(elementType,
            // "xslt", "XSLT", false);
            pipelineData.addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", translationXSLT));
        }
        if (pipelineReferences != null) {
            for (final PipelineReference pipelineReference : pipelineReferences) {
                pipelineData.addPipelineReference(pipelineReference);
            }
        }
        if (flatteningXSLT != null) {
            // final ElementType elementType = new ElementType("XSLTFilter");
            // final PropertyType propertyType = new PropertyType(elementType,
            // "xslt", "XSLT", false);
            pipelineData.addProperty(PipelineDataUtil.createProperty("flattenFilter", "xslt", flatteningXSLT));
        } else {
            pipelineData.removeLink(PipelineDataUtil.createLink("writeRecordCountFilter", "flattenFilter"));
        }
        // final ElementType elementType = new ElementType(
        // "StoreAppender", false, true);
        // final PropertyType feedPropertyType = new PropertyType(elementType,
        // "feed", "Feed", false);
        pipelineData.addProperty(PipelineDataUtil.createProperty("storeAppender", "feed", feed));

        // final PropertyType streamTypePropertyType = new PropertyType(
        // elementType, "streamType", "StreamType", false);
        pipelineData.addProperty(PipelineDataUtil.createProperty("storeAppender", "streamType", StreamType.EVENTS));

        // // Write the pipeline data.
        // final ByteArrayOutputStream outputStream = new
        // ByteArrayOutputStream();
        // pipelineDataWriter.write(pipelineData, outputStream);
        // data = outputStream.toString();
        //
        // pipeline.setData(data);

        return pipelineService.save(pipeline);
    }

    private PipelineEntity getIndexingPipeline(final Index index, final File xsltLocation) {
        final PipelineEntity pipeline = getPipeline(index.getName(), StreamUtil.fileToString(indexingPipeline));

        // Setup the xslt.
        final XSLT xslt = getXSLT(index.getName(), xsltLocation);

        // Read the pipeline data.
        final PipelineData pipelineData = pipeline.getPipelineData();

        // Change some properties.
        if (xslt != null) {
            // final ElementType elementType = new ElementType("XSLTFilter");
            // final PropertyType propertyType = new PropertyType(elementType,
            // "xslt", "XSLT", false);
            pipelineData.addProperty(PipelineDataUtil.createProperty("xsltFilter", "xslt", xslt));
        }

        // final ElementType elementType = new ElementType("IndexingFilter");
        // final PropertyType propertyType = new PropertyType(elementType,
        // "index", "Index", false);
        pipelineData.addProperty(PipelineDataUtil.createProperty("indexingFilter", "index", index));

        // // Write the pipeline data.
        // final ByteArrayOutputStream outputStream = new
        // ByteArrayOutputStream();
        // pipelineDataWriter.write(pipelineData, outputStream);
        // data = outputStream.toString();
        //
        // pipeline.setData(data);

        return pipelineService.save(pipeline);
    }

    private TextConverter getTextConverter(final String name, final TextConverterType textConverterType,
                                           final File textConverterLocation) {
        // Try to find an existing one first.
        final FindTextConverterCriteria criteria = new FindTextConverterCriteria();
        criteria.getName().setString(name);
        final BaseResultList<TextConverter> list = textConverterService.find(criteria);
        if (list != null && list.size() > 0) {
            return list.getFirst();
        }

        // Get the data to use.
        String data = null;
        if (textConverterLocation != null) {
            data = StreamUtil.fileToString(textConverterLocation);
            Assert.assertNotNull("Did not find " + textConverterLocation.getAbsolutePath(), data);
        }

        // Create a new text converter entity.
        TextConverter textConverter = null;
        if (data != null) {
            textConverter = textConverterService.create(name);
            textConverter.setDescription("Description " + name);
            textConverter.setConverterType(textConverterType);
            textConverter.setData(data);
            textConverter = textConverterService.save(textConverter);
        }

        return textConverter;
    }

    private XSLT getXSLT(final String name, final File xsltLocation) {
        // Try to find an existing one first.
        final FindXSLTCriteria criteria = new FindXSLTCriteria();
        criteria.getName().setString(name);
        final BaseResultList<XSLT> list = xsltService.find(criteria);
        if (list != null && list.size() > 0) {
            return list.getFirst();
        }

        // Get the data to use.
        String data = null;
        if (xsltLocation != null) {
            data = StreamUtil.fileToString(xsltLocation);
            Assert.assertNotNull("Did not find " + xsltLocation, data);
        }

        // Create the new XSLT entity.
        XSLT xslt = null;
        if (data != null) {
            xslt = xsltService.create(name);
            xslt.setDescription("Description " + name);
            xslt.setData(data);

            xslt = xsltService.save(xslt);
        }
        return xslt;
    }

    private PipelineEntity getPipeline(final String name, final String data) {
        // Try and find an existing pipeline first.
        final FindPipelineEntityCriteria findPipelineCriteria = new FindPipelineEntityCriteria();
        findPipelineCriteria.getName().setString(name);
        final BaseResultList<PipelineEntity> list = pipelineService.find(findPipelineCriteria);
        if (list != null && list.size() > 0) {
            return list.getFirst();
        }

        return PipelineTestUtil.createTestPipeline(pipelineService,name, "Description " + name,
                data);
    }

    public Index addIndex(final String name, final File translationXsltLocation) {
        final FindIndexCriteria criteria = new FindIndexCriteria();
        criteria.getName().setString(name);
        final BaseResultList<Index> list = indexService.find(criteria);
        if (list != null && list.size() > 0) {
            return list.getFirst();
        }

        final Index index = commonTestScenarioCreator.createIndex(name, createIndexFields());

        // Create the indexing pipeline.
        final PipelineEntity pipeline = getIndexingPipeline(index, translationXsltLocation);

        StreamProcessor streamProcessor = streamProcessorService.find(new FindStreamProcessorCriteria(pipeline))
                .getFirst();
        if (streamProcessor == null) {
            // Setup the stream processor.
            streamProcessor = new StreamProcessor();
            streamProcessor.setEnabled(true);
            streamProcessor.setPipeline(pipeline);
            streamProcessor = streamProcessorService.save(streamProcessor);

            // Setup the stream processor filter.
            final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
            findStreamCriteria.obtainStreamTypeIdSet().add(StreamType.EVENTS.getId());
            streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, findStreamCriteria);
        }

        return index;
    }

    private IndexFields createIndexFields() {
        final IndexFields indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("Feed"));
        indexFields.add(IndexField.createField("Feed (Keyword)", AnalyzerType.KEYWORD));
        indexFields.add(IndexField.createField("Action"));
        indexFields.add(IndexField.createDateField("EventTime"));
        indexFields.add(IndexField.createField("UserId", AnalyzerType.KEYWORD));
        indexFields.add(IndexField.createField("System"));
        indexFields.add(IndexField.createField("Environment"));
        indexFields.add(IndexField.createField("IPAddress", AnalyzerType.KEYWORD));
        indexFields.add(IndexField.createField("HostName", AnalyzerType.KEYWORD));
        indexFields.add(IndexField.createField("Generator"));
        indexFields.add(IndexField.createField("Command"));
        indexFields.add(IndexField.createField("Command (Keyword)", AnalyzerType.KEYWORD, true));
        indexFields.add(IndexField.createField("Description"));
        indexFields.add(IndexField.createField("Description (Case Sensitive)", AnalyzerType.ALPHA_NUMERIC, true));
        indexFields.add(IndexField.createField("Text", AnalyzerType.ALPHA_NUMERIC));
        return indexFields;
    }

    public PipelineEntity getSearchResultPipeline(final String name, final File xsltLocation) {
        final PipelineEntity pipeline = getPipeline(name, StreamUtil.fileToString(searchExtractionPipeline));
        pipeline.setPipelineType(PipelineType.SEARCH_EXTRACTION.getDisplayValue());

        // Setup the xslt.
        final XSLT xslt = getXSLT(name, xsltLocation);
        final PipelineData pipelineData = pipeline.getPipelineData();

        // Change some properties.
        if (xslt != null) {
            // final ElementType elementType = new ElementType("XSLTFilter");
            // final PropertyType propertyType = new PropertyType(elementType,
            // "xslt", "XSLT", false);
            pipelineData.addProperty(PipelineDataUtil.createProperty("xsltFilter", "xslt", xslt));
        }

        // Write the pipeline data.
        // final ByteArrayOutputStream outputStream = new
        // ByteArrayOutputStream();
        // pipelineDataWriter.write(pipelineData, outputStream);
        // data = outputStream.toString();
        //
        // pipeline.setData(data);

        return pipelineService.save(pipeline);
    }
}
