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

package stroom.entity.client.presenter;

import stroom.content.client.event.RefreshContentTabEvent;
import stroom.core.client.HasSave;
import stroom.data.table.client.Refreshable;
import stroom.docref.DocRef;
import stroom.docref.HasType;
import stroom.document.client.DocumentTabData;
import stroom.explorer.shared.DocumentType;
import stroom.svg.client.Icon;
import stroom.svg.client.Preset;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.core.client.Scheduler;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.PresenterWidget;

public abstract class DocumentEditTabPresenter2<V extends LinkTabPanelView2, D>
        extends DocumentEditPresenter<V, D> implements DocumentTabData, Refreshable, HasType, HasSave {

    private TabData selectedTab;
    private String lastLabel;
    private PresenterWidget<?> currentContent;
    private DocRef docRef;

    public DocumentEditTabPresenter2(final EventBus eventBus,
                                     final V view) {
        super(eventBus, view);
        registerHandler(getView().getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem())));
    }

    public void addTab(final TabData tab) {
        getView().getTabBar().addTab(tab);
    }

    protected abstract void getContent(TabData tab, ContentCallback callback);

    public void selectTab(final TabData tab) {
        TaskStartEvent.fire(DocumentEditTabPresenter2.this);
        Scheduler.get().scheduleDeferred(() -> {
            if (tab != null) {
                getContent(tab, content -> {
                    if (content != null) {
                        currentContent = content;

                        // Set the content.
                        getView().getLayerContainer().show((Layer) currentContent);

                        // Update the selected tab.
                        getView().getTabBar().selectTab(tab);
                        selectedTab = tab;

                        afterSelectTab(content);
                    }
                });
            }

            TaskEndEvent.fire(DocumentEditTabPresenter2.this);
        });
    }

    protected void afterSelectTab(final PresenterWidget<?> content) {
    }

    public TabData getSelectedTab() {
        return selectedTab;
    }

    @Override
    public void refresh() {
        if (selectedTab != null) {
            if (currentContent != null && currentContent instanceof Refreshable) {
                ((Refreshable) currentContent).refresh();
            }
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final D entity, final boolean readOnly) {
        this.docRef = docRef;
    }

    @Override
    public String getLabel() {
        if (isDirty()) {
            return "* " + docRef.getName();
        }

        return docRef.getName();
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public Icon getIcon() {
        return new Preset(DocumentType.DOC_IMAGE_CLASS_NAME + getType(), null, true);
    }

    @Override
    public void onDirty(final boolean dirty) {
        super.onDirty(dirty);

        // Only fire tab refresh if the tab has changed.
        if (lastLabel == null || !lastLabel.equals(getLabel())) {
            lastLabel = getLabel();
            RefreshContentTabEvent.fire(this, this);
        }
    }
}
