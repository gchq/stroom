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

package stroom.pipeline.client;

import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.document.client.DocumentTabData;
import stroom.entity.client.component.DocumentEditorHost;
import stroom.entity.client.presenter.DocPresenter;
import stroom.explorer.client.component.ExplorerPopupController;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.client.component.DocRefSelectionController;
import stroom.pipeline.client.component.PipelineEditor;
import stroom.pipeline.client.event.CreateProcessorEvent;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineResource;
import stroom.pipeline.shared.stepping.GetPipelineForMetaRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResource;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.processor.shared.Processor;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.AppRoot;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class PipelinePlugin extends DocumentPlugin<PipelineDoc> {

    private static final PipelineResource PIPELINE_RESOURCE = GWT.create(PipelineResource.class);
    private static final SteppingResource STEPPING_RESOURCE = GWT.create(SteppingResource.class);
    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final Provider<ExplorerPopupController> pipelineSelection;
    private final AppRoot appRoot;
    private final EventBus eventBus;
    private final Provider<PipelineEditor> editorProvider;
    private final RestFactory restFactory;
    private final Provider<DocRefSelectionController> docRefSelectionProvider;

    @Inject
    public PipelinePlugin(final EventBus eventBus,
                          final Provider<PipelineEditor> editorProvider,
                          final RestFactory restFactory,
                          final ContentManager contentManager,
                          final DocumentPluginEventManager entityPluginEventManager,
                          final ClientSecurityContext securityContext,
                          final Provider<ExplorerPopupController> pipelineSelection,
                          final Provider<DocRefSelectionController> docRefSelectionProvider,
                          final AppRoot appRoot) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.eventBus = eventBus;
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;
        this.pipelineSelection = pipelineSelection;
        this.docRefSelectionProvider = docRefSelectionProvider;
        this.appRoot = appRoot;

        getEventBus().addHandler(CreateProcessorEvent.getType(), event -> {
            final Processor processor = event.getProcessorFilter().getProcessor();
            final String pipelineUuid = processor.getPipelineUuid();
            final DocRef docRef = new DocRef(PipelineDoc.TYPE, pipelineUuid);
            // Open the item in the content pane.
            final MyPresenterWidget<?> presenter = open(docRef,
                    true,
                    false,
                    new DefaultTaskMonitorFactory(this));
            // Highlight the item in the explorer tree.
            //            highlight(docRef);

            final PipelineEditor pipelineEditor = getEditor(presenter);
            if (pipelineEditor != null) {
                pipelineEditor.showProcessorFilter(event.getProcessorFilter());
            }
        });

        getEventBus().addHandler(BeginPipelineSteppingEvent.getType(), this::onBeginStepping);
    }

    @Override
    public void save(final DocumentTabData tabData) {
        final PipelineEditor pipelineEditor = getEditor(tabData);
        if (pipelineEditor != null) {
            final List<DocRef> dirtyDocs = pipelineEditor.getDirtyDocs();
            final DocRef pipeline = pipelineEditor.getDocRef();

            if (!(dirtyDocs.size() == 1 && dirtyDocs.contains(pipeline))) {
                docRefSelectionProvider.get().show(appRoot, "Save Pipeline: " + pipeline.getName(), dirtyDocs,
                        selectedDocRefs -> {
                            final AtomicInteger completedSaves = new AtomicInteger(0);
                            final Runnable onSaved = () -> {
                                if (completedSaves.incrementAndGet() == selectedDocRefs.size()) {
                                    pipelineEditor.refreshDirty();
                                }
                            };
                            pipelineEditor.saveDocs(selectedDocRefs, onSaved);
                            if (selectedDocRefs.contains(pipeline)) {
                                super.save(tabData, onSaved);
                            }
                        });
            } else {
                super.save(tabData);
            }
        }
    }

    @Override
    protected DocPresenter<?, ?> createEditor() {
        return new DocumentEditorHost<>(eventBus, editorProvider.get(), PipelineDoc.TYPE);
    }

    // The pipeline editor a pipeline's tab shows, or null for another tab
    private static PipelineEditor getEditor(final Object tab) {
        return tab instanceof final DocumentEditorHost<?> host
               && host.getEditor() instanceof final PipelineEditor pipelineEditor
                ? pipelineEditor
                : null;
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<PipelineDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PIPELINE_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final PipelineDoc document,
                     final Consumer<PipelineDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PIPELINE_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public String getType() {
        return PipelineDoc.TYPE;
    }

    @Override
    protected DocRef getDocRef(final PipelineDoc document) {
        return DocRefUtil.create(document);
    }

    public void onBeginStepping(final BeginPipelineSteppingEvent event) {
        final ExplorerPopupController chooser = pipelineSelection.get();
        chooser.setIncludeNullSelection(false);
        chooser.setCaption("Choose Pipeline To Step With");
        chooser.setIncludedTypes(PipelineDoc.TYPE);
        chooser.setRequiredPermissions(DocumentPermission.VIEW);

        final Runnable showChooser = () -> choosePipeline(chooser,
                event.getStepType(),
                event.getStepLocation(),
                event.getChildStreamType());

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
                    .taskMonitorFactory(new DefaultTaskMonitorFactory(this))
                    .exec();
        }
    }

    private void choosePipeline(final ExplorerPopupController docRefChooserPopup,
                                final StepType stepType,
                                final StepLocation stepLocation,
                                final String childStreamType) {

        // The app owns the chooser while it is shown, as a plugin owns nothing
        docRefChooserPopup.showOnce(appRoot, pipeDocRef -> {
            if (pipeDocRef != null) {
                step(stepType, stepLocation, childStreamType, pipeDocRef);
            }
        }, PopupType.CREATE_OK_CANCEL_DIALOG);
    }

    private void step(final StepType stepType,
                      final StepLocation stepLocation,
                      final String childStreamType,
                      final DocRef pipeDocRef) {
        final FindMetaCriteria findMetaCriteria = FindMetaCriteria.createFromId(
                stepLocation.getMetaId());

        restFactory
                .create(META_RESOURCE)
                .method(res -> res.findMetaRow(findMetaCriteria))
                .onSuccess(result -> {
                    if (result != null && result.size() == 1) {
                        final MetaRow row = result.getFirst();
                        openSteppingMode(
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

    private void openSteppingMode(final DocRef pipeline,
                                  final StepType stepType,
                                  final StepLocation stepLocation,
                                  final Meta meta,
                                  final String childStreamType) {
        open(pipeline, true, false,
                false, presenter -> {
                    final PipelineEditor pipelineEditor = getEditor(presenter);
                    if (pipelineEditor == null) {
                        return;
                    }

                    if (pipelineEditor.isSteppingInit()) {
                        pipelineEditor.showSteppingMode(true);
                        pipelineEditor.setMetaListExpression(
                                MetaExpressionUtil.createDataIdExpression(meta.getId()),
                                () -> pipelineEditor.beginStepping(stepType, stepLocation, meta, childStreamType));
                    } else {
                        pipelineEditor.setSteppingMetaExpression(
                                MetaExpressionUtil.createDataIdExpression(meta.getId()));
                        pipelineEditor.showSteppingMode(true);
                        // Only begin stepping when the pipeline model has been loaded.
                        // Then remove the handler because we dont want it to run again
                        final HandlerRegistration[] registration = new HandlerRegistration[1];
                        registration[0] = pipelineEditor.addDataLoadedListener(model -> {
                            registration[0].removeHandler();
                            pipelineEditor.refreshSteppingMeta(
                                    () -> pipelineEditor.beginStepping(stepType,
                                            stepLocation,
                                            meta,
                                            childStreamType));
                        });
                    }
                }, new DefaultTaskMonitorFactory(this));
    }
}
