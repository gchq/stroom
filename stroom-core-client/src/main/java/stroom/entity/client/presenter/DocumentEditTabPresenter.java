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
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.HasType;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.SaveAsDocumentEvent;
import stroom.document.client.event.WriteDocumentEvent;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerResource;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.client.Icon;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.ArrayList;
import java.util.List;

public abstract class DocumentEditTabPresenter<V extends LinkTabPanelView, D>
        extends DocumentEditPresenter<V, D> implements DocumentTabData, Refreshable, HasType, HasSave {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final List<TabData> tabs = new ArrayList<>();
    private final ButtonView saveButton;
    private final ButtonView saveAsButton;
    private final RestFactory restFactory;
    private TabData selectedTab;
    private String lastLabel;
    private ButtonPanel leftButtons;
    private ButtonPanel rightButtons;
    private PresenterWidget<?> currentContent;
    private DocRef docRef;

    public DocumentEditTabPresenter(final EventBus eventBus,
                                    final V view,
                                    final ClientSecurityContext securityContext,
                                    final RestFactory restFactory) {
        super(eventBus, view, securityContext);

        this.restFactory = restFactory;

        saveButton = addButtonLeft(SvgPresets.SAVE);
        saveAsButton = addButtonLeft(SvgPresets.SAVE_AS);
        saveButton.setEnabled(false);
        saveAsButton.setEnabled(false);

        registerHandler(saveButton.addClickHandler(event -> save()));
        registerHandler(saveAsButton.addClickHandler(this::onSaveAsDocument));
        registerHandler(getView().getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem())));
    }

    @Override
    public void save() {
        if (saveButton.isEnabled()) {
            WriteDocumentEvent.fire(DocumentEditTabPresenter.this, DocumentEditTabPresenter.this);
        }
    }

    private void onSaveAsDocument(ClickEvent event) {
        if (saveAsButton.isEnabled()) {
            restFactory.create()
                    .onSuccess(explorerNode ->
                            SaveAsDocumentEvent.fire(DocumentEditTabPresenter.this, (ExplorerNode) explorerNode))
                    .call(EXPLORER_RESOURCE)
                    .getFromDocRef(docRef);
        }
    }

    public ButtonView addButtonLeft(final Preset preset) {
        if (leftButtons == null) {
            leftButtons = new ButtonPanel();
//            leftButtons.getElement().getStyle().setPaddingLeft(1, Style.Unit.PX);
            addWidgetLeft(leftButtons);
        }

        return leftButtons.addButton(preset);
    }

    public void addTab(final TabData tab) {
        getView().getTabBar().addTab(tab);
        tabs.add(tab);
    }

    protected abstract void getContent(TabData tab, ContentCallback callback);

    public void addWidgetLeft(final Widget widget) {
        getView().addWidgetLeft(widget);
    }

    public void addWidgetRight(final Widget widget) {
        getView().addWidgetRight(widget);
    }

    public void selectTab(final TabData tab) {
        TaskStartEvent.fire(DocumentEditTabPresenter.this);
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

            TaskEndEvent.fire(DocumentEditTabPresenter.this);
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
    protected void onRead(final DocRef docRef, final D entity) {
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
    public void onReadOnly(final boolean readOnly) {
        super.onReadOnly(readOnly);
        saveButton.setEnabled(isDirty());
        saveAsButton.setEnabled(true);
        if (readOnly) {
            saveButton.setTitle("Save is not available as this document is read only");
        }
    }

    @Override
    public void onDirtyChange() {
        // Only fire tab refresh if the tab has changed.
        if (lastLabel == null || !lastLabel.equals(getLabel())) {
            lastLabel = getLabel();
            RefreshContentTabEvent.fire(this, this);
        }
        saveButton.setEnabled(isDirty());
    }
}
