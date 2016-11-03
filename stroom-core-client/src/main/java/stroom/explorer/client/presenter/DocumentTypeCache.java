/*
 * Copyright 2016 Crown Copyright
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

package stroom.explorer.client.presenter;

import javax.inject.Inject;

import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.FetchDocumentTypesAction;

public class DocumentTypeCache {
    private final ClientDispatchAsync dispatcher;

    private DocumentTypes documentTypes;

    @Inject
    public DocumentTypeCache(final ClientDispatchAsync dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void clear() {
        documentTypes = null;
    }

    public void fetch(final AsyncCallbackAdaptor<DocumentTypes> callback) {
        // Get the document types if they are null.
        if (documentTypes == null) {
            final FetchDocumentTypesAction fetchDocumentTypesAction = new FetchDocumentTypesAction();
            dispatcher.execute(fetchDocumentTypesAction, new AsyncCallbackAdaptor<DocumentTypes>() {
                @Override
                public void onSuccess(final DocumentTypes result) {
                    documentTypes = result;
                    callback.onSuccess(result);
                }
            });
        } else {
            callback.onSuccess(documentTypes);
        }
    }
}
