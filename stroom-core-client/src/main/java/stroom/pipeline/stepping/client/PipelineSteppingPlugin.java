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

package stroom.pipeline.stepping.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.core.client.presenter.Plugin;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.DocRef;
import stroom.explorer.client.presenter.ExplorerDropDownTreePresenter;
import stroom.explorer.shared.ExplorerData;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.pipeline.stepping.client.presenter.SteppingContentTabPresenter;
import stroom.pipeline.stepping.shared.GetPipelineForStreamAction;
import stroom.security.shared.DocumentPermissionNames;
import stroom.streamstore.shared.StreamType;

public class PipelineSteppingPlugin extends Plugin implements BeginPipelineSteppingEvent.Handler {
    private final Provider<ExplorerDropDownTreePresenter> pipelineSelection;
    private final Provider<SteppingContentTabPresenter> editorProvider;
    private final ContentManager contentManager;
    private final ClientDispatchAsync dispatcher;

    @Inject
    public PipelineSteppingPlugin(final EventBus eventBus, final Provider<SteppingContentTabPresenter> editorProvider,
                                  final Provider<ExplorerDropDownTreePresenter> pipelineSelection, final ContentManager contentManager,
                                  final ClientDispatchAsync dispatcher) {
        super(eventBus);
        this.pipelineSelection = pipelineSelection;
        this.editorProvider = editorProvider;
        this.contentManager = contentManager;
        this.dispatcher = dispatcher;

        registerHandler(eventBus.addHandler(BeginPipelineSteppingEvent.getType(), this));
    }

    @Override
    public void onBegin(final BeginPipelineSteppingEvent event) {
        if (event.getStreamId() != null) {
            if (event.getPipelineRef() != null) {
                choosePipeline(event.getPipelineRef(), event.getStreamId(), event.getEventId(),
                        event.getChildStreamType());
            } else {
                // If we don't have a pipeline id then try to guess one for the
                // supplied stream.
                dispatcher.execute(new GetPipelineForStreamAction(event.getStreamId(), event.getChildStreamId()),
                        new AsyncCallbackAdaptor<DocRef>() {
                            @Override
                            public void onSuccess(final DocRef result) {
                                choosePipeline(result, event.getStreamId(), event.getEventId(),
                                        event.getChildStreamType());
                            }
                        });
            }
        }
    }

    private void choosePipeline(final DocRef initialPipelineRef, final long streamId, final long eventId,
                                final StreamType childStreamType) {
        final ExplorerDropDownTreePresenter chooser = pipelineSelection.get();
        chooser.setCaption("Choose Pipeline To Step With");
        chooser.setIncludedTypes(PipelineEntity.ENTITY_TYPE);
        chooser.setRequiredPermissions(DocumentPermissionNames.READ);
        chooser.addDataSelectionHandler(new DataSelectionHandler<ExplorerData>() {
            @Override
            public void onSelection(final DataSelectionEvent<ExplorerData> event) {
                final DocRef pipeline = chooser.getSelectedEntityReference();
                if (pipeline != null) {
                    openEditor(pipeline, streamId, eventId, childStreamType);
                }
            }
        });

        if (initialPipelineRef != null) {
            chooser.setSelectedEntityReference(initialPipelineRef, false);
        }

        chooser.show();
    }

    private void openEditor(final DocRef pipeline, final long streamId, final long eventId,
                            final StreamType childStreamType) {
        final SteppingContentTabPresenter editor = editorProvider.get();
        editor.read(pipeline, streamId, eventId, childStreamType);
        contentManager.open(editor, editor, editor);
    }
}
