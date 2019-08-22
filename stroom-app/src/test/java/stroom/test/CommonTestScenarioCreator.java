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
 *
 */

package stroom.test;


import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.docref.DocRef;
import stroom.index.VolumeCreator;
import stroom.index.impl.IndexStore;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFields;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.StandardHeaderArguments;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Help class to create some basic scenarios for testing.
 */
public class CommonTestScenarioCreator {
    private final Store streamStore;
    private final ProcessorService streamProcessorService;
    private final ProcessorFilterService processorFilterService;
    private final IndexStore indexStore;

    @Inject
    CommonTestScenarioCreator(final Store streamStore,
                              final ProcessorService streamProcessorService,
                              final ProcessorFilterService processorFilterService,
                              final IndexStore indexStore) {
        this.streamStore = streamStore;
        this.streamProcessorService = streamProcessorService;
        this.processorFilterService = processorFilterService;
        this.indexStore = indexStore;
    }

    public void createBasicTranslateStreamProcessor(final String feed) {
        final QueryData findStreamQueryData = new QueryData.Builder()
                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                        .addTerm(MetaFields.FEED_NAME, ExpressionTerm.Condition.EQUALS, feed)
                        .addTerm(MetaFields.TYPE_NAME, ExpressionTerm.Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();

        createProcessor(findStreamQueryData);
    }

    public void createProcessor(final QueryData queryData) {
        Processor processor = new Processor();
        processor.setPipelineUuid(UUID.randomUUID().toString());
        processor.setEnabled(true);
        processor = streamProcessorService.create(processor);
        processorFilterService.create(processor, queryData, 1, true);
    }

    public DocRef createIndex(final String name) {
        return createIndex(name, createIndexFields(), IndexDoc.DEFAULT_MAX_DOCS_PER_SHARD);
    }

    public DocRef createIndex(final String name, final List<IndexField> indexFields) {
        return createIndex(name, indexFields, IndexDoc.DEFAULT_MAX_DOCS_PER_SHARD);
    }

    public DocRef createIndex(final String name, final List<IndexField> indexFields, final int maxDocsPerShard) {
        // Create a test index.
        final DocRef indexRef = indexStore.createDocument(name);
        final IndexDoc index = indexStore.readDocument(indexRef);

        // Update the index
        index.setMaxDocsPerShard(maxDocsPerShard);
        index.setFields(indexFields);
        index.setVolumeGroupName(VolumeCreator.DEFAULT_VOLUME_GROUP);
        indexStore.writeDocument(index);
        assertThat(index).isNotNull();
        return indexRef;
    }

    public List<IndexField> createIndexFields() {
        final List<IndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("test"));
        return indexFields;
    }

    /**
     * @param feed related
     * @return a basic raw file
     */
    public Meta createSample2LineRawFile(final String feed, final String streamType) {
        final MetaProperties metaProperties = new MetaProperties.Builder()
                .feedName(feed)
                .typeName(streamType)
                .build();
        try (final Target target = streamStore.openTarget(metaProperties)) {
            TargetUtil.write(target, "line1\nline2");
            target.getAttributes().put(StandardHeaderArguments.FEED, feed);
            return target.getMeta();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Meta createSampleBlankProcessedFile(final String feed, final Meta sourceMeta) {
        final MetaProperties metaProperties = new MetaProperties.Builder()
                .feedName(feed)
                .typeName(StreamTypeNames.EVENTS)
                .parent(sourceMeta)
                .build();

        final String data = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Events xpath-default-namespace=\"records:2\" "
                + "xmlns:stroom=\"stroom\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns=\"event-logging:3\" "
                + "xsi:schemaLocation=\"event-logging:3 file://event-logging-v3.0.0.xsd\" "
                + "Version=\"3.0.0\"/>";

        try (final Target target = streamStore.openTarget(metaProperties)) {
            TargetUtil.write(target, data);
            return target.getMeta();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
