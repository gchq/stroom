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
import stroom.importexport.api.DocumentData;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.shared.Base64EncodedDocumentData;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.receive.rules.shared.ReceiveDataRuleSetResource;
import stroom.receive.rules.shared.ReceiveDataRules;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

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
    public ReceiveDataRules read(final DocRef docRef) {
        return documentResourceHelperProvider.get().read(ruleSetServiceProvider.get(), docRef);
    }

    @Override
    public ReceiveDataRules update(final ReceiveDataRules doc) {
        return documentResourceHelperProvider.get().update(ruleSetServiceProvider.get(), doc);
    }

    @Override
    public Set<DocRef> listDocuments() {
        return ruleSetServiceProvider.get().listDocuments();
    }

    @Override
    public DocRef importDocument(final Base64EncodedDocumentData encodedDocumentData) {
        final DocumentData documentData = DocumentData.fromBase64EncodedDocumentData(encodedDocumentData);
        final ImportState importState = new ImportState
                (documentData.getDocRef(),
                        documentData.getDocRef().getName());
        final ImportExportActionHandler.ImpexDetails result = ruleSetServiceProvider.get().importDocument(
                documentData.getDocRef(),
                documentData.getDataMap(),
                importState,
                ImportMode.IGNORE_CONFIRMATION);
        if (result != null)
            return result.getDocRef();
        else
            return null;
    }

    @Override
    public Base64EncodedDocumentData exportDocument(final DocRef docRef) {
        final Map<String, byte[]> map = ruleSetServiceProvider.get().exportDocument(
                docRef,
                true,
                new ArrayList<>());
        return DocumentData.toBase64EncodedDocumentData(new DocumentData(docRef, map));
    }
}