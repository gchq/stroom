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

package stroom.feed.client.presenter;

import stroom.data.client.presenter.MetaPresenter;
import stroom.data.client.presenter.ProcessorTaskPresenter;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class FeedPresenter extends DocumentEditTabPresenter<LinkTabPanelView, FeedDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final FeedSettingsPresenter settingsPresenter;
    private final MetaPresenter metaPresenter;
    private final ProcessorTaskPresenter taskPresenter;
    private final MarkdownEditPresenter markdownEditPresenter;

    @Inject
    public FeedPresenter(final EventBus eventBus,
                         final LinkTabPanelView view,
                         final ClientSecurityContext securityContext,
                         final FeedSettingsPresenter settingsPresenter,
                         final MetaPresenter metaPresenter,
                         final ProcessorTaskPresenter taskPresenter,
                         final MarkdownEditPresenter markdownEditPresenter) {
        super(eventBus, view);
        this.settingsPresenter = settingsPresenter;
        this.metaPresenter = metaPresenter;
        this.taskPresenter = taskPresenter;
        this.markdownEditPresenter = markdownEditPresenter;

        TabData selectedTab = SETTINGS;

        if (securityContext.hasAppPermission(PermissionNames.VIEW_DATA_PERMISSION)) {
            addTab(DATA);
            selectedTab = DATA;
        }

        if (securityContext.hasAppPermission(PermissionNames.MANAGE_PROCESSORS_PERMISSION)) {
            addTab(TASKS);
        }

        addTab(SETTINGS);
        addTab(DOCUMENTATION);

        selectTab(selectedTab);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(settingsPresenter.addDirtyHandler(event -> {
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
        if (SETTINGS.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (DATA.equals(tab)) {
            callback.onReady(metaPresenter);
        } else if (TASKS.equals(tab)) {
            callback.onReady(taskPresenter);
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, FeedDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        settingsPresenter.read(docRef, doc, readOnly);
        metaPresenter.read(docRef, doc, readOnly);
        taskPresenter.read(docRef, doc, readOnly);
        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);
    }

    @Override
    protected FeedDoc onWrite(FeedDoc doc) {
        doc = settingsPresenter.write(doc);
        doc.setDescription(markdownEditPresenter.getText());

        // Something has changed, e.g. the encoding so refresh the meta presenter to reflect it
        metaPresenter.refreshData();

        return doc;
    }

    @Override
    public String getType() {
        return FeedDoc.DOCUMENT_TYPE;
    }
}
