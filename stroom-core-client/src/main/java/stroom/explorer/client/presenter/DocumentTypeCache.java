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

package stroom.explorer.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerResource;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;

import java.util.function.Consumer;
import javax.inject.Inject;

public class DocumentTypeCache {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    private final RestFactory restFactory;

    private DocumentTypes documentTypes;

    @Inject
    public DocumentTypeCache(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void clear() {
        documentTypes = null;
    }

    public void fetch(final Consumer<DocumentTypes> consumer,
                      final TaskMonitorFactory taskMonitorFactory) {
        // Get the document types if they are null.
        if (documentTypes == null) {
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(ExplorerResource::fetchDocumentTypes)
                    .onSuccess(result -> {
                        documentTypes = result;
                        consumer.accept(result);
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        } else {
            consumer.accept(documentTypes);
        }
    }
}
