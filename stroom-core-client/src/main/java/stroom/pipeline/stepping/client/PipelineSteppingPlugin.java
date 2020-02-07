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


import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.core.client.presenter.Plugin;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.EntityChooser;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.MetaRowResultPage;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.GetPipelineForMetaRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResource;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.pipeline.stepping.client.presenter.SteppingContentTabPresenter;
import stroom.security.shared.DocumentPermissionNames;

public class PipelineSteppingPlugin extends Plugin implements BeginPipelineSteppingEvent.Handler {
    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);
    private static final SteppingResource STEPPING_RESOURCE = GWT.create(SteppingResource.class);

    private final Provider<EntityChooser> pipelineSelection;
    private final Provider<SteppingContentTabPresenter> editorProvider;
    private final ContentManager contentManager;
    private final RestFactory restFactory;

    @Inject
    public PipelineSteppingPlugin(final EventBus eventBus, final Provider<SteppingContentTabPresenter> editorProvider,
                                  final Provider<EntityChooser> pipelineSelection, final ContentManager contentManager,
                                  final RestFactory restFactory) {
        super(eventBus);
        this.pipelineSelection = pipelineSelection;
        this.editorProvider = editorProvider;
        this.contentManager = contentManager;
        this.restFactory = restFactory;

        registerHandler(eventBus.addHandler(BeginPipelineSteppingEvent.getType(), this));
    }

    @Override
    public void onBegin(final BeginPipelineSteppingEvent event) {
        if (event.getPipelineRef() != null) {
            choosePipeline(event.getPipelineRef(),
                    event.getStepLocation(),
                    event.getChildStreamType());
        } else {
            // If we don't have a pipeline id then try to guess one for the
            // supplied stream.
            final Rest<DocRef> rest = restFactory.create();
            rest
                    .onSuccess(result ->
                            choosePipeline(result, event.getStepLocation(), event.getChildStreamType()))
                    .call(STEPPING_RESOURCE)
                    .getPipelineForStepping(new GetPipelineForMetaRequest(event.getStreamId(), event.getChildStreamId()));
        }
    }

    private void choosePipeline(final DocRef initialPipelineRef,
                                final StepLocation stepLocation,
                                final String childStreamType) {
        final EntityChooser chooser = pipelineSelection.get();
        chooser.setCaption("Choose Pipeline To Step With");
        chooser.setIncludedTypes(PipelineDoc.DOCUMENT_TYPE);
        chooser.setRequiredPermissions(DocumentPermissionNames.READ);
        chooser.addDataSelectionHandler(event -> {
            final DocRef pipeline = chooser.getSelectedEntityReference();
            if (pipeline != null) {
                final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
                findMetaCriteria.obtainSelectedIdSet().add(stepLocation.getId());

                final Rest<MetaRowResultPage> rest = restFactory.create();
                rest
                        .onSuccess(result -> {
                            if (result != null && result.getValues().size() == 1) {
                                final MetaRow row = result.getValues().get(0);
                                openEditor(pipeline, stepLocation, row.getMeta(), childStreamType);
                            }
                        })
                        .call(META_RESOURCE).findMetaRow(findMetaCriteria);
            }
        });

        if (initialPipelineRef != null) {
            chooser.setSelectedEntityReference(initialPipelineRef);
        }

        chooser.show();
    }

    private void openEditor(final DocRef pipeline,
                            final StepLocation stepLocation,
                            final Meta meta,
                            final String childStreamType) {
        final SteppingContentTabPresenter editor = editorProvider.get();
        editor.read(pipeline, stepLocation, meta, childStreamType);
        contentManager.open(editor, editor, editor);
    }
}
