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

package stroom.streamstore.tools;

import org.junit.Assert;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.feed.FeedService;
import stroom.feed.shared.Feed;
import stroom.feed.shared.Feed.FeedStatus;
import stroom.index.IndexService;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.TextConverterStore;
import stroom.pipeline.XsltStore;
import stroom.pipeline.parser.CombinedParser;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.streamstore.StreamSource;
import stroom.streamstore.StreamStore;
import stroom.streamstore.StreamTarget;
import stroom.streamstore.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.fs.serializable.RawInputSegmentWriter;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.StreamProcessorFilterService;
import stroom.streamtask.StreamProcessorService;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A tool used to add data to a stream store.
 */
public final class StoreCreationTool {
    private static final int OLD_YEAR = 2006;
    private static final Path eventDataPipeline = StroomCoreServerTestFileUtil
            .getFile("samples/config/Standard_Pipelines/Event_Data.Pipeline.7740cfc4-3443-4001-bf0b-6adc77d5a3cf.xml");
    private static final Path referenceDataPipeline = StroomCoreServerTestFileUtil
            .getFile("samples/config/Standard_Pipelines/Reference_Data.Pipeline.b15e0cc8-3f82-446d-b106-04f43c38e19c.xml");
    private static final Path referenceLoaderPipeline = StroomCoreServerTestFileUtil
            .getFile("samples/config/Standard_Pipelines/Reference_Loader.Pipeline.da1c7351-086f-493b-866a-b42dbe990700.xml");
    private static final Path contextDataPipeline = StroomCoreServerTestFileUtil
            .getFile("samples/config/Standard_Pipelines/Context_Data.Pipeline.fc281170-360d-4773-ad79-5378c5dcf52e.xml");
    private static final Path indexingPipeline = StroomCoreServerTestFileUtil
            .getFile("samples/config/Standard_Pipelines/Indexing.Pipeline.fcef1b20-083e-436c-ab95-47a6ce453435.xml");
    private static final Path searchExtractionPipeline = StroomCoreServerTestFileUtil
            .getFile("samples/config/Standard_Pipelines/Search_Extraction.Pipeline.3d9d60e9-61c2-4c88-a57b-7bc584dd970e.xml");
    private static long effectiveMsOffset = 0;

    private final StreamStore streamStore;
    private final FeedService feedService;
    private final TextConverterStore textConverterStore;
    private final XsltStore xsltStore;
    private final PipelineStore pipelineStore;
    private final CommonTestScenarioCreator commonTestScenarioCreator;
    private final CommonTestControl commonTestControl;
    private final StreamProcessorService streamProcessorService;
    private final StreamProcessorFilterService streamProcessorFilterService;
    private final IndexService indexService;

    @Inject
    public StoreCreationTool(final StreamStore streamStore,
                             final FeedService feedService,
                             final TextConverterStore textConverterStore,
                             final XsltStore xsltStore,
                             final PipelineStore pipelineStore,
                             final CommonTestScenarioCreator commonTestScenarioCreator,
                             final CommonTestControl commonTestControl,
                             final StreamProcessorService streamProcessorService,
                             final StreamProcessorFilterService streamProcessorFilterService,
                             final IndexService indexService) {
        this.streamStore = streamStore;
        this.feedService = feedService;
        this.textConverterStore = textConverterStore;
        this.xsltStore = xsltStore;
        this.pipelineStore = pipelineStore;
        this.commonTestScenarioCreator = commonTestScenarioCreator;
        this.commonTestControl = commonTestControl;
        this.streamProcessorService = streamProcessorService;
        this.streamProcessorFilterService = streamProcessorFilterService;
        this.indexService = indexService;
    }

