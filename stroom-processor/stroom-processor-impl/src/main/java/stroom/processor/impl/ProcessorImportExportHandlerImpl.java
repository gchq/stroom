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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.importexport.api.DocumentData;
import stroom.importexport.api.NonExplorerDocRefProvider;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.api.ImportExportDocumentEventLog;
import stroom.importexport.shared.ImportState;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorDataSource;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.util.shared.Message;
import stroom.util.shared.ResultPage;
import stroom.util.string.EncodingUtil;
import stroom.util.xml.XMLMarshallerUtil;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProcessorImportExportHandlerImpl implements ImportExportActionHandler, NonExplorerDocRefProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorImportExportHandlerImpl.class);
    private final ImportExportDocumentEventLog importExportDocumentEventLog;
    private final ProcessorService processorService;

    private static final String XML = "xml";
    private static final String META = "meta";

    private final Serialiser2<Processor> delegate;

    @Inject
    ProcessorImportExportHandlerImpl(final ProcessorService processorService, final ImportExportDocumentEventLog importExportDocumentEventLog,final Serialiser2Factory serialiser2Factory){
        this.processorService = processorService;
        this.importExportDocumentEventLog = importExportDocumentEventLog;
        this.delegate = serialiser2Factory.createSerialiser(Processor.class);
    }

    @Override
    public DocRef importDocument(DocRef docRef, Map<String, byte[]> dataMap, ImportState importState, ImportState.ImportMode importMode) {
        if (dataMap.get(META) == null)
            throw new IllegalArgumentException("Unable to import Processor with no meta file.  Docref is " + docRef);

        final Processor processor;
        try{
            processor = delegate.read(dataMap.get(META));
        } catch (IOException ex){
            throw new RuntimeException("Unable to read meta file associated with processor " + docRef, ex);
        }

//        if (processor.getId() != null || processor.getId() == 0) {
        if (ImportState.State.NEW.equals(importState.getState())) {
            processorService.create(processor);
        } else if (ImportState.State.UPDATE.equals(importState.getState())) {
            Processor currentVersion = findProcessor(docRef);
            if (currentVersion != null)
                processor.setId(currentVersion.getId());
            processorService.update(processor);
        }

        return docRef;
    }

    private Processor findProcessor (DocRef docRef){
        if (docRef == null || docRef.getUuid() == null)
            return null;

        final ExpressionOperator expression = new ExpressionOperator.Builder()
                .addTerm(ProcessorDataSource.UUID, ExpressionTerm.Condition.EQUALS, docRef.getUuid()).build();

        ExpressionCriteria criteria = new ExpressionCriteria(expression);
        ResultPage<Processor> page = processorService.find(criteria);

        RuntimeException ex = null;
        if (page.size() == 0)
            ex = new RuntimeException("Processor with DocRef " + docRef + " not found.");

        if (page.size() > 1)
            ex = new IllegalStateException("Multiple processors with DocRef " + docRef + " found.");

        if (ex != null) {
            LOGGER.error("Unable to export processor", ex);
            throw ex;
        }
        final Processor processor = page.getFirst();

        return processor;
    }

    @Override
    public Map<String, byte[]> exportDocument(DocRef docRef, boolean omitAuditFields, List<Message> messageList) {
        if (docRef == null)
            return null;

        //Don't export certain fields
        Processor processor = new AuditFieldFilter<Processor>().apply(findProcessor(docRef));
        processor.setId(null);
        processor.setVersion(null);

        Map<String, byte[]> data = null;
        try {
            data = delegate.write(processor);
        }catch (IOException ioex){
            importExportDocumentEventLog.exportDocument(docRef, ioex);
            LOGGER.error ("Unable to create meta file for processor", ioex);
            throw new RuntimeException("Unable to create meta file for processor", ioex);
        }
        ;
        return data;
    }

    private String getXmlFromProcessor(final Processor processor) {
        if (processor != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(Processor.class);
                return XMLMarshallerUtil.marshal(jaxbContext, XMLMarshallerUtil.removeEmptyCollections(processor));
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to marshal processor config", e);
                throw new RuntimeException("Unable to create XML for processor", e);
            }
        }

        return null;
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
        return Processor.ENTITY_TYPE;
    }

    @Override
    public DocRef findNearestExplorerDocRef(DocRef docref) {
        if (docref != null && Processor.ENTITY_TYPE.equals(docref.getType())){
            Processor processor = findProcessor(docref);

            DocRef pipelineDocRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, processor.getPipelineUuid());

            return pipelineDocRef;
        }

        return null;
    }

    @Override
    public String findNameOfDocRef(DocRef docRef) {
        if (docRef == null)
            return "Processor Null";
        return "Processor " + docRef.getUuid().substring(0,7);
    }

    @Override
    public boolean docExists(DocRef docRef) {
        return false;
    }
}
