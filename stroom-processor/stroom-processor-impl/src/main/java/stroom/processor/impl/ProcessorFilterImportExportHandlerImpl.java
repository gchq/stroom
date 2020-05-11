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
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.api.ImportExportDocumentEventLog;
import stroom.importexport.api.NonExplorerDocRefProvider;
import stroom.importexport.shared.ImportState;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorFilterUtilities;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorDataSource;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterDataSource;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.util.shared.Message;
import stroom.util.shared.ResultPage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    ProcessorFilterImportExportHandlerImpl(final ProcessorFilterService processorFilterService, final ProcessorService processorService,
                                           final ImportExportDocumentEventLog importExportDocumentEventLog, final Serialiser2Factory serialiser2Factory,
                                           final DocRefInfoService docRefInfoService) {
        this.processorFilterService = processorFilterService;
        this.processorService = processorService;
        this.importExportDocumentEventLog = importExportDocumentEventLog;
        this.delegate = serialiser2Factory.createSerialiser(ProcessorFilter.class);
        this.docRefInfoService = docRefInfoService;
    }

    @Override
    public ImpexDetails importDocument(final DocRef docRef, Map<String, byte[]> dataMap, ImportState importState, ImportState.ImportMode importMode) {
        if (dataMap.get(META) == null)
            throw new IllegalArgumentException("Unable to import Processor with no meta file.  Docref is " + docRef);

        final ProcessorFilter processorFilter;
        try {
            processorFilter = delegate.read(dataMap.get(META));
        } catch (IOException ex) {
            throw new RuntimeException("Unable to read meta file associated with processor " + docRef, ex);
        }

        boolean ignore = ProcessorFilterUtilities.shouldImport (processorFilter);

        if (ignore)
            LOGGER.warn("Not importing processor filter " + docRef.getUuid() + " because it contains id fields");

        if (importMode != ImportState.ImportMode.CREATE_CONFIRMATION) {
            processorFilter.setProcessor(findProcessorForFilter(processorFilter));

            if (ImportState.State.NEW.equals(importState.getState())) {
                final boolean enable;
                final Long trackerStartMs;
                if (importState.getEnable() != null) {
                    enable = importState.getEnable();
                    trackerStartMs = importState.getEnableTime();
                } else {
                    enable = processorFilter.isEnabled();
                    trackerStartMs = null;
                }

                ProcessorFilter filter = findProcessorFilter(docRef);
                if (filter == null) {
                    final Processor processor = findProcessor(docRef.getUuid(),
                            processorFilter.getProcessorUuid(),
                            processorFilter.getPipelineUuid(),
                            processorFilter.getPipelineName());

                    processorFilterService.create(processor,
                            new DocRef(ProcessorFilter.ENTITY_TYPE, processorFilter.getUuid(), null),
                            processorFilter.getQueryData(),
                            processorFilter.getPriority(),
                            enable,
                            trackerStartMs);
                }

            } else if (ImportState.State.UPDATE.equals(importState.getState())) {
                ProcessorFilter currentVersion = findProcessorFilter(docRef);
                if (currentVersion != null) {
                    processorFilter.setId(currentVersion.getId());
                    processorFilter.setVersion(currentVersion.getVersion());
                }
                processorFilterService.update(processorFilter);
            }
        }
        return new ImpexDetails(docRef, processorFilter.getPipelineName(),ignore);
    }

    private ProcessorFilter findProcessorFilter(final DocRef docRef) {
        if (docRef == null || docRef.getUuid() == null)
            return null;

        final ExpressionOperator expression = new ExpressionOperator.Builder()
                .addTerm(ProcessorFilterDataSource.UUID, ExpressionTerm.Condition.EQUALS, docRef.getUuid()).build();

        ExpressionCriteria criteria = new ExpressionCriteria(expression);
        ResultPage<ProcessorFilter> page = processorFilterService.find(criteria);

        if (page != null && page.size() == 1) {
            ProcessorFilter filter = page.getFirst();

            if (filter.getPipelineName() == null && filter.getPipelineUuid() != null) {
                final Optional<String> optional = docRefInfoService.name(new DocRef(PipelineDoc.DOCUMENT_TYPE, filter.getPipelineUuid()));
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
        if (docRef == null)
            return null;

        //Don't export certain fields
        ProcessorFilter processorFilter = findProcessorFilter(docRef);

        processorFilter.setId(null);
        processorFilter.setVersion(null);
        processorFilter.setProcessorFilterTracker(null);
        processorFilter.setProcessor(null);
        processorFilter.setData(null);

        if (omitAuditFields)
            processorFilter = new AuditFieldFilter<ProcessorFilter>().apply(processorFilter);

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
    public Map<DocRef, Set<DocRef>> getDependencies() {
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
            if (pipelineName != null)
                return pipelineName + " Pipeline-" + "Filter " + name;
            else
                return "Unknown Pipeline-Filter " + name;
        }
        return null;
    }

    @Override
    public boolean docExists(final DocRef docRef) {
        DocRef associatedExplorerDocRef = findNearestExplorerDocRef(docRef);
        if (associatedExplorerDocRef != null)
            return true;
        else
            return findProcessorFilter(docRef) != null;
    }

    private Processor findProcessorForFilter(final ProcessorFilter filter) {
        Processor processor = filter.getProcessor();
        if (processor == null) {
            processor = findProcessor(filter.getUuid(), filter.getProcessorUuid(), filter.getPipelineUuid(), filter.getPipelineName());
            filter.setProcessor(processor);
        }

        return processor;
    }

    private Processor findProcessor(final String filterUuid, final String processorUuid, final String pipelineUuid, final String pipelineName) {
        if (filterUuid == null) {
            return null;
        }

        final ExpressionOperator expression = new ExpressionOperator.Builder()
                .addTerm(ProcessorDataSource.UUID, ExpressionTerm.Condition.EQUALS, processorUuid).build();

        ExpressionCriteria criteria = new ExpressionCriteria(expression);
        ResultPage<Processor> page = processorService.find(criteria);

        RuntimeException ex = null;
        if (page.size() == 0) {
            if (pipelineUuid != null) {
                //Create the missing processor
                processorService.create(new DocRef(Processor.ENTITY_TYPE, processorUuid), new DocRef(PipelineDoc.DOCUMENT_TYPE, pipelineUuid, pipelineName), true);
            } else {
                throw new RuntimeException("Unable to find processor for filter " + filterUuid);
            }
        }

        if (page.size() > 1) {
            ex = new IllegalStateException("Multiple processors with DocRef " + filterUuid + " found.");
        }

        if (ex != null) {
            LOGGER.error("Unable to export processor", ex);
            throw ex;
        }

        return page.getFirst();
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        return null;
    }
}
