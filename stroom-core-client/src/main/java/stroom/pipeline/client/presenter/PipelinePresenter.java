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
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter;
import stroom.processor.client.presenter.ProcessorPresenter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.security.shared.AppPermission;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class PipelinePresenter extends DocumentEditTabPresenter<LinkTabPanelView, PipelineDoc> {

    public static final TabData DATA = new TabDataImpl("Data");
    public static final TabData STRUCTURE = new TabDataImpl("Structure");
    public static final TabData PROCESSORS = new TabDataImpl("Processors");
    public static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final ProcessorPresenter processorPresenter;

    private boolean isAdmin;
    private boolean hasManageProcessorsPermission;

    @Inject
    public PipelinePresenter(final EventBus eventBus,
                             final LinkTabPanelView view,
                             final Provider<MetaPresenter> metaPresenterProvider,
                             final Provider<PipelineStructurePresenter> structurePresenterProvider,
                             final ProcessorPresenter processorPresenter,
                             final Provider<ProcessorTaskPresenter> taskPresenterProvider,
                             final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                             final DocumentUserPermissionsTabProvider<PipelineDoc> documentUserPermissionsTabProvider,
                             final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.processorPresenter = processorPresenter;

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

        addTab(STRUCTURE, new DocumentEditTabProvider<>(structurePresenterProvider::get));

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
}