    /**
     * Adds reference data to a stream store.
     *
     * @param feedName              The feed name to use.
     * @param textConverterType     Type of text converter
     * @param textConverterLocation The Text Converter location
     * @param xsltLocation          The XSLT location
     * @param dataLocation          The reference data location.
     * @return A reference feed definition.
     * @throws IOException Thrown if files not found.
     */
    public Feed addReferenceData(final String feedName,
                                 final TextConverterType textConverterType,
                                 final Path textConverterLocation,
                                 final Path xsltLocation,
                                 final Path dataLocation) {
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
                            final Path textConverterLocation, final Path xsltLocation) {
        Feed referenceFeed = feedService.loadByName(feedName);

        if (referenceFeed == null) {
            // Setup the feeds in mock feed configuration manager.
            referenceFeed = feedService.create(feedName);
            referenceFeed.setReference(true);
            referenceFeed.setDescription("Description " + feedName);
            referenceFeed.setStatus(FeedStatus.RECEIVE);
            referenceFeed = feedService.save(referenceFeed);

            // Setup the pipeline.
            final DocRef pipelineRef = getReferencePipeline(feedName, referenceFeed, textConverterType,
                    textConverterLocation, xsltLocation);

            // Setup the stream processor.
            final BaseResultList<StreamProcessor> processors = streamProcessorService
                    .find(new FindStreamProcessorCriteria(pipelineRef));
            StreamProcessor streamProcessor = processors.getFirst();
            if (streamProcessor == null) {
                streamProcessor = new StreamProcessor();
                streamProcessor.setEnabled(true);
                streamProcessor.setPipelineUuid(pipelineRef.getUuid());
                streamProcessor = streamProcessorService.save(streamProcessor);
            }

            // Setup the stream processor filter.
            final QueryData findStreamQueryData = new QueryData.Builder()
                    .dataSource(StreamDataSource.STREAM_STORE_DOC_REF)
                    .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                            .addTerm(StreamDataSource.FEED, ExpressionTerm.Condition.EQUALS, referenceFeed.getName())
                            .addTerm(StreamDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, StreamType.RAW_REFERENCE.getName())
                            .build())
                    .build();
            streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 2, findStreamQueryData);
        }

        return referenceFeed;
    }

    private DocRef getReferencePipeline(final String feedName, final Feed referenceFeed,
                                        final TextConverterType textConverterType, final Path textConverterLocation, final Path xsltLocation) {
        // Setup the pipeline.
        final String data = StreamUtil.fileToString(referenceDataPipeline);
        final DocRef pipelineRef = getPipeline(feedName, data);
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);

        // Setup the text converter.
        final DocRef textConverterRef = getTextConverter(feedName, textConverterType, textConverterLocation);
        if (textConverterRef != null) {
            pipelineDoc.getPipelineData().addProperty(
                    PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverterRef));
        }
        // Setup the xslt.
        final DocRef xslt = getXSLT(feedName, xsltLocation);
        pipelineDoc.getPipelineData().addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xslt));
        pipelineDoc.getPipelineData().addProperty(PipelineDataUtil.createProperty("storeAppender", "feed", referenceFeed));
        pipelineDoc.getPipelineData()
                .addProperty(PipelineDataUtil.createProperty("storeAppender", "streamType", StreamType.REFERENCE));
        pipelineStore.writeDocument(pipelineDoc);
        return pipelineRef;
    }

    /**
     * Adds event data to a stream store.
     *
     * @param feedName                         The feed name to use.
     * @param translationTextConverterType     Type of text converter
     * @param translationTextConverterLocation The Text Converter location
     * @param translationXsltLocation          The XSLT location
     * @param dataLocation                     The event data location.
     * @param referenceFeeds                   The reference feeds used.
     * @return An event feed definition.
     * @throws IOException Thrown if files not found.
     */
    public Feed addEventData(final String feedName,
                             final TextConverterType translationTextConverterType,
                             final Path translationTextConverterLocation,
                             final Path translationXsltLocation,
                             final Path dataLocation,
                             final Set<Feed> referenceFeeds) throws IOException {
        return addEventData(feedName, translationTextConverterType, translationTextConverterLocation,
                translationXsltLocation, null, null, null, null, dataLocation, null, referenceFeeds);
    }

    /**
     * Adds event data to a stream store.
     *
     * @param feedName                         The feed name to use.
     * @param translationTextConverterType     Type of text converter
     * @param translationTextConverterLocation The Text Converter location
     * @param translationXsltLocation          The XSLT location
     * @param dataLocation                     The event data location.
     * @param referenceFeeds                   The reference feeds used.
     * @return An event feed definition.
     * @throws IOException Thrown if files not found.
     */
    private Feed addEventData(final String feedName,
                              final TextConverterType translationTextConverterType,
                              final Path translationTextConverterLocation,
                              final Path translationXsltLocation,
                              final TextConverterType contextTextConverterType,
                              final Path contextTextConverterLocation,
                              final Path contextXsltLocation,
                              final Path flatteningXsltLocation,
                              final Path dataLocation,
                              final Path contextLocation,
                              final Set<Feed> referenceFeeds) throws IOException {
        commonTestControl.createRequiredXMLSchemas();

        final Feed eventFeed = getEventFeed(feedName, translationTextConverterType, translationTextConverterLocation,
                translationXsltLocation, contextTextConverterType, contextTextConverterLocation, contextXsltLocation,
                flatteningXsltLocation, referenceFeeds);

        // Add the associated data to the stream store.
        final Stream stream = Stream.createStreamForTesting(StreamType.RAW_EVENTS, eventFeed, null,
                System.currentTimeMillis());

        final StreamTarget dataTarget = streamStore.openStreamTarget(stream);

        final InputStream dataInputStream = Files.newInputStream(dataLocation);

        final RASegmentOutputStream dataOutputStream = new RASegmentOutputStream(dataTarget);

        final RawInputSegmentWriter dataWriter = new RawInputSegmentWriter();
        dataWriter.write(dataInputStream, dataOutputStream);

        if (contextLocation != null) {
            final StreamTarget contextTarget = dataTarget.addChildStream(StreamType.CONTEXT);

            final InputStream contextInputStream = Files.newInputStream(contextLocation);

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
                              final Path translationTextConverterLocation, final Path translationXsltLocation,
                              final TextConverterType contextTextConverterType, final Path contextTextConverterLocation,
                              final Path contextXsltLocation, final Path flatteningXsltLocation, final Set<Feed> referenceFeeds) {
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
        final DocRef contextPipeline = getContextPipeline(eventFeed, contextTextConverterType,
                contextTextConverterLocation, contextXsltLocation);
        pipelineReferences.add(PipelineDataUtil.createReference("translationFilter", "pipelineReference",
                contextPipeline, DocRefUtil.create(eventFeed), StreamType.CONTEXT.getName()));

        // Add reference data loader pipelines.
        if (referenceFeeds != null && referenceFeeds.size() > 0) {
            final DocRef referenceLoaderPipeline = getReferenceLoaderPipeline();
            for (final Feed refFeed : referenceFeeds) {
                pipelineReferences.add(PipelineDataUtil.createReference("translationFilter", "pipelineReference",
                        referenceLoaderPipeline, DocRefUtil.create(refFeed), StreamType.REFERENCE.getName()));
            }
        }

        // Create the event pipeline.
        final DocRef pipelineRef = getEventPipeline(eventFeed, translationTextConverterType,
                translationTextConverterLocation, translationXsltLocation, flatteningXsltLocation, pipelineReferences);

        StreamProcessor streamProcessor = streamProcessorService.find(new FindStreamProcessorCriteria(pipelineRef))
                .getFirst();
        if (streamProcessor == null) {
            // Setup the stream processor.
            streamProcessor = new StreamProcessor();
            streamProcessor.setEnabled(true);
            streamProcessor.setPipelineUuid(pipelineRef.getUuid());
            streamProcessor = streamProcessorService.save(streamProcessor);

            // Setup the stream processor filter.
            final QueryData findStreamQueryData = new QueryData.Builder()
                    .dataSource(StreamDataSource.STREAM_STORE_DOC_REF)
                    .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                            .addTerm(StreamDataSource.FEED, ExpressionTerm.Condition.EQUALS, eventFeed.getName())
                            .addTerm(StreamDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, StreamType.RAW_EVENTS.getName())
                            .build())
                    .build();

            streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, findStreamQueryData);
        }

        return eventFeed;
    }

    private DocRef getContextPipeline(final Feed feed, final TextConverterType textConverterType,
                                      final Path contextTextConverterLocation, final Path contextXsltLocation) {
        final DocRef contextTextConverterRef = getTextConverter(feed.getName() + "_CONTEXT", textConverterType,
                contextTextConverterLocation);
        final DocRef contextXSLT = getXSLT(feed.getName() + "_CONTEXT", contextXsltLocation);

        // Setup the pipeline.
        final String data = StreamUtil.fileToString(contextDataPipeline);
        final DocRef pipelineRef = getPipeline(feed.getName() + "_CONTEXT", data);
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);

        if (contextTextConverterRef != null) {
            pipelineDoc.getPipelineData().addProperty(PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME,
                    "textConverter", contextTextConverterRef));
        }
        if (contextXSLT != null) {
            pipelineDoc.getPipelineData()
                    .addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", contextXSLT));
        }

        pipelineStore.writeDocument(pipelineDoc);
        return pipelineRef;
    }

    private DocRef getReferenceLoaderPipeline() {
        // Setup the pipeline.
        return getPipeline("ReferenceLoader", StreamUtil.fileToString(referenceLoaderPipeline));
    }

    private DocRef getEventPipeline(final Feed feed, final TextConverterType textConverterType,
                                    final Path translationTextConverterLocation, final Path translationXsltLocation,
                                    final Path flatteningXsltLocation, final List<PipelineReference> pipelineReferences) {
        final DocRef pipelineRef = getPipeline(feed.getName(), StreamUtil.fileToString(eventDataPipeline));
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);

        // Setup the text converter.
        final DocRef translationTextConverterRef = getTextConverter(feed.getName(), textConverterType,
                translationTextConverterLocation);

        // Setup the xslt.
        final DocRef translationXSLT = getXSLT(feed.getName(), translationXsltLocation);
        final DocRef flatteningXSLT = getXSLT(feed.getName() + "_FLATTENING", flatteningXsltLocation);

        // Read the pipeline data.
        final PipelineData pipelineData = pipelineDoc.getPipelineData();

        // Change some properties.
        if (translationTextConverterRef != null) {
            // final ElementType elementType = new ElementType("Parser");
            // final PropertyType propertyType = new PropertyType(elementType,
            // "textConverter", "TextConverter", false);
            pipelineData.addProperty(PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter",
                    translationTextConverterRef));
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

        pipelineStore.writeDocument(pipelineDoc);

        return pipelineRef;
    }

    private DocRef getIndexingPipeline(final Index index, final Path xsltLocation) {
        final DocRef pipelineRef = getPipeline(index.getName(), StreamUtil.fileToString(indexingPipeline));
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);


        // Setup the xslt.
        final DocRef xslt = getXSLT(index.getName(), xsltLocation);

        // Read the pipeline data.
        final PipelineData pipelineData = pipelineDoc.getPipelineData();

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

        pipelineStore.writeDocument(pipelineDoc);
        return pipelineRef;
    }

    private DocRef getTextConverter(final String name, final TextConverterType textConverterType,
                                    final Path textConverterLocation) {
        // Try to find an existing one first.
        final List<DocRef> refs = textConverterStore.list().stream().filter(docRef -> name.equals(docRef.getName())).collect(Collectors.toList());
        if (refs != null && refs.size() > 0) {
            return refs.get(0);
        }

        // Get the data to use.
        String data = null;
        if (textConverterLocation != null) {
            data = StreamUtil.fileToString(textConverterLocation);
            Assert.assertNotNull("Did not find " + FileUtil.getCanonicalPath(textConverterLocation), data);
        }

        // Create a new text converter entity.
        if (data != null) {
            final DocRef textConverterRef = textConverterStore.createDocument(name);
            final TextConverterDoc textConverter = textConverterStore.readDocument(textConverterRef);
            textConverter.setDescription("Description " + name);
            textConverter.setConverterType(textConverterType);
            textConverter.setData(data);
            textConverterStore.writeDocument(textConverter);
            return textConverterRef;
        }

        return null;
    }

    private DocRef getXSLT(final String name, final Path xsltLocation) {
        // Try to find an existing one first.
        final List<DocRef> refs = xsltStore.list().stream().filter(docRef -> name.equals(docRef.getName())).collect(Collectors.toList());
        if (refs != null && refs.size() > 0) {
            return refs.get(0);
        }

        // Get the data to use.
        String data = null;
        if (xsltLocation != null) {
            data = StreamUtil.fileToString(xsltLocation);
            Assert.assertNotNull("Did not find " + xsltLocation, data);
        }

        // Create the new XSLT entity.
        if (data != null) {
            final DocRef docRef = xsltStore.createDocument(name);
            final XsltDoc document = xsltStore.readDocument(docRef);
            document.setDescription("Description " + name);
            document.setData(data);
            xsltStore.writeDocument(document);
            return docRef;
        }
        return null;
    }

    private DocRef getPipeline(final String name, final String data) {
        // Try to find an existing one first.
        final List<DocRef> refs = pipelineStore.list().stream().filter(docRef -> name.equals(docRef.getName())).collect(Collectors.toList());
        if (refs != null && refs.size() > 0) {
            return refs.get(0);
        }

        return PipelineTestUtil.createTestPipeline(pipelineStore, name, "Description " + name,
                data);
    }

    public Index addIndex(final String name, final Path translationXsltLocation, final OptionalInt maxDocsPerShard) {
        final FindIndexCriteria criteria = new FindIndexCriteria();
        criteria.getName().setString(name);
        final BaseResultList<Index> list = indexService.find(criteria);
        if (list != null && list.size() > 0) {
            return list.getFirst();
        }

        final Index index = commonTestScenarioCreator.createIndex(
                name,
                createIndexFields(),
                maxDocsPerShard.orElse(Index.DEFAULT_MAX_DOCS_PER_SHARD));

        // Create the indexing pipeline.
        final DocRef pipelineRef = getIndexingPipeline(index, translationXsltLocation);

        StreamProcessor streamProcessor = streamProcessorService.find(new FindStreamProcessorCriteria(pipelineRef))
                .getFirst();
        if (streamProcessor == null) {
            // Setup the stream processor.
            streamProcessor = new StreamProcessor();
            streamProcessor.setEnabled(true);
            streamProcessor.setPipelineUuid(pipelineRef.getUuid());
            streamProcessor = streamProcessorService.save(streamProcessor);

            // Setup the stream processor filter.
            final QueryData findStreamQueryData = new QueryData.Builder()
                    .dataSource(StreamDataSource.STREAM_STORE_DOC_REF)
                    .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                            .addTerm(StreamDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, StreamType.EVENTS.getName())
                            .build())
                    .build();
            streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, findStreamQueryData);
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

    public DocRef getSearchResultPipeline(final String name, final Path xsltLocation) {
        final DocRef pipelineRef = getPipeline(name, StreamUtil.fileToString(searchExtractionPipeline));
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);

        // Setup the xslt.
        final DocRef xslt = getXSLT(name, xsltLocation);
        final PipelineData pipelineData = pipelineDoc.getPipelineData();

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

        pipelineStore.writeDocument(pipelineDoc);
        return pipelineRef;
    }
}
