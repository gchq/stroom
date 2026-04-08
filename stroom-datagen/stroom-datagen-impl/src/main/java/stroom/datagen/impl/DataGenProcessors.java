/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.datagen.impl;

import stroom.datagen.shared.DataGenDoc;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.ProcessorType;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.language.SearchRequestFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;
import stroom.view.api.ViewStore;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class DataGenProcessors {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataGenProcessors.class);

    private final SearchRequestFactory searchRequestFactory;
    private final ProcessorService processorService;
    private final ProcessorFilterService processorFilterService;
    private final ViewStore viewStore;

    @Inject
    public DataGenProcessors(final SearchRequestFactory searchRequestFactory,
                             final ProcessorService processorService,
                             final ProcessorFilterService processorFilterService,
                             final ViewStore viewStore) {
        this.searchRequestFactory = searchRequestFactory;
        this.processorService = processorService;
        this.processorFilterService = processorFilterService;
        this.viewStore = viewStore;
    }

    public void deleteProcessorFilters(final DataGenDoc doc) {
        final List<Processor> processors = getProcessor(doc);
        if (processors.size() > 1) {
            throw new RuntimeException("Unexpected number of processors");
        } else {
            for (final Processor processor : processors) {
                final ResultPage<ProcessorFilter> existing = getProcessorFilters(processor);
                deleteFilters(existing.getValues());
            }
        }
    }

    public ExpressionOperator getDefaultProcessingFilterExpression(final String query) {
        final Optional<ViewDoc> optionalViewDoc = getViewDoc(query);
        return optionalViewDoc.map(ViewDoc::getFilter).orElse(null);
    }

    private void deleteFilters(final List<ProcessorFilter> filters) {
        // Disable processor filters.
        for (final ProcessorFilter filter : filters) {
            processorFilterService.delete(filter.getId());
        }
    }

    private List<Processor> getProcessor(final DataGenDoc doc) {
        final List<Processor> list = new ArrayList<>();
        final DocRef docRef = doc.asDocRef();
        if (docRef != null) {
            // First try to find the associated processors
            final ExpressionOperator processorExpression = ExpressionOperator.builder()
                    .addTextTerm(
                            ProcessorFields.PROCESSOR_TYPE,
                            Condition.EQUALS,
                            ProcessorType.STREAMING_ANALYTIC.getDisplayValue())
                    .addDocRefTerm(
                            ProcessorFields.ANALYTIC_RULE,
                            Condition.IS_DOC_REF,
                            docRef)
                    .build();
            list.addAll(processorService.find(new ExpressionCriteria(processorExpression)).getValues());
        }
        return list;
    }

    private ResultPage<ProcessorFilter> getProcessorFilters(final Processor processor) {
        final ExpressionOperator filterExpression = ExpressionOperator.builder()
                .addIdTerm(
                        ProcessorFields.ID,
                        Condition.EQUALS,
                        processor.getId())
                .addBooleanTerm(
                        ProcessorFilterFields.DELETED,
                        Condition.EQUALS,
                        false)
                .build();
        return processorFilterService
                .find(new ExpressionCriteria(filterExpression));
    }

    private Optional<ViewDoc> getViewDoc(final String query) {
        final AtomicReference<ViewDoc> reference = new AtomicReference<>();
        try {
            if (query != null) {
                searchRequestFactory.extractDataSourceOnly(query, docRef -> {
                    try {
                        if (docRef != null) {
                            reference.set(viewStore.readDocument(docRef));
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e::getMessage, e);
                    }
                });
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }

        return Optional.ofNullable(reference.get());
    }
}
