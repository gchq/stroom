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

package stroom.pipeline.client.presenter;

import stroom.data.client.presenter.ExpressionValidator;
import stroom.data.client.presenter.MetaPresenter;
import stroom.data.client.presenter.ProcessorTaskPresenter;
import stroom.docref.DocRef;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.entity.client.presenter.TabContentProvider.TabProvider;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.presenter.SteppingPresenter;
import stroom.pipeline.structure.client.component.PipelineStructureController;
import stroom.pipeline.structure.client.presenter.PipelineElementTypesFactory;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.pipeline.structure.client.presenter.PipelineModelFactory;
import stroom.processor.client.presenter.ProcessorPresenter;
import stroom.query.api.ExpressionOperator;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Listeners;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.List;
import java.util.function.Consumer;

public class PipelinePresenter extends DocTabPresenter<LinkTabPanelView, PipelineDoc> {

    public static final TabData DATA = new TabDataImpl("Data");
    public static final TabData STRUCTURE = new TabDataImpl("Structure");
    public static final TabData PROCESSORS = new TabDataImpl("Processors");
    public static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final ProcessorPresenter processorPresenter;
    private final TabProvider<PipelineDoc> structureTabProvider;
    private final TabProvider<PipelineDoc> steppingTabProvider;
    private final PipelineStructureController pipelineStructure;
    private final SteppingPresenter steppingPresenter;
    private final PipelineElementTypesFactory pipelineElementTypesFactory;
    private final PipelineModelFactory pipelineModelFactory;

    private PipelineModel pipelineModel;
    private boolean steppingMetaListLoaded = false;

    private final ToolbarToggleButton steppingModeButton;

    private ExpressionOperator steppingMetaExpression = null;

    // Told when the model is loaded, e.g. to begin stepping once it is
    private final Listeners<PipelineModel> dataLoadedListeners = new Listeners<>();

    @Inject
    public PipelinePresenter(final EventBus eventBus,
                             final LinkTabPanelView view,
                             final Provider<MetaPresenter> metaPresenterProvider,
                             final Provider<PipelineStructureController> structureProvider,
                             final ProcessorPresenter processorPresenter,
                             final Provider<ProcessorTaskPresenter> taskPresenterProvider,
                             final Provider<MarkdownEditController> markdownEditProvider,
                             final DocumentUserPermissionsTabProvider<PipelineDoc> documentUserPermissionsTabProvider,
                             final ClientSecurityContext securityContext,
                             final Provider<SteppingPresenter> steppingPresenterProvider,
                             final PipelineElementTypesFactory pipelineElementTypesFactory,
                             final PipelineModelFactory pipelineModelFactory) {
        super(eventBus, view);
        own(processorPresenter);
        this.processorPresenter = processorPresenter;
        this.pipelineElementTypesFactory = pipelineElementTypesFactory;
        this.pipelineModelFactory = pipelineModelFactory;

        TabData defaultTab = null;

        if (securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)) {
            addTab(DATA, new AbstractTabProvider<PipelineDoc, MetaPresenter>() {
                @Override
                protected MetaPresenter createPresenter() {
                    return metaPresenterProvider.get();
                }

                @Override
                public void onRead(final MetaPresenter presenter,
                                   final DocRef docRef,
                                   final PipelineDoc document) {
                    presenter.read(docRef, document);
                }
            });
            defaultTab = DATA;
        }

        steppingPresenter = make(steppingPresenterProvider);
        pipelineStructure = make(structureProvider);

        steppingTabProvider = new AbstractTabProvider<PipelineDoc, SteppingPresenter>() {
            @Override
            protected SteppingPresenter createPresenter() {
                return steppingPresenter;
            }

            @Override
            public void onRead(final SteppingPresenter presenter,
                               final DocRef docRef,
                               final PipelineDoc document) {
                loadPipelineModel(docRef, model -> {
                    steppingPresenter.setPipelineModel(model);
                    steppingPresenter.setPipelineDoc(document);
                });

                if (!steppingMetaListLoaded && steppingMetaExpression == null) {
                    steppingMetaListLoaded = true;
                    setMetaListExpression(ExpressionValidator.ALL_UNLOCKED_EXPRESSION, null);
                }
            }
        };

        // Owned here, as the stepping tab asks it whether the structure is dirty; its edits reach
        // this presenter's onChange() through its markChanged()
        structureTabProvider = ControllerTabProvider.ofOwned(eventBus, pipelineStructure,
                (structure, docRef, document) -> {
                    structure.read(docRef, document);
                    loadPipelineModel(docRef, structure::setPipelineModel);
                },
                PipelineStructureController::write);
        addTab(STRUCTURE, structureTabProvider);

        final boolean hasManageProcessorsPermission = securityContext.hasAppPermission(
                AppPermission.MANAGE_PROCESSORS_PERMISSION);

