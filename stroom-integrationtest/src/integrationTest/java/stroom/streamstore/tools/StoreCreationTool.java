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
import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.feed.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.index.IndexStore;
import stroom.index.shared.IndexDoc;
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
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.streamstore.api.StreamSource;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.api.StreamTarget;
import stroom.streamstore.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.fs.serializable.RawInputSegmentWriter;
import stroom.data.meta.api.StreamProperties;
import stroom.streamstore.shared.QueryData;
import stroom.data.meta.api.StreamDataSource;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.streamtask.StreamProcessorFilterService;
import stroom.streamtask.StreamProcessorService;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.Processor;
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
    private final FeedStore feedStore;
    private final TextConverterStore textConverterStore;
    private final XsltStore xsltStore;
    private final PipelineStore pipelineStore;
    private final CommonTestScenarioCreator commonTestScenarioCreator;
    private final CommonTestControl commonTestControl;
    private final StreamProcessorService streamProcessorService;
    private final StreamProcessorFilterService streamProcessorFilterService;
    private final IndexStore indexStore;

    @Inject
    public StoreCreationTool(final StreamStore streamStore,
                             final FeedStore feedStore,
                             final TextConverterStore textConverterStore,
                             final XsltStore xsltStore,
                             final PipelineStore pipelineStore,
                             final CommonTestScenarioCreator commonTestScenarioCreator,
                             final CommonTestControl commonTestControl,
                             final StreamProcessorService streamProcessorService,
                             final StreamProcessorFilterService streamProcessorFilterService,
                             final IndexStore indexStore) {
        this.streamStore = streamStore;
        this.feedStore = feedStore;
        this.textConverterStore = textConverterStore;
        this.xsltStore = xsltStore;
        this.pipelineStore = pipelineStore;
        this.commonTestScenarioCreator = commonTestScenarioCreator;
        this.commonTestControl = commonTestControl;
        this.streamProcessorService = streamProcessorService;
        this.streamProcessorFilterService = streamProcessorFilterService;
        this.indexStore = indexStore;
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
     */
    public DocRef addReferenceData(final String feedName,
                                   final TextConverterType textConverterType,
                                   final Path textConverterLocation,
                                   final Path xsltLocation,
                                   final Path dataLocation) {
        commonTestControl.createRequiredXMLSchemas();

        final DocRef referenceFeed = getRefFeed(feedName, textConverterType, textConverterLocation, xsltLocation);

        // We need to ensure the reference data is older then the earliest event
        // we are going to see.
        ZonedDateTime dateTime = ZonedDateTime.now(ZoneOffset.UTC);
        dateTime = dateTime.withYear(OLD_YEAR);
        long effectiveMs = dateTime.toInstant().toEpochMilli();

        // Always make sure effective date is unique.
        effectiveMs += effectiveMsOffset++;

        // Add the associated data to the stream store.
        final StreamProperties streamProperties = new StreamProperties.Builder()
                .feedName(referenceFeed.getName())
                .streamTypeName(StreamTypeNames.RAW_REFERENCE)
                .createMs(effectiveMs)
                .build();

        final String data = StreamUtil.fileToString(dataLocation);

        final StreamTarget target = streamStore.openStreamTarget(streamProperties);

        final InputStream inputStream = new ByteArrayInputStream(data.getBytes());
        final RASegmentOutputStream outputStream = new RASegmentOutputStream(target);

        final RawInputSegmentWriter writer = new RawInputSegmentWriter();
        writer.write(inputStream, outputStream);

        streamStore.closeStreamTarget(target);

        final StreamSource checkSource = streamStore.openStreamSource(target.getStream().getId());
        Assert.assertEquals(data, StreamUtil.streamToString(checkSource.getInputStream()));
        streamStore.closeStreamSource(checkSource);

        return referenceFeed;
    }

    private DocRef getRefFeed(final String feedName, final TextConverterType textConverterType,
                              final Path textConverterLocation, final Path xsltLocation) {
        DocRef docRef;
        final List<DocRef> docRefs = feedStore.findByName(feedName);
        if (docRefs.size() > 0) {
            docRef = docRefs.get(0);

        } else {
            // Setup the feeds in mock feed configuration manager.
            docRef = feedStore.createDocument(feedName);
            final FeedDoc feedDoc = feedStore.readDocument(docRef);
            feedDoc.setReference(true);
            feedDoc.setDescription("Description " + feedName);
            feedDoc.setStatus(FeedStatus.RECEIVE);
            feedStore.writeDocument(feedDoc);

            // Setup the pipeline.
            final DocRef pipelineRef = getReferencePipeline(feedName, textConverterType,
                    textConverterLocation, xsltLocation);

            // Setup the stream processor.
            final BaseResultList<Processor> processors = streamProcessorService
                    .find(new FindStreamProcessorCriteria(pipelineRef));
            Processor streamProcessor = processors.getFirst();
            if (streamProcessor == null) {
                streamProcessor = new Processor();
                streamProcessor.setEnabled(true);
                streamProcessor.setPipelineUuid(pipelineRef.getUuid());
                streamProcessor = streamProcessorService.save(streamProcessor);
            }

            // Setup the stream processor filter.
            final QueryData findStreamQueryData = new QueryData.Builder()
                    .dataSource(StreamDataSource.STREAM_STORE_DOC_REF)
                    .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                            .addTerm(StreamDataSource.FEED, ExpressionTerm.Condition.EQUALS, feedDoc.getName())
                            .addTerm(StreamDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_REFERENCE)
                            .build())
                    .build();
            streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 2, findStreamQueryData);
        }

        return docRef;
    }

    private DocRef getReferencePipeline(final String feedName,
                                        final TextConverterType textConverterType,
                                        final Path textConverterLocation,
                                        final Path xsltLocation) {
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
        pipelineDoc.getPipelineData().addProperty(PipelineDataUtil.createProperty("storeAppender", "feed", new DocRef(null, null, feedName)));
        pipelineDoc.getPipelineData()
                .addProperty(PipelineDataUtil.createProperty("storeAppender", "streamType", StreamTypeNames.REFERENCE));
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
    public void addEventData(final String feedName,
                             final TextConverterType translationTextConverterType,
                             final Path translationTextConverterLocation,
                             final Path translationXsltLocation,
                             final Path dataLocation,
                             final Set<DocRef> referenceFeeds) throws IOException {
        addEventData(feedName, translationTextConverterType, translationTextConverterLocation,
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
    private void addEventData(final String feedName,
                              final TextConverterType translationTextConverterType,
                              final Path translationTextConverterLocation,
                              final Path translationXsltLocation,
                              final TextConverterType contextTextConverterType,
                              final Path contextTextConverterLocation,
                              final Path contextXsltLocation,
                              final Path flatteningXsltLocation,
                              final Path dataLocation,
                              final Path contextLocation,
                              final Set<DocRef> referenceFeeds) throws IOException {
        commonTestControl.createRequiredXMLSchemas();

        getEventFeed(feedName, translationTextConverterType, translationTextConverterLocation,
                translationXsltLocation, contextTextConverterType, contextTextConverterLocation, contextXsltLocation,
                flatteningXsltLocation, referenceFeeds);

        // Add the associated data to the stream store.
        final StreamProperties streamProperties = new StreamProperties.Builder()
                .feedName(feedName)
                .streamTypeName(StreamTypeNames.RAW_EVENTS)
                .build();

        final StreamTarget dataTarget = streamStore.openStreamTarget(streamProperties);

        final InputStream dataInputStream = Files.newInputStream(dataLocation);

        final RASegmentOutputStream dataOutputStream = new RASegmentOutputStream(dataTarget);

        final RawInputSegmentWriter dataWriter = new RawInputSegmentWriter();
        dataWriter.write(dataInputStream, dataOutputStream);

        if (contextLocation != null) {
            final StreamTarget contextTarget = dataTarget.addChildStream(StreamTypeNames.CONTEXT);

            final InputStream contextInputStream = Files.newInputStream(contextLocation);

            final RASegmentOutputStream contextOutputStream = new RASegmentOutputStream(contextTarget);

            final RawInputSegmentWriter contextWriter = new RawInputSegmentWriter();
            contextWriter.write(contextInputStream, contextOutputStream);
        }

        streamStore.closeStreamTarget(dataTarget);

        // Check that the data was written ok.
        final String data = StreamUtil.fileToString(dataLocation);
        final StreamSource checkSource = streamStore.openStreamSource(dataTarget.getStream().getId());
        Assert.assertEquals(data, StreamUtil.streamToString(checkSource.getInputStream()));
        streamStore.closeStreamSource(checkSource);
    }

    private DocRef getEventFeed(final String feedName, final TextConverterType translationTextConverterType,
                                final Path translationTextConverterLocation, final Path translationXsltLocation,
                                final TextConverterType contextTextConverterType, final Path contextTextConverterLocation,
                                final Path contextXsltLocation, final Path flatteningXsltLocation, final Set<DocRef> referenceFeeds) {
        final List<PipelineReference> pipelineReferences = new ArrayList<>();

        DocRef docRef;
        final List<DocRef> docRefs = feedStore.findByName(feedName);
        if (docRefs.size() > 0) {
            docRef = docRefs.get(0);
        } else {
            // Setup the feeds in mock feed configuration manager.
            docRef = feedStore.createDocument(feedName);
            FeedDoc feedDoc = feedStore.readDocument(docRef);
            feedDoc.setDescription("Description " + feedName);
            feedDoc.setStatus(FeedStatus.RECEIVE);
            feedStore.writeDocument(feedDoc);
        }

        // Add context data loader pipeline.
        final DocRef contextPipeline = getContextPipeline(feedName, contextTextConverterType,
                contextTextConverterLocation, contextXsltLocation);
        pipelineReferences.add(PipelineDataUtil.createReference("translationFilter", "pipelineReference",
                contextPipeline, docRef, StreamTypeNames.CONTEXT));

        // Add reference data loader pipelines.
        if (referenceFeeds != null && referenceFeeds.size() > 0) {
            final DocRef referenceLoaderPipeline = getReferenceLoaderPipeline();
            for (final DocRef refFeed : referenceFeeds) {
                pipelineReferences.add(PipelineDataUtil.createReference("translationFilter", "pipelineReference",
                        referenceLoaderPipeline, refFeed, StreamTypeNames.REFERENCE));
            }
        }

        // Create the event pipeline.
        final DocRef pipelineRef = getEventPipeline(feedName, translationTextConverterType,
                translationTextConverterLocation, translationXsltLocation, flatteningXsltLocation, pipelineReferences);

        Processor streamProcessor = streamProcessorService.find(new FindStreamProcessorCriteria(pipelineRef))
                .getFirst();
        if (streamProcessor == null) {
            // Setup the stream processor.
            streamProcessor = new Processor();
            streamProcessor.setEnabled(true);
            streamProcessor.setPipelineUuid(pipelineRef.getUuid());
            streamProcessor = streamProcessorService.save(streamProcessor);

            // Setup the stream processor filter.
            final QueryData findStreamQueryData = new QueryData.Builder()
                    .dataSource(StreamDataSource.STREAM_STORE_DOC_REF)
                    .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                            .addTerm(StreamDataSource.FEED, ExpressionTerm.Condition.EQUALS, docRef.getName())
                            .addTerm(StreamDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                            .build())
                    .build();

            streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, findStreamQueryData);
        }

        return docRef;
    }

    private DocRef getContextPipeline(final String feedName, final TextConverterType textConverterType,
                                      final Path contextTextConverterLocation, final Path contextXsltLocation) {
        final DocRef contextTextConverterRef = getTextConverter(feedName + "_CONTEXT", textConverterType,
                contextTextConverterLocation);
        final DocRef contextXSLT = getXSLT(feedName + "_CONTEXT", contextXsltLocation);

        // Setup the pipeline.
        final String data = StreamUtil.fileToString(contextDataPipeline);
        final DocRef pipelineRef = getPipeline(feedName + "_CONTEXT", data);
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

    private DocRef getEventPipeline(final String feedName, final TextConverterType textConverterType,
                                    final Path translationTextConverterLocation, final Path translationXsltLocation,
                                    final Path flatteningXsltLocation, final List<PipelineReference> pipelineReferences) {
        final DocRef pipelineRef = getPipeline(feedName, StreamUtil.fileToString(eventDataPipeline));
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);

        // Setup the text converter.
        final DocRef translationTextConverterRef = getTextConverter(feedName, textConverterType,
                translationTextConverterLocation);

        // Setup the xslt.
        final DocRef translationXSLT = getXSLT(feedName, translationXsltLocation);
        final DocRef flatteningXSLT = getXSLT(feedName + "_FLATTENING", flatteningXsltLocation);

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
        pipelineData.addProperty(PipelineDataUtil.createProperty("storeAppender", "feed", new DocRef(null, null, feedName)));

        // final PropertyType streamTypePropertyType = new PropertyType(
        // elementType, "streamType", "StreamType", false);
        pipelineData.addProperty(PipelineDataUtil.createProperty("storeAppender", "streamType", StreamTypeNames.EVENTS));

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

    private DocRef getIndexingPipeline(final DocRef indexRef, final Path xsltLocation) {
        final DocRef pipelineRef = getPipeline(indexRef.getName(), StreamUtil.fileToString(indexingPipeline));
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);


        // Setup the xslt.
        final DocRef xslt = getXSLT(indexRef.getName(), xsltLocation);

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
        pipelineData.addProperty(PipelineDataUtil.createProperty("indexingFilter", "index", indexRef));

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

    private DocRef getTextConverter(final String name,
                                    final TextConverterType textConverterType,
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

    public DocRef addIndex(final String name, final Path translationXsltLocation, final OptionalInt maxDocsPerShard) {
        // Try to find an existing one first.
        final List<DocRef> refs = indexStore.list().stream().filter(docRef -> name.equals(docRef.getName())).collect(Collectors.toList());
        if (refs != null && refs.size() > 0) {
            return refs.get(0);
        }

        final DocRef indexRef = commonTestScenarioCreator.createIndex(
                name,
                createIndexFields(),
                maxDocsPerShard.orElse(IndexDoc.DEFAULT_MAX_DOCS_PER_SHARD));

        // Create the indexing pipeline.
        final DocRef pipelineRef = getIndexingPipeline(indexRef, translationXsltLocation);

        Processor streamProcessor = streamProcessorService.find(new FindStreamProcessorCriteria(pipelineRef))
                .getFirst();
        if (streamProcessor == null) {
            // Setup the stream processor.
            streamProcessor = new Processor();
            streamProcessor.setEnabled(true);
            streamProcessor.setPipelineUuid(pipelineRef.getUuid());
            streamProcessor = streamProcessorService.save(streamProcessor);

            // Setup the stream processor filter.
            final QueryData findStreamQueryData = new QueryData.Builder()
                    .dataSource(StreamDataSource.STREAM_STORE_DOC_REF)
                    .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                            .addTerm(StreamDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, StreamTypeNames.EVENTS)
                            .build())
                    .build();
            streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, findStreamQueryData);
        }

        return indexRef;
    }

    private List<IndexField> createIndexFields() {
        final List<IndexField> indexFields = IndexFields.createStreamIndexFields();
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
