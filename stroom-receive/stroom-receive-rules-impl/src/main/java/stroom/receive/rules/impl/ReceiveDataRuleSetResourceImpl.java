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

package stroom.receive.rules.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.importexport.api.DocumentData;
import stroom.importexport.shared.Base64EncodedDocumentData;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.receive.rules.shared.ReceiveDataRuleSetResource;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

@AutoLogged
public class ReceiveDataRuleSetResourceImpl implements ReceiveDataRuleSetResource {

    private final Provider<ReceiveDataRuleSetService> ruleSetServiceProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    ReceiveDataRuleSetResourceImpl(final Provider<ReceiveDataRuleSetService> ruleSetServiceProvider,
                                   final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.ruleSetServiceProvider = ruleSetServiceProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public ReceiveDataRules create() {
        return ruleSetServiceProvider.get().createDocument();
    }

    @Override
    public ReceiveDataRules fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(ruleSetServiceProvider.get(), getDocRef(uuid));
    }

    @Override
    public ReceiveDataRules update(final String uuid, final ReceiveDataRules doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(ruleSetServiceProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(ReceiveDataRules.DOCUMENT_TYPE)
                .build();
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public Set<DocRef> listDocuments() {
        return ruleSetServiceProvider.get().listDocuments();
    }

    @Override
    @AutoLogged(value = OperationType.IMPORT, verb = "Importing data for ruleset")
    public DocRef importDocument(final Base64EncodedDocumentData encodedDocumentData) {
        final DocumentData documentData = DocumentData.fromBase64EncodedDocumentData(encodedDocumentData);
        final ImportState importState = new ImportState(documentData.getDocRef(),
                documentData.getDocRef().getName());
        return ruleSetServiceProvider.get().importDocument(
                documentData.getDocRef(),
                documentData.getDataMap(),
                importState,
                ImportSettings.auto());
    }

    @Override
    @AutoLogged(value = OperationType.EXPORT, verb = "Exporting data for ruleset")
    public Base64EncodedDocumentData exportDocument(final DocRef docRef) {
        final Map<String, byte[]> map = ruleSetServiceProvider.get().exportDocument(
                docRef,
                true,
                new ArrayList<>());
        return DocumentData.toBase64EncodedDocumentData(new DocumentData(docRef, map));
    }
}