        if (hasManageProcessorsPermission) {
            addTab(PROCESSORS, new AbstractTabProvider<PipelineDoc, ProcessorPresenter>() {
                @Override
                protected ProcessorPresenter createPresenter() {
                    return processorPresenter;
                }

                @Override
                public void onRead(final ProcessorPresenter presenter,
                                   final DocRef docRef,
                                   final PipelineDoc document) {
                    presenter.read(docRef, document);
                    presenter.setIsAdmin(securityContext.hasAppPermission(AppPermission.ADMINISTRATOR));
                    presenter.setAllowUpdate(!isReadOnly());
                }
            });

            addTab(TASKS, new AbstractTabProvider<PipelineDoc, ProcessorTaskPresenter>() {
                @Override
                protected ProcessorTaskPresenter createPresenter() {
                    return taskPresenterProvider.get();
                }

                @Override
                public void onRead(final ProcessorTaskPresenter presenter,
                                   final DocRef docRef,
                                   final PipelineDoc document) {
                    presenter.read(docRef, document);
                }
            });

            if (defaultTab == null) {
                defaultTab = PROCESSORS;
            }
        }

        if (defaultTab == null) {
            defaultTab = STRUCTURE;
        }

        addTab(DOCUMENTATION, new MarkdownTabProvider<PipelineDoc>(getEventBus(), markdownEditProvider::get) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final PipelineDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public PipelineDoc onWrite(final MarkdownEditController markdownEdit,
                                       final PipelineDoc document) {
                return document.copy().description(markdownEdit.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        setDefaultTab(defaultTab);

        steppingModeButton = toolbar.toggle(SvgImage.STEP, "Stepping Mode", this::onSteppingModeToggled);
        steppingModeButton.setVisible(false);

        // Editing an element's code (e.g. XSLT) while stepping does not change the PipelineDoc itself,
        // so onChange()'s structure comparison won't detect it. The stepping presenter calls its
        // change callback on each edit; route it directly to onChange() so the pipeline's Save button is
        // re-evaluated via hasAssociatedDirty() (which checks steppingPresenter.getDirtyDocs()).
        // Note that this must not touch the pipeline model: element code is not part of the pipeline
        // structure, and rebuilding on each keypress makes the tree flash and the editor lag.
        steppingPresenter.setOnChange(this::onChange);
    }

    private void onSteppingModeToggled(final boolean steppingMode) {
        showSteppingMode(steppingMode);
    }

    public void beginStepping(final StepType stepType, final StepLocation stepLocation,
                              final Meta meta, final String childStreamType) {
        steppingPresenter.beginStepping(stepType, stepLocation, meta, childStreamType);
    }

    public void showSteppingMode(final boolean steppingMode) {
        if (steppingMode) {
            replaceTab(STRUCTURE, steppingTabProvider);
        } else {
            replaceTab(STRUCTURE, structureTabProvider);
        }

        steppingModeButton.setState(steppingMode);
    }

    @Override
    public void selectTab(final TabData tab) {
        super.selectTab(tab);

        if (steppingModeButton != null) {
            steppingModeButton.setVisible(STRUCTURE.equals(tab));
        }
    }

    @Override
    public String getType() {
        return PipelineDoc.TYPE;
    }

    public ProcessorPresenter getProcessorPresenter() {
        return processorPresenter;
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }

    @Override
    protected void onRead(final DocRef docRef, final PipelineDoc document) {
        super.onRead(docRef, document);
    }

    public void loadPipelineModel(final DocRef docRef, final Consumer<PipelineModel> consumer) {
        pipelineElementTypesFactory.get(this, elementTypes ->
                pipelineModelFactory.get(this, docRef, elementTypes, model -> {
                    if (pipelineModel == null) {
                        this.pipelineModel = model;

                        // The structure presenter recomputes dirty from the same event, so this
                        // only has to keep the stepping view in step with the model.
                        pipelineModel.addChangeListener(changed -> {
                            this.pipelineModel = changed;
                            steppingPresenter.setPipelineModel(pipelineModel);
                        });
                    }

                    consumer.accept(model);
                    dataLoadedListeners.fire(model);
                })
        );
    }

    public void setSteppingMetaExpression(final ExpressionOperator expression) {
        steppingMetaExpression = expression;
    }

    public void refreshSteppingMeta(final Runnable runnable) {
        if (steppingMetaExpression != null) {
            setMetaListExpression(steppingMetaExpression, runnable);
        }
    }

    public boolean isSteppingInit() {
        return steppingPresenter.getPipelineDoc() != null;
    }

    public List<DocRef> getDirtyDocs() {
        final List<DocRef> dirtyDocs = steppingPresenter.getDirtyDocs();

        if (pipelineStructure.isDirty()) {
            dirtyDocs.add(docRef);
        }

        return dirtyDocs;
    }

    public void saveDocs(final List<DocRef> docRefs, final Runnable onComplete) {
        steppingPresenter.save(docRefs, onComplete);
    }

    @Override
    protected boolean hasAssociatedDirty() {
        return !steppingPresenter.getDirtyDocs().isEmpty();
    }

    @Override
    protected PipelineDoc onWrite(final PipelineDoc document) {
        // Write via the tab content provider so that every tab contributes, including Documentation.
        return super.onWrite(document);
    }

    public void setMetaListExpression(final ExpressionOperator expressionOperator, final Runnable afterSet) {
        steppingPresenter.setMetaListExpression(expressionOperator, afterSet);
    }

    /// Listens for the pipeline's model being loaded (when the pipeline is read), until the
    /// registration is removed.
    ///
    /// @param listener Told the model.
    /// @return The registration.
    public HandlerRegistration addDataLoadedListener(final Consumer<PipelineModel> listener) {
        return dataLoadedListeners.add(listener);
    }
}
