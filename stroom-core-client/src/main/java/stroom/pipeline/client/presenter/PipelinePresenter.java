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

package stroom.pipeline.client.presenter;

import stroom.data.client.presenter.MetaPresenter;
import stroom.data.client.presenter.ProcessorTaskPresenter;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter;
import stroom.processor.client.presenter.ProcessorPresenter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class PipelinePresenter extends DocumentEditTabPresenter<LinkTabPanelView, PipelineDoc> {

    public static final TabData DATA = new TabDataImpl("Data");
    public static final TabData STRUCTURE = new TabDataImpl("Structure");
    public static final TabData PROCESSORS = new TabDataImpl("Processors");
    public static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final MetaPresenter metaPresenter;
    private final PipelineStructurePresenter structurePresenter;
    private final ProcessorPresenter processorPresenter;
    private final ProcessorTaskPresenter taskPresenter;
    private final MarkdownEditPresenter markdownEditPresenter;

    private boolean hasManageProcessorsPermission;

    @Inject
    public PipelinePresenter(final EventBus eventBus,
                             final LinkTabPanelView view,
                             final MetaPresenter metaPresenter,
                             final PipelineStructurePresenter structurePresenter,
                             final ProcessorPresenter processorPresenter,
                             final ProcessorTaskPresenter taskPresenter,
                             final MarkdownEditPresenter markdownEditPresenter,
                             final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.metaPresenter = metaPresenter;
        this.structurePresenter = structurePresenter;
        this.processorPresenter = processorPresenter;
        this.taskPresenter = taskPresenter;
        this.markdownEditPresenter = markdownEditPresenter;

        TabData selectedTab = null;

        if (securityContext.hasAppPermission(PermissionNames.VIEW_DATA_PERMISSION)) {
            addTab(DATA);
            selectedTab = DATA;
        }

        addTab(STRUCTURE);

        if (securityContext.hasAppPermission(PermissionNames.MANAGE_PROCESSORS_PERMISSION)) {
            hasManageProcessorsPermission = true;

            addTab(PROCESSORS);
            addTab(TASKS);

            if (selectedTab == null) {
                selectedTab = PROCESSORS;
            }
        }

        if (selectedTab == null) {
            selectedTab = STRUCTURE;
        }

        addTab(DOCUMENTATION);
        selectTab(selectedTab);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(structurePresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(markdownEditPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (DATA.equals(tab)) {
            callback.onReady(metaPresenter);
        } else if (STRUCTURE.equals(tab)) {
            callback.onReady(structurePresenter);
        } else if (PROCESSORS.equals(tab)) {
            callback.onReady(processorPresenter);
        } else if (TASKS.equals(tab)) {
            callback.onReady(taskPresenter);
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final PipelineDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);

        metaPresenter.read(docRef, doc, readOnly);
        structurePresenter.read(docRef, doc, readOnly);
        processorPresenter.read(docRef, doc, readOnly);
        taskPresenter.read(docRef, doc, readOnly);
        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);

        processorPresenter.setAllowUpdate(hasManageProcessorsPermission && !readOnly);
    }

    @Override
    protected PipelineDoc onWrite(PipelineDoc doc) {
        doc = structurePresenter.write(doc);
        doc.setDescription(markdownEditPresenter.getText());
        return doc;
    }

    @Override
    public String getType() {
        return PipelineDoc.DOCUMENT_TYPE;
    }

    public ProcessorPresenter getProcessorPresenter() {
        return processorPresenter;
    }
}
