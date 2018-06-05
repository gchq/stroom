/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.docref.DocRef;
import stroom.entity.shared.EntityServiceFindAction;
import stroom.entity.shared.SharedDocRef;
import stroom.explorer.client.presenter.EntityChooser;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.GetPipelineForStreamAction;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.pipeline.stepping.client.presenter.SteppingContentTabPresenter;
import stroom.security.shared.DocumentPermissionNames;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamTypeEntity;
import stroom.streamtask.shared.StreamProcessor;

public class PipelineSteppingPlugin extends Plugin implements BeginPipelineSteppingEvent.Handler {
    private final Provider<EntityChooser> pipelineSelection;
    private final Provider<SteppingContentTabPresenter> editorProvider;
    private final ContentManager contentManager;
    private final ClientDispatchAsync dispatcher;

    @Inject
    public PipelineSteppingPlugin(final EventBus eventBus, final Provider<SteppingContentTabPresenter> editorProvider,
                                  final Provider<EntityChooser> pipelineSelection, final ContentManager contentManager,
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
                dispatcher.exec(new GetPipelineForStreamAction(event.getStreamId(), event.getChildStreamId())).onSuccess(result ->
                        choosePipeline(result, event.getStreamId(), event.getEventId(), event.getChildStreamType()));
            }
        }
    }

    private void choosePipeline(final SharedDocRef initialPipelineRef,
                                final long streamId,
                                final long eventId,
                                final String childStreamType) {
        final EntityChooser chooser = pipelineSelection.get();
        chooser.setCaption("Choose Pipeline To Step With");
        chooser.setIncludedTypes(PipelineDoc.DOCUMENT_TYPE);
        chooser.setRequiredPermissions(DocumentPermissionNames.READ);
        chooser.addDataSelectionHandler(event -> {
            final DocRef pipeline = chooser.getSelectedEntityReference();
            if (pipeline != null) {
                final FindStreamAttributeMapCriteria streamAttributeMapCriteria = new FindStreamAttributeMapCriteria();
                streamAttributeMapCriteria.obtainFindStreamCriteria().obtainSelectedIdSet().add(streamId);
                streamAttributeMapCriteria.getFetchSet().add(FeedDoc.DOCUMENT_TYPE);
                streamAttributeMapCriteria.getFetchSet().add(StreamTypeEntity.ENTITY_TYPE);
                streamAttributeMapCriteria.getFetchSet().add(StreamProcessor.ENTITY_TYPE);
                streamAttributeMapCriteria.getFetchSet().add(PipelineDoc.DOCUMENT_TYPE);

                dispatcher.exec(new EntityServiceFindAction<FindStreamAttributeMapCriteria, StreamAttributeMap>(streamAttributeMapCriteria)).onSuccess(result -> {
                    if (result != null && result.size() == 1) {
                        final StreamAttributeMap row = result.get(0);
                        openEditor(pipeline, row.getStream(), eventId, childStreamType);
                    }
                });
            }
        });

        if (initialPipelineRef != null) {
            chooser.setSelectedEntityReference(initialPipelineRef);
        }

        chooser.show();
    }

    private void openEditor(final DocRef pipeline, final Stream stream, final long eventId,
                            final String childStreamType) {
        final SteppingContentTabPresenter editor = editorProvider.get();
        editor.read(pipeline, stream, eventId, childStreamType);
        contentManager.open(editor, editor, editor);
    }
}
