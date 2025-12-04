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

package stroom.pipeline.stepping.client;


import stroom.core.client.ContentManager;
import stroom.core.client.presenter.Plugin;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.GetPipelineForMetaRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResource;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.pipeline.stepping.client.presenter.SteppingContentTabPresenter;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.DefaultTaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class PipelineSteppingPlugin extends Plugin implements BeginPipelineSteppingEvent.Handler {

    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);
    private static final SteppingResource STEPPING_RESOURCE = GWT.create(SteppingResource.class);

    private final Provider<DocSelectionPopup> pipelineSelection;
    private final Provider<SteppingContentTabPresenter> editorProvider;
    private final ContentManager contentManager;
    private final RestFactory restFactory;

    @Inject
    public PipelineSteppingPlugin(final EventBus eventBus,
                                  final Provider<SteppingContentTabPresenter> editorProvider,
                                  final Provider<DocSelectionPopup> pipelineSelection,
                                  final ContentManager contentManager,
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
        final DocSelectionPopup chooser = pipelineSelection.get();
        chooser.setCaption("Choose Pipeline To Step With");
        chooser.setIncludedTypes(PipelineDoc.TYPE);
        chooser.setRequiredPermissions(DocumentPermission.VIEW);

        final Runnable showChooser = () -> {
            choosePipeline(chooser,
                    event.getStepType(),
                    event.getStepLocation(),
                    event.getChildStreamType());
        };

        if (event.getPipelineRef() != null) {
            chooser.setSelectedEntityReference(event.getPipelineRef(), showChooser);
        } else {
            // If we don't have a pipeline id then try to guess one for the
            // supplied stream.
            restFactory
                    .create(STEPPING_RESOURCE)
                    .method(res -> res.getPipelineForStepping(new GetPipelineForMetaRequest(
                            event.getStepLocation().getMetaId(),
                            event.getChildStreamId())))
                    .onSuccess(docRef ->
                            chooser.setSelectedEntityReference(docRef, showChooser))
                    .taskMonitorFactory(chooser)
                    .exec();
        }
    }

    private void choosePipeline(final DocSelectionPopup docRefChooserPopup,
                                final StepType stepType,
                                final StepLocation stepLocation,
                                final String childStreamType) {

        docRefChooserPopup.show(pipeDocRef -> {
            if (pipeDocRef != null) {
                final FindMetaCriteria findMetaCriteria = FindMetaCriteria.createFromId(
                        stepLocation.getMetaId());

                restFactory
                        .create(META_RESOURCE)
                        .method(res -> res.findMetaRow(findMetaCriteria))
                        .onSuccess(result -> {
                            if (result != null && result.size() == 1) {
                                final MetaRow row = result.getFirst();
                                openEditor(
                                        pipeDocRef,
                                        stepType,
                                        stepLocation,
                                        row.getMeta(),
                                        childStreamType);
                            }
                        })
                        .taskMonitorFactory(new DefaultTaskMonitorFactory(this))
                        .exec();
            }
        });
    }

    private void openEditor(final DocRef pipeline,
                            final StepType stepType,
                            final StepLocation stepLocation,
                            final Meta meta,
                            final String childStreamType) {
        final SteppingContentTabPresenter editor = editorProvider.get();
        editor.read(pipeline, stepType, stepLocation, meta, childStreamType);
        contentManager.open(editor, editor, editor);
    }
}
