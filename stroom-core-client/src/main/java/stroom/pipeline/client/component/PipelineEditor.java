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

package stroom.pipeline.client.component;

import stroom.data.client.ExpressionValidator;
import stroom.data.client.component.MetaBrowserController;
import stroom.data.client.component.ProcessorTasksTabController;
import stroom.docref.DocRef;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.MarkdownEditController;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.component.SteppingScreenController;
import stroom.pipeline.structure.client.PipelineElementTypesFactory;
import stroom.pipeline.structure.client.PipelineModel;
import stroom.pipeline.structure.client.PipelineModelFactory;
import stroom.pipeline.structure.client.component.PipelineStructureController;
import stroom.processor.client.component.ProcessorsController;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionOperator;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Listeners;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.List;
import java.util.function.Consumer;

/// A pipeline's editor: its Data (for a user who may view data), Structure, Processors and Active
/// Tasks (for a user who may manage processors), Documentation and Permissions tabs (see
/// [DocumentEditor]). It opens on Data, or Processors, or Structure, the first the user may see.
///
/// With the Structure tab showing, Stepping Mode shows the stepping screen in its place. The
/// structure and the stepping screen share the pipeline's model, and the documents edited while
/// stepping (e.g. an XSLT) make the pipeline dirty too, as they are saved with it.
public class PipelineEditor extends DocumentEditor<PipelineDoc> {

    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData STRUCTURE = new TabDataImpl("Structure");
    private static final TabData PROCESSORS = new TabDataImpl("Processors");
    private static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final ProcessorsController processors;
    private final PipelineStructureController pipelineStructure;
    private final SteppingScreenController stepping;
    private final PipelineElementTypesFactory pipelineElementTypesFactory;
    private final PipelineModelFactory pipelineModelFactory;
    private final ToolbarToggleButton steppingModeButton;
    // Told when the model is loaded, e.g. to begin stepping once it is
    private final Listeners<PipelineModel> dataLoadedListeners = new Listeners<>();

    private PipelineModel pipelineModel;
    private boolean steppingMetaListLoaded;
    private ExpressionOperator steppingMetaExpression;

    /// Makes the editor, with the tabs the user may see.
    ///
    /// @param eventBus                    The application's event bus.
    /// @param view                        Its component.
    /// @param metaBrowserProvider         Makes the meta browser.
    /// @param structureProvider           Makes the structure.
    /// @param processors                  The processors.
    /// @param taskControllerProvider      Makes the task controller.
    /// @param markdownEditProvider        Makes the Documentation tab's editor.
    /// @param permissionsProvider         Makes the Permissions tab.
    /// @param securityContext             The user's permissions.
    /// @param steppingProvider            Makes the stepping.
    /// @param pipelineElementTypesFactory The pipeline element types factory.
    /// @param pipelineModelFactory        The pipeline model factory.
    @Inject
    public PipelineEditor(final EventBus eventBus,
                          final LinkTabPanel view,
                          final Provider<MetaBrowserController> metaBrowserProvider,
                          final Provider<PipelineStructureController> structureProvider,
                          final ProcessorsController processors,
                          final Provider<ProcessorTasksTabController> taskControllerProvider,
                          final Provider<MarkdownEditController> markdownEditProvider,
                          final Provider<DocumentUserPermissionsTabController> permissionsProvider,
                          final ClientSecurityContext securityContext,
                          final Provider<SteppingScreenController> steppingProvider,
                          final PipelineElementTypesFactory pipelineElementTypesFactory,
                          final PipelineModelFactory pipelineModelFactory) {
        super(eventBus, view);
        this.processors = own(processors);
        this.pipelineElementTypesFactory = pipelineElementTypesFactory;
        this.pipelineModelFactory = pipelineModelFactory;

        TabData defaultTab = null;
        if (securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)) {
            addTab(DATA,
                    metaBrowserProvider::get,
                    (metaBrowser, docRef, doc) -> metaBrowser.read(docRef, doc));
            defaultTab = DATA;
        }

        stepping = make(steppingProvider);
        // Owned here, as the stepping screen asks it whether the structure is dirty; its edits
        // reach this editor through its markChanged()
        pipelineStructure = make(structureProvider);
        addOwnedTab(STRUCTURE,
                pipelineStructure,
                this::readStructure,
                PipelineStructureController::write);

