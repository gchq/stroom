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
 *
 */

package stroom.pipeline.client;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.pipeline.client.event.CreateProcessorEvent;
import stroom.pipeline.client.presenter.PipelinePresenter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineResource;
import stroom.processor.client.presenter.ProcessorPresenter;
import stroom.processor.shared.Processor;

import java.util.function.Consumer;

public class PipelinePlugin extends DocumentPlugin<PipelineDoc> {
    private static final PipelineResource PIPELINE_RESOURCE = GWT.create(PipelineResource.class);

    private final Provider<PipelinePresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public PipelinePlugin(final EventBus eventBus,
                          final Provider<PipelinePresenter> editorProvider,
                          final RestFactory restFactory,
                          final ContentManager contentManager,
                          final DocumentPluginEventManager entityPluginEventManager) {
        super(eventBus, contentManager, entityPluginEventManager);
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getEventBus().addHandler(CreateProcessorEvent.getType(), event -> {
            final Processor processor = event.getProcessorFilter().getProcessor();
            final String pipelineUuid = processor.getPipelineUuid();
            final DocRef docRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, pipelineUuid);
            // Open the item in the content pane.
            final PipelinePresenter pipelinePresenter = (PipelinePresenter) open(docRef, true);
            // Highlight the item in the explorer tree.
            //            highlight(docRef);

            pipelinePresenter.selectTab(PipelinePresenter.PROCESSORS);
            pipelinePresenter.getContent(PipelinePresenter.PROCESSORS, content -> ((ProcessorPresenter) content).refresh(event.getProcessorFilter()));
        }));
    }

    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public void load(final DocRef docRef, final Consumer<PipelineDoc> resultConsumer, final Consumer<Throwable> errorConsumer) {
        final Rest<PipelineDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(PIPELINE_RESOURCE)
                .read(docRef);
    }

    @Override
    public void save(final DocRef docRef, final PipelineDoc document, final Consumer<PipelineDoc> resultConsumer, final Consumer<Throwable> errorConsumer) {
        final Rest<PipelineDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(PIPELINE_RESOURCE)
                .update(document);
    }

    @Override
    public String getType() {
        return PipelineDoc.DOCUMENT_TYPE;
    }

    @Override
    protected DocRef getDocRef(final PipelineDoc document) {
        return DocRefUtil.create(document);
    }
}
