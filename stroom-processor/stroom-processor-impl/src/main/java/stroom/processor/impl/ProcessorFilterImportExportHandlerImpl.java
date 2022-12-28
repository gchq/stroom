/*
 * Copyright 2020 Crown Copyright
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

package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.api.ImportExportDocumentEventLog;
import stroom.importexport.api.NonExplorerDocRefProvider;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorFilterUtil;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.util.shared.Message;
import stroom.util.shared.ResultPage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

public class ProcessorFilterImportExportHandlerImpl implements ImportExportActionHandler, NonExplorerDocRefProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorFilterImportExportHandlerImpl.class);
    private final ImportExportDocumentEventLog importExportDocumentEventLog;
    private final ProcessorFilterService processorFilterService;
    private final ProcessorService processorService;
    private final DocRefInfoService docRefInfoService;

    private static final String XML = "xml";
    private static final String META = "meta";

    private final Serialiser2<ProcessorFilter> delegate;

    @Inject
    ProcessorFilterImportExportHandlerImpl(final ProcessorFilterService processorFilterService,
                                           final ProcessorService processorService,
                                           final ImportExportDocumentEventLog importExportDocumentEventLog,
                                           final Serialiser2Factory serialiser2Factory,
                                           final DocRefInfoService docRefInfoService) {
        this.processorFilterService = processorFilterService;
        this.processorService = processorService;
        this.importExportDocumentEventLog = importExportDocumentEventLog;
        this.delegate = serialiser2Factory.createSerialiser(ProcessorFilter.class);
        this.docRefInfoService = docRefInfoService;
    }

    @Override
    public DocRef getOwnerDocument(final DocRef docRef,
                                   final Map<String, byte[]> dataMap) {
        if (dataMap.get(META) == null) {
            throw new IllegalArgumentException("Unable to import Processor with no meta file. DocRef is " + docRef);
        }

        try {
            final ProcessorFilter processorFilter = delegate.read(dataMap.get(META));
            if (processorFilter != null) {
                final Processor processor = processorFilter.getProcessor();
                if (processor != null) {
                    return new DocRef(PipelineDoc.DOCUMENT_TYPE,
                            processor.getPipelineUuid(),
                            processor.getPipelineName());
                } else {
                    return new DocRef(PipelineDoc.DOCUMENT_TYPE,
                            processorFilter.getPipelineUuid(),
                            processorFilter.getPipelineName());
                }
            }
        } catch (final IOException ex) {
            throw new RuntimeException("Unable to read meta file associated with processor " + docRef, ex);
        }

        return null;
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final Map<String, byte[]> dataMap,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        if (dataMap.get(META) == null) {
            throw new IllegalArgumentException("Unable to import Processor with no meta file.  DocRef is " + docRef);
        }

        final ProcessorFilter filter = findProcessorFilter(docRef);
        if (filter != null) {
            // Ignore filters that already exist as we really don't want to mess with them.
            LOGGER.warn("Not importing processor filter because it already exists");
            importState.setState(State.IGNORE);

        } else {
            importState.setState(State.NEW);
            final ProcessorFilter processorFilter;
            try {
                processorFilter = delegate.read(dataMap.get(META));
            } catch (IOException ex) {
                throw new RuntimeException("Unable to read meta file associated with processor filter " + docRef, ex);
            }

            if (!ProcessorFilterUtil.shouldImport(processorFilter)) {
                LOGGER.warn("Not importing processor filter " + docRef.getUuid() + " because it contains id fields");
                importState.setState(State.IGNORE);

            } else if (!ImportMode.CREATE_CONFIRMATION.equals(importSettings.getImportMode())) {
                final boolean enableFilters = importSettings.isEnableFilters();
                final Long minMetaCreateTimeMs = importSettings.getEnableFiltersFromTime();

                processorFilter.setProcessor(findProcessorForFilter(processorFilter));

                // Make sure we can get the processor for this filter.
                final Processor processor = findProcessor(docRef.getUuid(),
                        processorFilter.getProcessorUuid(),
                        processorFilter.getPipelineUuid(),
                        processorFilter.getPipelineName());

                if (processor != null) {
                    final DocRef processorFilterRef = new DocRef(ProcessorFilter.ENTITY_TYPE,
                            processorFilter.getUuid(),
                            null);
                    final CreateProcessFilterRequest request = CreateProcessFilterRequest
                            .builder()
                            .queryData(processorFilter.getQueryData())
                            .priority(processorFilter.getPriority())
                            .autoPriority(false)
                            .reprocess(processorFilter.isReprocess())
                            .enabled(enableFilters)
                            .minMetaCreateTimeMs(minMetaCreateTimeMs)
                            .maxMetaCreateTimeMs(processorFilter.getMaxMetaCreateTimeMs())
                            .build();
                    processorFilterService.importFilter(
                            processor,
                            processorFilterRef,
                            request);
                } else {
                    LOGGER.error("Processor not found on pipeline " +
                            processorFilter.getPipelineName() +
                            "(" +
                            processorFilter.getPipelineUuid() +
                            ")" +
                            " and failed to create");
                }
            }
        }

        return docRef;
    }

    private ProcessorFilter findProcessorFilter(final DocRef docRef) {
        if (docRef == null || docRef.getUuid() == null) {
            return null;
        }

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ProcessorFilterFields.UUID, ExpressionTerm.Condition.EQUALS, docRef.getUuid()).build();

        ExpressionCriteria criteria = new ExpressionCriteria(expression);
        ResultPage<ProcessorFilter> page = processorFilterService.find(criteria);

        if (page != null && page.size() == 1) {
            ProcessorFilter filter = page.getFirst();

            if (filter.getPipelineName() == null && filter.getPipelineUuid() != null) {
                final Optional<String> optional = docRefInfoService.name(new DocRef(PipelineDoc.DOCUMENT_TYPE,
                        filter.getPipelineUuid()));
                filter.setPipelineName(optional.orElse(null));
                if (filter.getPipelineName() == null) {
                    LOGGER.warn("Unable to find Pipeline " + filter.getPipelineUuid() +
                            " associated with ProcessorFilter " + filter.getUuid() + " (id: " + filter.getId() + ")");
                }
            }

            return filter;
        }

        return null;
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef, boolean omitAuditFields, List<Message> messageList) {
        if (docRef == null) {
            return null;
        }

        //Don't export certain fields
        ProcessorFilter processorFilter = findProcessorFilter(docRef);

        processorFilter.setId(null);
        processorFilter.setVersion(null);
        processorFilter.setProcessorFilterTracker(null);
        processorFilter.setProcessor(null);
        processorFilter.setData(null);

        if (omitAuditFields) {
            processorFilter = new AuditFieldFilter<ProcessorFilter>().apply(processorFilter);
        }

        Map<String, byte[]> data;
        try {
            data = delegate.write(processorFilter);
        } catch (IOException ioex) {
            LOGGER.error("Unable to create meta file for processor filter", ioex);
            importExportDocumentEventLog.exportDocument(docRef, ioex);
            throw new RuntimeException("Unable to create meta file for processor filter", ioex);
        }

        importExportDocumentEventLog.exportDocument(docRef, null);

        return data;
    }


    @Override
    public Set<DocRef> listDocuments() {
        return null;
    }

    @Override
    public String getType() {
        return ProcessorFilter.ENTITY_TYPE;
    }

    @Override
    public DocRef findNearestExplorerDocRef(final DocRef docref) {
        if (docref != null && ProcessorFilter.ENTITY_TYPE.equals(docref.getType())) {
            ProcessorFilter processorFilter = findProcessorFilter(docref);

            if (processorFilter != null) {
                Processor processor = findProcessorForFilter(processorFilter);
                return new DocRef(PipelineDoc.DOCUMENT_TYPE, processor.getPipelineUuid());
            }
        }

        return null;
    }

    @Override
    public String findNameOfDocRef(final DocRef docRef) {
        if (docRef != null && ProcessorFilter.ENTITY_TYPE.equals(docRef.getType())) {
            ProcessorFilter processorFilter = findProcessorFilter(docRef);

            final String name = docRef.getUuid().substring(0, 7);
            final String pipelineName = processorFilter.getPipelineName();
            if (pipelineName != null) {
                return pipelineName + " " + ProcessorFilter.ENTITY_TYPE + " " + name;
            } else {
                return "Unknown " + ProcessorFilter.ENTITY_TYPE + " " + name;
            }
        }
        return null;
    }

    private Processor findProcessorForFilter(final ProcessorFilter filter) {
        Processor processor = filter.getProcessor();
        if (processor == null) {
            processor = findProcessor(filter.getUuid(),
                    filter.getProcessorUuid(),
                    filter.getPipelineUuid(),
                    filter.getPipelineName());
            filter.setProcessor(processor);
        }

        return processor;
    }

    private Processor findProcessor(final String filterUuid,
                                    final String processorUuid,
                                    final String pipelineUuid,
                                    final String pipelineName) {
        if (filterUuid == null) {
            return null;
        }

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ProcessorFields.UUID, ExpressionTerm.Condition.EQUALS, processorUuid).build();

        ExpressionCriteria criteria = new ExpressionCriteria(expression);
        ResultPage<Processor> page = processorService.find(criteria);

        Processor result;
        RuntimeException ex;
        if (page.size() == 0) {
            if (pipelineUuid != null) {
                //Create the missing processor
                result = processorService.create(new DocRef(Processor.ENTITY_TYPE, processorUuid),
                        new DocRef(PipelineDoc.DOCUMENT_TYPE, pipelineUuid, pipelineName),
                        true);
                ex = null;
            } else {
                ex = new IllegalStateException("Unable to find processor for filter " + filterUuid);
                result = null;
            }
        } else if (page.size() > 1) {
            ex = new IllegalStateException("Multiple processors with DocRef " + filterUuid + " found.");
            result = null;
        } else if (page.size() == 1) {
            result = page.getFirst();
            ex = null;
        } else {
            ex = new IllegalStateException("Found " + page.size() + " processors with DocRef " + filterUuid + "!");
            result = null;
        }

        if (ex != null) {
            LOGGER.error("Unable to export processor", ex);
            throw ex;
        }

        return result;
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        final Map<DocRef, Set<DocRef>> dependencies = new HashMap<>();
        final ResultPage<ProcessorFilter> page = processorFilterService.find(new ExpressionCriteria());

        if (page != null && page.getValues() != null) {
            page.getValues().forEach(processorFilter -> {
                final DependencyRemapper dependencyRemapper = new DependencyRemapper();
                if (processorFilter.getQueryData() != null && processorFilter.getQueryData().getExpression() != null) {
                    dependencyRemapper.remapExpression(processorFilter.getQueryData().getExpression());
                }
                final DocRef docRef = new DocRef(ProcessorFilter.ENTITY_TYPE, processorFilter.getUuid());
                dependencies.put(docRef, dependencyRemapper.getDependencies());
            });
        }

        return dependencies;
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ProcessorFilterFields.UUID, ExpressionTerm.Condition.EQUALS, docRef.getUuid()).build();
        final ExpressionCriteria criteria = new ExpressionCriteria(expression);
        final ResultPage<ProcessorFilter> page = processorFilterService.find(criteria);
        if (page != null && page.getValues() != null) {
            page.getValues().forEach(processorFilter -> {
                if (processorFilter.getQueryData() != null && processorFilter.getQueryData().getExpression() != null) {
                    dependencyRemapper.remapExpression(processorFilter.getQueryData().getExpression());
                }
            });
        }
        return dependencyRemapper.getDependencies();
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////
}