        if (securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION)) {
            addOwnedTab(PROCESSORS,
                    processors,
                    (controller, docRef, doc) -> {
                        controller.read(docRef, doc);
                        controller.setIsAdmin(securityContext.hasAppPermission(AppPermission.ADMINISTRATOR));
                        controller.setAllowUpdate(!context(Contexts.READ_ONLY));
                    });
            addTab(TASKS,
                    taskControllerProvider::get,
                    (tasks, docRef, doc) -> tasks.read(docRef, doc));
            if (defaultTab == null) {
                defaultTab = PROCESSORS;
            }
        }
        if (defaultTab == null) {
            defaultTab = STRUCTURE;
        }

        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                PipelineDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);

        steppingModeButton = getToolbar().toggle(SvgImage.STEP, "Stepping Mode", this::showSteppingMode);
        steppingModeButton.setVisible(false);

        // Editing an element's code (e.g. XSLT) while stepping doesn't change the pipeline itself,
        // so comparing it won't find the change. The stepping screen calls its change callback on
        // each edit, so the pipeline's Save is worked out again with hasAssociatedDirty() (which
        // asks the stepping screen for its dirty documents). This must not touch the pipeline
        // model: element code isn't part of the pipeline's structure, and rebuilding it on each key
        // press makes the tree flash and the editor lag.
        stepping.setOnChange(this::onChange);

        setDefaultTab(defaultTab);
    }

    /// Shows the Processors tab with a processor filter, e.g. one just made, if the user may
    /// manage processors (only they have the tab).
    ///
    /// @param processorFilter The filter, to show and select.
    public void showProcessorFilter(final ProcessorFilter processorFilter) {
        if (hasTab(PROCESSORS)) {
            selectTab(PROCESSORS);
            processors.refresh(processorFilter);
        }
    }

    /// Shows the stepping screen in place of the structure, or the structure again.
    ///
    /// @param steppingMode Whether to show the stepping screen.
    public void showSteppingMode(final boolean steppingMode) {
        if (steppingMode) {
            replaceTab(STRUCTURE, stepping, this::readStepping, (screen, doc) -> doc);
        } else {
            replaceTab(STRUCTURE, pipelineStructure, this::readStructure, PipelineStructureController::write);
        }
        steppingModeButton.setState(steppingMode);
    }

    /// Steps through a stream.
    ///
    /// @param stepType        Where to step to.
    /// @param stepLocation    Where to start.
    /// @param meta            The stream.
    /// @param childStreamType The child stream type to step, or null.
    public void beginStepping(final StepType stepType,
                              final StepLocation stepLocation,
                              final Meta meta,
                              final String childStreamType) {
        stepping.beginStepping(stepType, stepLocation, meta, childStreamType);
    }

    /// @param expression Which streams the stepping screen lists, once it is first shown.
    public void setSteppingMetaExpression(final ExpressionOperator expression) {
        steppingMetaExpression = expression;
    }

    /// Lists the streams [#setSteppingMetaExpression] said, if it said any.
    ///
    /// @param afterSet What to run once they are listed.
    public void refreshSteppingMeta(final Runnable afterSet) {
        if (steppingMetaExpression != null) {
            setMetaListExpression(steppingMetaExpression, afterSet);
        }
    }

    /// @param expressionOperator Which streams the stepping screen lists.
    /// @param afterSet           What to run once they are listed.
    public void setMetaListExpression(final ExpressionOperator expressionOperator, final Runnable afterSet) {
        stepping.setMetaListExpression(expressionOperator, afterSet);
    }

    /// @return Whether the stepping screen has been shown this pipeline.
    public boolean isSteppingInit() {
        return stepping.getPipelineDoc() != null;
    }

    /// @return The documents with changes that aren't saved: those edited while stepping, and the
    /// pipeline if its structure has changed.
    public List<DocRef> getDirtyDocs() {
        final List<DocRef> dirtyDocs = stepping.getDirtyDocs();
        if (pipelineStructure.isDirty()) {
            dirtyDocs.add(getDocRef());
        }
        return dirtyDocs;
    }

    /// Saves documents edited while stepping.
    ///
    /// @param docRefs    The documents.
    /// @param onComplete What to run once each is saved.
    public void saveDocs(final List<DocRef> docRefs, final Runnable onComplete) {
        stepping.save(docRefs, onComplete);
    }

    /// Works out again whether the pipeline is dirty, e.g. once documents edited while stepping are
    /// saved.
    public void refreshDirty() {
        onChange();
    }

    /// Listens for the pipeline's model being loaded (when the pipeline is read), until the
    /// registration is removed.
    ///
    /// @param listener Told the model.
    /// @return The registration.
    public HandlerRegistration addDataLoadedListener(final Consumer<PipelineModel> listener) {
        return dataLoadedListeners.add(listener);
    }

    @Override
    protected void afterSelectTab(final TabData tab) {
        steppingModeButton.setVisible(STRUCTURE.equals(tab));
    }

    @Override
    protected boolean hasAssociatedDirty() {
        return !stepping.getDirtyDocs().isEmpty();
    }

    private void readStructure(final PipelineStructureController structure,
                               final DocRef docRef,
                               final PipelineDoc doc) {
        structure.read(docRef, doc);
        loadPipelineModel(docRef, structure::setPipelineModel);
    }

    private void readStepping(final SteppingScreenController screen,
                              final DocRef docRef,
                              final PipelineDoc doc) {
        loadPipelineModel(docRef, model -> {
            screen.setPipelineModel(model);
            screen.setPipelineDoc(doc);
        });
        if (!steppingMetaListLoaded && steppingMetaExpression == null) {
            steppingMetaListLoaded = true;
            setMetaListExpression(ExpressionValidator.ALL_UNLOCKED_EXPRESSION, null);
        }
    }

    private void loadPipelineModel(final DocRef docRef, final Consumer<PipelineModel> consumer) {
        pipelineElementTypesFactory.get(taskMonitor(), elementTypes ->
                pipelineModelFactory.get(taskMonitor(), docRef, elementTypes, guard(model -> {
                    if (pipelineModel == null) {
                        pipelineModel = model;
                        // The structure works out whether it is dirty from the same event, so this
                        // only has to keep the stepping screen in step with the model
                        pipelineModel.addChangeListener(changed -> {
                            pipelineModel = changed;
                            stepping.setPipelineModel(pipelineModel);
                        });
                    }
                    consumer.accept(model);
                    dataLoadedListeners.fire(model);
                })));
    }

}
