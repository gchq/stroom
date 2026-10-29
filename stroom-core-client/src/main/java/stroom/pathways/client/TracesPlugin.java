/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.client;

import stroom.content.client.ContentPlugin;
import stroom.core.client.ContentManager;
import stroom.core.client.event.CloseContentEvent;
import stroom.document.client.DocumentPluginRegistry;
import stroom.entity.client.component.DocumentEditorHost;
import stroom.pathways.client.component.TracesEditor;
import stroom.pathways.shared.TracesDoc;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class TracesPlugin extends ContentPlugin<DocumentEditorHost<TracesDoc>> {

    @Inject
    public TracesPlugin(final EventBus eventBus,
                        final Provider<TracesEditor> tracesEditorProvider,
                        final ContentManager contentManager,
                        final DocumentPluginRegistry documentPluginRegistry) {
        super(eventBus,
                contentManager,
                () -> new DocumentEditorHost<>(eventBus, tracesEditorProvider.get(), TracesDoc.TYPE),
                documentPluginRegistry);

        getEventBus().addHandler(
                ShowTracesEvent.getType(), showTracesEvent -> {

                    final TracesEditor tracesEditor = tracesEditorProvider.get();
                    tracesEditor.showTraces(showTracesEvent.getDataSourceRef(),
                            showTracesEvent.getPathway(),
                            showTracesEvent.getFilter());
                    final DocumentEditorHost<TracesDoc> tracesTab = new DocumentEditorHost<>(
                            eventBus, tracesEditor, TracesDoc.TYPE);

                    final CloseContentEvent.Handler closeHandler = (event) -> {
                        event.getCallback().closeTab(true);
                    };

                    // Tell the content manager to open the tab.
                    contentManager.open(closeHandler, tracesTab, tracesTab);
                });
    }

    @Override
    public String getType() {
        return TracesEditor.TAB_TYPE;
    }
}

