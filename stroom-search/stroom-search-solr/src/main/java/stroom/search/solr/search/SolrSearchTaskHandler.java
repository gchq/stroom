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

package stroom.search.solr.search;

import stroom.docref.DocRef;
import stroom.query.api.datasource.IndexField;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValErr;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.search.solr.SolrIndexClientCache;
import stroom.search.solr.shared.SolrConnectionConfig;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.apache.solr.client.solrj.FastStreamingDocsCallback;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.StreamingResponseCallback;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.DataEntry;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class SolrSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrSearchTaskHandler.class);

    private final SolrIndexClientCache solrIndexClientCache;
    private final IndexFieldCache indexFieldCache;
    private final CountDownLatch completionLatch = new CountDownLatch(1);

    @Inject
    SolrSearchTaskHandler(final SolrIndexClientCache solrIndexClientCache,
                          final IndexFieldCache indexFieldCache) {
        this.solrIndexClientCache = solrIndexClientCache;
        this.indexFieldCache = indexFieldCache;
    }

    public void search(final TaskContext taskContext,
                       final DocRef indexDocRef,
                       final SolrIndexDoc solrIndexDoc,
                       final SolrParams solrParams,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer valuesConsumer,
                       final ErrorConsumer errorConsumer,
                       final LongAdder hitCount) {
        if (!Thread.currentThread().isInterrupted()) {
            taskContext.reset();
            taskContext.info(() -> "Searching Solr index");

            // Start searching.
            searchIndex(indexDocRef,
                    solrIndexDoc,
                    solrParams,
                    fieldIndex,
                    valuesConsumer,
                    errorConsumer,
                    hitCount);
        }
    }

    private void searchIndex(final DocRef indexDocRef,
                             final SolrIndexDoc solrIndexDoc,
                             final SolrParams solrParams,
                             final FieldIndex fieldIndex,
                             final ValuesConsumer valuesConsumer,
                             final ErrorConsumer errorConsumer,
                             final LongAdder hitCount) {
        final SolrConnectionConfig connectionConfig = solrIndexDoc.getSolrConnectionConfig();

        // If there is an error building the query then it will be null here.
        try {
            LOGGER.logDurationIfDebugEnabled(
                    () -> {
//                            try {
//                                fastStreamingDocsSearch(task, solrIndex, connectionConfig);
//                            } catch (final RuntimeException e) {
//                                complete.set(true);
//                                error(task, e.getMessage(), e);
//                            }

                        try {
                            streamingSearch(
                                    indexDocRef,
                                    solrParams,
                                    fieldIndex,
                                    valuesConsumer,
                                    errorConsumer,
                                    hitCount,
                                    solrIndexDoc,
                                    connectionConfig);
                        } catch (final RuntimeException e) {
                            error(errorConsumer, e);
                        } finally {
                            completionLatch.countDown();
                        }
                    },
                    () -> "searcher.search()");
        } catch (final RuntimeException e) {
            error(errorConsumer, e);
        }
    }

    private String[] getStoredFieldNames(final DocRef indexDocRef,
                                         final FieldIndex fieldIndex) {
        final String[] storedFieldNames = new String[fieldIndex.size()];
        for (int i = 0; i < storedFieldNames.length; i++) {
            final String fieldName = fieldIndex.getField(i);
            if (fieldName != null) {
                final IndexField indexField = indexFieldCache.get(indexDocRef, fieldName);
                if (indexField != null && indexField.isStored()) {
                    storedFieldNames[i] = fieldName;
                }
            }
        }
        return storedFieldNames;
    }

