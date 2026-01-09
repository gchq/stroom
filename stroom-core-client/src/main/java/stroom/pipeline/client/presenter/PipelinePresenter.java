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

package stroom.pipeline.client.presenter;

import stroom.data.client.presenter.MetaPresenter;
import stroom.data.client.presenter.ProcessorTaskPresenter;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.entity.client.presenter.TabContentProvider.TabProvider;
import stroom.meta.shared.Meta;
import stroom.pipeline.client.event.ChangeDataEvent;
import stroom.pipeline.client.event.ChangeDataEvent.ChangeDataHandler;
import stroom.pipeline.client.event.HasChangeDataHandlers;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.presenter.SteppingPresenter;
import stroom.pipeline.structure.client.presenter.PipelineElementTypesFactory;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.pipeline.structure.client.presenter.PipelineModelFactory;
import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter;
import stroom.processor.client.presenter.ProcessorPresenter;
import stroom.query.api.ExpressionOperator;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.util.client.MouseUtil;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import javax.inject.Provider;

public class PipelinePresenter extends DocumentEditTabPresenter<LinkTabPanelView, PipelineDoc>
        implements ChangeDataHandler<PipelineModel>, HasChangeDataHandlers<PipelineModel> {

    public static final TabData DATA = new TabDataImpl("Data");
    public static final TabData STRUCTURE = new TabDataImpl("Structure");
    public static final TabData PROCESSORS = new TabDataImpl("Processors");
    public static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final ProcessorPresenter processorPresenter;
    private final TabProvider<PipelineDoc> structureTabProvider;
    private final TabProvider<PipelineDoc> steppingTabProvider;
    private final PipelineStructurePresenter pipelineStructurePresenter;
    private final SteppingPresenter steppingPresenter;
    private final PipelineElementTypesFactory pipelineElementTypesFactory;
    private final PipelineModelFactory pipelineModelFactory;

    private PipelineModel pipelineModel;
    private boolean doStepping = true;

    private boolean isAdmin;
    private boolean hasManageProcessorsPermission;
    private boolean initSize = false;
    private final InlineSvgToggleButton steppingModeButton;

    @Inject
    public PipelinePresenter(final EventBus eventBus,
                             final LinkTabPanelView view,
                             final Provider<MetaPresenter> metaPresenterProvider,
                             final Provider<PipelineStructurePresenter> structurePresenterProvider,
                             final ProcessorPresenter processorPresenter,
                             final Provider<ProcessorTaskPresenter> taskPresenterProvider,
                             final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                             final DocumentUserPermissionsTabProvider<PipelineDoc> documentUserPermissionsTabProvider,
                             final ClientSecurityContext securityContext,
                             final Provider<SteppingPresenter> steppingPresenterProvider,
                             final PipelineElementTypesFactory pipelineElementTypesFactory,
                             final PipelineModelFactory pipelineModelFactory) {
        super(eventBus, view);
        this.processorPresenter = processorPresenter;
        this.pipelineElementTypesFactory = pipelineElementTypesFactory;
        this.pipelineModelFactory = pipelineModelFactory;

        TabData selectedTab = null;

        if (securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)) {
            addTab(DATA, new AbstractTabProvider<PipelineDoc, MetaPresenter>(eventBus) {
                @Override
                protected MetaPresenter createPresenter() {
                    return metaPresenterProvider.get();
                }

                @Override
                public void onRead(final MetaPresenter presenter,
                                   final DocRef docRef,
                                   final PipelineDoc document,
                                   final boolean readOnly) {
                    presenter.read(docRef, document, readOnly);
                }
            });
            selectedTab = DATA;
        }
        steppingPresenter = steppingPresenterProvider.get();
        steppingTabProvider = new AbstractTabProvider<PipelineDoc, SteppingPresenter>(getEventBus()) {
            @Override
            protected SteppingPresenter createPresenter() {
                return steppingPresenter;
            }
        };

        pipelineStructurePresenter = structurePresenterProvider.get();

        structureTabProvider = new AbstractTabProvider<PipelineDoc, PipelineStructurePresenter>(getEventBus()) {
            @Override
            protected PipelineStructurePresenter createPresenter() {
                return pipelineStructurePresenter;
            }

            @Override
            public void onRead(final PipelineStructurePresenter presenter,
                               final DocRef docRef,
                               final PipelineDoc document,
                               final boolean readOnly) {
                presenter.read(docRef, document, readOnly);
            }
        };
        addTab(STRUCTURE, structureTabProvider);

        hasManageProcessorsPermission = securityContext
                .hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION);
        isAdmin = securityContext.hasAppPermission(AppPermission.ADMINISTRATOR);

        if (hasManageProcessorsPermission) {
            addTab(PROCESSORS, new AbstractTabProvider<PipelineDoc, ProcessorPresenter>(eventBus) {
                @Override
                protected ProcessorPresenter createPresenter() {
                    return processorPresenter;
                }

                @Override
                public void onRead(final ProcessorPresenter presenter,
                                   final DocRef docRef,
                                   final PipelineDoc document,
                                   final boolean readOnly) {
                    presenter.read(docRef, document, readOnly);
                    presenter.setIsAdmin(isAdmin);
                    presenter.setAllowUpdate(hasManageProcessorsPermission && !isReadOnly());
                }
            });

            addTab(TASKS, new AbstractTabProvider<PipelineDoc, ProcessorTaskPresenter>(eventBus) {
                @Override
                protected ProcessorTaskPresenter createPresenter() {
                    return taskPresenterProvider.get();
                }

                @Override
                public void onRead(final ProcessorTaskPresenter presenter,
                                   final DocRef docRef,
                                   final PipelineDoc document,
                                   final boolean readOnly) {
                    presenter.read(docRef, document, readOnly);
                }
            });

            if (selectedTab == null) {
                selectedTab = PROCESSORS;
            }
        }

        if (selectedTab == null) {
            selectedTab = STRUCTURE;
        }

        addTab(DOCUMENTATION, new MarkdownTabProvider<PipelineDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final PipelineDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public PipelineDoc onWrite(final MarkdownEditPresenter presenter,
                                       final PipelineDoc document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(selectedTab);

        steppingModeButton = new InlineSvgToggleButton();
        steppingModeButton.setSvg(SvgImage.STEP);
        steppingModeButton.setTitle("Enter Stepping Mode");
        steppingModeButton.setState(false);
        steppingModeButton.setVisible(false);
        toolbar.addButton(steppingModeButton);

        registerHandler(steppingModeButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {

                if (steppingModeButton.getState()) {
                    steppingModeButton.setTitle("Exit Stepping Mode");
                } else {
                    steppingModeButton.setTitle("Enter Stepping Mode");
                }

                setSteppingMode(steppingModeButton.getState());
            }
        }));
    }

    public void beginStepping(final StepType stepType, final StepLocation stepLocation,
                              final Meta meta, final String childStreamType) {
        steppingPresenter.beginStepping(stepType, stepLocation, meta, childStreamType);
    }

    public void setSteppingMode(final boolean steppingMode) {
        if (steppingMode) {

            replaceTab(STRUCTURE, steppingTabProvider);

            if (doStepping) {
                doStepping = false;
                if (pipelineStructurePresenter.getPipelineDoc() != null) {
                    steppingPresenter.setPipelineDoc(pipelineStructurePresenter.getPipelineDoc());
                }
                steppingPresenter.setPipelineModel(pipelineModel);
                steppingPresenter.beginStepping();
            }

            if (!initSize) {
                initSize = true;
                steppingPresenter.resize();
            }
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
    protected void onBind() {
        super.onBind();

        steppingPresenter.addPipelineChangeHandler(this::rebuildPipelineModel);
        pipelineStructurePresenter.addPipelineChangeHandler(this::rebuildPipelineModel);
    }

    public void rebuildPipelineModel(final PipelineModel model) {
        model.build();
    }

    @Override
    public void onChange(final ChangeDataEvent<PipelineModel> event) {
        this.pipelineModel = event.getData();
        doStepping = true;
        this.setDirty(true);
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
    protected void onRead(final DocRef docRef, final PipelineDoc document, final boolean readOnly) {
        super.onRead(docRef, document, readOnly);
        steppingPresenter.setPipelineDoc(document);
    }

    public void initPipelineModel(final DocRef docRef) {
        pipelineElementTypesFactory.get(this, elementTypes ->
                pipelineModelFactory.get(this, docRef, elementTypes, model -> {
                    if (pipelineModel == null) {
                        this.pipelineModel = model;
                        pipelineModel.addChangeDataHandler(this);
                        pipelineStructurePresenter.setPipelineModel(pipelineModel);
                        steppingPresenter.setPipelineModel(pipelineModel);
                    }
                    ChangeDataEvent.fire(this, model);
                })
        );
    }

    @Override
    protected PipelineDoc onWrite(final PipelineDoc document) {
        steppingPresenter.save();
        return pipelineStructurePresenter.onWrite(document);
    }

    public void setMetaListExpression(final ExpressionOperator expressionOperator) {
        steppingPresenter.setMetaListExpression(expressionOperator);
    }

    @Override
    public HandlerRegistration addChangeDataHandler(final ChangeDataHandler<PipelineModel> handler) {
        return getEventBus().addHandlerToSource(ChangeDataEvent.getType(), this, handler);
    }
}
