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

package stroom.pipeline.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.EntityPlugin;
import stroom.entity.client.EntityPluginEventManager;
import stroom.entity.client.presenter.EntityEditPresenter;
import stroom.entity.shared.DocRefUtil;
import stroom.pipeline.client.event.CreateProcessorEvent;
import stroom.pipeline.client.presenter.PipelinePresenter;
import stroom.pipeline.shared.PipelineEntity;
import stroom.process.client.presenter.ProcessorPresenter;
import stroom.query.api.v1.DocRef;
import stroom.security.client.ClientSecurityContext;
import stroom.streamtask.shared.StreamProcessor;

public class PipelinePlugin extends EntityPlugin<PipelineEntity> {
    private final Provider<PipelinePresenter> editorProvider;

    @Inject
    public PipelinePlugin(final EventBus eventBus, final Provider<PipelinePresenter> editorProvider,
            final ClientDispatchAsync dispatcher, final ClientSecurityContext securityContext,
            final ContentManager contentManager, final EntityPluginEventManager entityPluginEventManager) {
        super(eventBus, dispatcher, securityContext, contentManager, entityPluginEventManager);
        this.editorProvider = editorProvider;
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getEventBus().addHandler(CreateProcessorEvent.getType(), event -> {
            final StreamProcessor streamProcessor = event.getStreamProcessorFilter().getStreamProcessor();
            final PipelineEntity pipelineEntity = streamProcessor.getPipeline();
            final DocRef docRef = DocRefUtil.create(pipelineEntity);
            // Open the item in the content pane.
            final PipelinePresenter pipelinePresenter = (PipelinePresenter) open(docRef, true);
            // Highlight the item in the explorer tree.
            highlight(docRef);

            pipelinePresenter.selectTab(PipelinePresenter.PROCESSORS);
            pipelinePresenter.getContent(PipelinePresenter.PROCESSORS, content -> ((ProcessorPresenter) content).refresh(event.getStreamProcessorFilter()));
        }));
    }

    @Override
    protected EntityEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public String getType() {
        return PipelineEntity.ENTITY_TYPE;
    }
}