//    private void fastStreamingDocsSearch(final CachedSolrIndex solrIndex,
//                                         final SolrParams solrParams,
//                                         final FieldIndex fieldIndex,
//                                         final ValuesConsumer valuesConsumer,
//                                         final ErrorConsumer errorConsumer,
//                                         final AtomicLong hitCount,
//                                         final SolrIndexDoc solrIndexDoc,
//                                         final SolrConnectionConfig connectionConfig) {
//        final String[] fieldNames = getStoredFieldNames(solrIndex.getFieldsMap(), fieldIndex);
//
//        final Callback2 callback = new Callback2(
//                hitCount,
//                fieldNames,
//                valuesConsumer,
//                errorConsumer);
//        solrIndexClientCache.context(connectionConfig, solrClient -> {
//            try {
//                final QueryResponse response = solrClient.queryAndStreamResponse(solrIndexDoc.getCollection(),
//                        solrParams,
//                        callback);
//                LOGGER.debug(() -> "fastStreamingDocsSearch() - response=" + response);
//            } catch (final SolrServerException | IOException | RuntimeException e) {
//                error(errorConsumer, e);
//            }
//        });
//    }

    private void streamingSearch(final DocRef indexDocRef,
                                 final SolrParams solrParams,
                                 final FieldIndex fieldIndex,
                                 final ValuesConsumer valuesConsumer,
                                 final ErrorConsumer errorConsumer,
                                 final LongAdder hitCount,
                                 final SolrIndexDoc solrIndexDoc,
                                 final SolrConnectionConfig connectionConfig) {

        final String[] fieldNames = getStoredFieldNames(indexDocRef, fieldIndex);

        final Callback callback = new Callback(
                hitCount,
                fieldNames,
                valuesConsumer,
                errorConsumer);
        solrIndexClientCache.context(connectionConfig, solrClient -> {
            try {
                final QueryResponse response = solrClient.queryAndStreamResponse(solrIndexDoc.getCollection(),
                        solrParams,
                        callback);
                final DocListInfo docListInfo = callback.getDocListInfo();
                LOGGER.debug(() -> "streamingSearch() - response=" + response);
                LOGGER.debug(() -> "hitCount=" + hitCount.longValue());
//                LOGGER.debug(() -> "documentCount=" + task.getTracker().getDocumentCount());

                // Make sure we got back what we expected to.
                if (docListInfo == null) {
                    throw new SolrServerException("docListInfo is null");
                }
//                else if (docListInfo.getNumFound() != task.getTracker().getDocumentCount()) {
//                    throw new SolrServerException(
//                    "Unexpected hit count - numFound=" + docListInfo.getNumFound() +
//                    " hitCount=" + task.getTracker().getHitCount());
//                }

            } catch (final SolrServerException | IOException | RuntimeException e) {
                error(errorConsumer, e);
            }
        });
    }

    private void error(final ErrorConsumer errorConsumer, final Throwable t) {
        if (errorConsumer == null) {
            LOGGER.error(t::getMessage, t);
        } else {
            errorConsumer.add(t);
        }
    }

    public void awaitCompletion() throws InterruptedException {
        completionLatch.await();
    }

    private static class Callback extends StreamingResponseCallback {

        private final LongAdder hitCount;
        private final String[] fieldNames;
        private final ValuesConsumer valuesConsumer;
        private final ErrorConsumer errorConsumer;

        private DocListInfo docListInfo;

        Callback(final LongAdder hitCount,
                 final String[] fieldNames,
                 final ValuesConsumer valuesConsumer,
                 final ErrorConsumer errorConsumer) {
            this.hitCount = hitCount;
            this.fieldNames = fieldNames;
            this.valuesConsumer = valuesConsumer;
            this.errorConsumer = errorConsumer;
        }

        @Override
        public void streamSolrDocument(final SolrDocument doc) {
            try {
                hitCount.increment();

                Val[] values = null;
                for (int i = 0; i < fieldNames.length; i++) {
                    final String fieldName = fieldNames[i];
                    if (fieldName != null) {
                        // Get the ordinal of the field, so values can be mapped by the values receiver
                        final Object fieldValue = doc.getFirstValue(fieldName);
                        if (fieldValue != null) {
                            if (values == null) {
                                values = new Val[fieldNames.length];
                            }

                            if (fieldValue instanceof Long) {
                                // TODO Need to handle date fields as ValDate, assuming they come in as longs,
                                //  but we need the field type from somewhere
                                values[i] = ValLong.create((Long) fieldValue);
                            } else if (fieldValue instanceof Integer) {
                                values[i] = ValInteger.create((Integer) fieldValue);
                            } else if (fieldValue instanceof Double) {
                                values[i] = ValDouble.create((Double) fieldValue);
                            } else if (fieldValue instanceof Float) {
                                values[i] = ValDouble.create((Float) fieldValue);
                            } else if (fieldValue instanceof Boolean) {
                                values[i] = ValBoolean.create((Boolean) fieldValue);
                            } else {
                                values[i] = ValString.create(fieldValue.toString());
                            }
                        }
                    }
                }

                if (values != null) {
                    valuesConsumer.accept(Val.of(values));
                }
            } catch (final RuntimeException e) {
                error(e);
            }
        }

        @Override
        public void streamDocListInfo(final long numFound, final long start, final Float maxScore) {
            docListInfo = new DocListInfo(numFound, start, maxScore);
            LOGGER.debug(() -> "streamDocListInfo() - " + docListInfo);
        }

        private void error(final Throwable t) {
            if (errorConsumer == null) {
                LOGGER.error(t::getMessage, t);
            } else {
                errorConsumer.add(t);
            }
        }

        DocListInfo getDocListInfo() {
            return docListInfo;
        }
    }

    private static class DocListInfo {

        private final long numFound;
        private final long start;
        private final Float maxScore;

        DocListInfo(final long numFound, final long start, final Float maxScore) {
            this.numFound = numFound;
            this.start = start;
            this.maxScore = maxScore;
        }

        long getNumFound() {
            return numFound;
        }

        long getStart() {
            return start;
        }

        Float getMaxScore() {
            return maxScore;
        }

        @Override
        public String toString() {
            return "DocListInfo{" +
                    "numFound=" + numFound +
                    ", start=" + start +
                    ", maxScore=" + maxScore +
                    '}';
        }
    }

    private static class Callback2 implements FastStreamingDocsCallback {

        final Map<String, Val> map = new HashMap<>();
        private final AtomicLong hitCount;
        private final String[] fieldNames;
        private final ValuesConsumer valuesConsumer;
        private final ErrorConsumer errorConsumer;

        Callback2(final AtomicLong hitCount,
                  final String[] fieldNames,
                  final ValuesConsumer valuesConsumer,
                  final ErrorConsumer errorConsumer) {
            this.hitCount = hitCount;
            this.fieldNames = fieldNames;
            this.valuesConsumer = valuesConsumer;
            this.errorConsumer = errorConsumer;
        }

        @Override
        public Object startDoc(final Object docListObj) {
            hitCount.incrementAndGet();
            LOGGER.debug(() -> "startDoc() - docListObj=" + docListObj);
            return null;
        }

        @Override
        public void field(final DataEntry entry, final Object docObj) {
            if (entry.name() != null && entry.val() != null) {
                final Val val = convertFieldValue(entry);
                map.put(entry.name().toString(), val);
            }
        }

        @Override
        public void endDoc(final Object docObj) {
            if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException("Interrupted");
            }

            getStoredData(map);
            map.clear();
        }

        private Val convertFieldValue(final DataEntry entry) {
            switch (entry.type()) {
                case BOOL:
                    return ValBoolean.create(entry.boolVal());
                case BYTEARR:
                    return ValErr.create("Byte array not supported");
                case DATE:
                    return ValLong.create(entry.longVal());
                case DOUBLE:
                    return ValDouble.create(entry.doubleVal());
                case ENTRY_ITER:
                    final Object object = entry.val();
                    if (object instanceof Collection) {
                        final Collection collection = (Collection) object;
                        if (collection.size() > 1) {
                            return ValErr.create("Multi value");
                        } else if (collection.size() == 1) {
                            return ValString.create(collection.iterator().next().toString());
                        }
                    }
                    return ValString.create(entry.strValue());
//                return ValErr.create("Entry iterator not supported");
                case FLOAT:
                    return ValDouble.create(entry.floatVal());
                case INT:
                    return ValInteger.create(entry.intVal());
                case JAVA_OBJ:
                    return ValString.create(entry.strValue());
//                return ValErr.create("Java object not supported");
                case KEYVAL_ITER:
                    return ValString.create(entry.strValue());
//                return ValErr.create("Key value iterator not supported");
                case LONG:
                    return ValLong.create(entry.longVal());
                case NULL:
                    return ValNull.INSTANCE;
                case STR:
                    return ValString.create(entry.strValue());
            }
            return ValErr.create("Unknown type");
        }

        /**
         * This method takes a list of document id's and extracts the stored fields
         * that are required for data display. In some cases such as batch search we
         * only want to get stream and event ids, in these cases no values are
         * retrieved, only stream and event ids.
         */
        private void getStoredData(final Map<String, Val> valueMap) {
            try {
                Val[] values = null;

                for (int i = 0; i < fieldNames.length; i++) {
                    final String fieldName = fieldNames[i];
                    // If the field is not in fact stored then it will be null here.
                    if (fieldName != null) {
                        final Val value = valueMap.get(fieldName);
                        if (value != null) {
                            if (values == null) {
                                values = new Val[fieldNames.length];
                            }
                            values[i] = value;
                        }
                    }
                }

                if (values != null) {
                    valuesConsumer.accept(Val.of(values));
                }
            } catch (final RuntimeException e) {
                error(e);
            }
        }

        private void error(final Throwable t) {
            if (errorConsumer == null) {
                LOGGER.error(t::getMessage, t);
            } else {
                errorConsumer.add(t);
            }
        }
    }
}
