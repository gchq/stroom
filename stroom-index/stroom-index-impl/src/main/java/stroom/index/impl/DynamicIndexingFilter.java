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

package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.index.shared.AllPartition;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.Partition;
import stroom.index.shared.TimePartition;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaHolder;
import stroom.query.api.datasource.IndexField;
import stroom.search.extraction.AbstractFieldFilter;
import stroom.search.extraction.FieldValue;
import stroom.svg.shared.SvgImage;
import stroom.util.CharBuffer;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Locator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * The index filter... takes the index XML and builds the LUCENE documents
 */
@ConfigurableElement(
        type = "DynamicIndexingFilter",
        category = Category.FILTER,
        description = """
                A filter to send source data to an index.
                """,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = SvgImage.PIPELINE_INDEX)
class DynamicIndexingFilter extends AbstractFieldFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicIndexingFilter.class);

    private final MetaHolder metaHolder;
    private final LocationFactoryProxy locationFactory;
    private final Indexer indexer;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final IndexFieldService indexFieldService;
    private final LuceneIndexDocCache luceneIndexDocCache;
    private final CharBuffer debugBuffer = new CharBuffer(10);
    private DocRef indexRef;
    private LuceneIndexDoc index;
    private final TimePartitionFactory timePartitionFactory = new TimePartitionFactory();
    private final NavigableMap<Long, TimePartition> timePartitionTreeMap = new TreeMap<>();
    private final Map<Partition, IndexShardKey> indexShardKeyMap = new HashMap<>();

    private Locator locator;

    private Long currentEventTime;
    private Partition defaultPartition;

    private final Set<IndexField> foundFields = new HashSet<>();

    @Inject
    DynamicIndexingFilter(final MetaHolder metaHolder,
                          final LocationFactoryProxy locationFactory,
                          final Indexer indexer,
                          final ErrorReceiverProxy errorReceiverProxy,
                          final IndexFieldService indexFieldService,
                          final LuceneIndexDocCache luceneIndexDocCache) {
        super(locationFactory, errorReceiverProxy);
        this.metaHolder = metaHolder;
        this.locationFactory = locationFactory;
        this.indexer = indexer;
        this.errorReceiverProxy = errorReceiverProxy;
        this.indexFieldService = indexFieldService;
        this.luceneIndexDocCache = luceneIndexDocCache;
    }

    /**
     * Initialise
     */
    @Override
    public void startProcessing() {
        try {
            if (indexRef == null) {
                log(Severity.FATAL_ERROR, "Index has not been set", null);
                throw LoggedException.create("Index has not been set");
            }

            // Get the index and index fields from the cache.
            index = luceneIndexDocCache.get(indexRef);
            if (index == null) {
                log(Severity.FATAL_ERROR, "Unable to load index", null);
                throw LoggedException.create("Unable to load index");
            }

            // Create a key to create shards with.
            if (metaHolder == null || metaHolder.getMeta() == null || index.getPartitionBy() == null) {
                // Many tests don't use streams so where this is the case just
                // create a basic key.
                final Partition partition = AllPartition.INSTANCE;
                defaultPartition = partition;
                final IndexShardKey indexShardKey = IndexShardKey.createKey(index, partition);
                indexShardKeyMap.put(indexShardKey.getPartition(), indexShardKey);
            } else {
                final long metaCreateMs = metaHolder.getMeta().getCreateMs();
                final TimePartition timePartition = timePartitionFactory.create(index, metaCreateMs);
                defaultPartition = timePartition;
                final IndexShardKey indexShardKey = IndexShardKey.createKey(index, timePartition);
                indexShardKeyMap.put(indexShardKey.getPartition(), indexShardKey);
                timePartitionTreeMap.put(timePartition.getPartitionFromTime(), timePartition);
            }
        } finally {
            super.startProcessing();
        }
    }

    @Override
    public void endProcessing() {
        flushFields();
        super.endProcessing();
    }

    private void flushFields() {
        try {
            // Flush found fields to the index.
            indexFieldService.addFields(indexRef, foundFields);
            foundFields.clear();
        } catch (final RuntimeException e) {
            LOGGER.error("Error updating fields for: " + indexRef + " " + e.getMessage(), e);
        }
    }

    /**
     * Sets the locator to use when reporting errors.
     *
     * @param locator The locator to use.
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    @Override
    protected void processFields(final List<FieldValue> fieldValues) {
        final IndexDocument document = new IndexDocument();
        for (final FieldValue fieldValue : fieldValues) {
            final IndexField indexField = fieldValue.field();
            foundFields.add(indexField);
            if (foundFields.size() > 10_000) {
                flushFields();
            }

            if (indexField.isIndexed() || indexField.isStored()) {
                // Set the current event time if this is a recognised event time field.
                if (currentEventTime == null && indexField.getFldName().equals(index.getTimeField())) {
                    currentEventTime = fieldValue.value().toLong();
                }

                // Output some debug.
                if (LOGGER.isDebugEnabled()) {
                    debugBuffer.append("processIndexContent() - Adding to index indexName=");
                    debugBuffer.append(indexRef.getName());
                    debugBuffer.append(" name=");
                    debugBuffer.append(fieldValue.field().getFldName());
                    debugBuffer.append(" value=");
                    debugBuffer.append(fieldValue.value());

                    final String debug = debugBuffer.toString();
                    debugBuffer.clear();

                    LOGGER.debug(debug);
                }

                document.add(fieldValue);
            }
        }

        if (!document.getValues().isEmpty()) {
            try {
                Partition partition = defaultPartition;
                if (currentEventTime != null) {
                    final Entry<Long, TimePartition> entry = timePartitionTreeMap.floorEntry(currentEventTime);
                    if (entry != null &&
                        entry.getValue().getPartitionFromTime() <= currentEventTime &&
                        entry.getValue().getPartitionToTime() > currentEventTime) {
                        partition = entry.getValue();

                    } else {
                        final TimePartition timePartition = timePartitionFactory.create(index, currentEventTime);
                        timePartitionTreeMap.put(timePartition.getPartitionFromTime(), timePartition);
                        partition = timePartition;
                    }
                }

                final IndexShardKey indexShardKey =
                        indexShardKeyMap.computeIfAbsent(partition, k -> IndexShardKey.createKey(index, k));

                indexer.addDocument(indexShardKey, document);
            } catch (final RuntimeException e) {
                log(Severity.FATAL_ERROR, e.getMessage(), e);
                // Terminate processing as this is a fatal error.
                throw LoggedException.wrap(e);
            }
        }
    }

    @PipelineProperty(description = "The index to send records to.", displayPriority = 1)
    @PipelinePropertyDocRef(types = stroom.index.shared.LuceneIndexDoc.TYPE)
    public void setIndex(final DocRef indexRef) {
        this.indexRef = indexRef;
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }
}
