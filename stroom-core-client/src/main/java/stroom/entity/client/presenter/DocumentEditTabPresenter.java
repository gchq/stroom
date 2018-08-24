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

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.PresenterWidget;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.data.table.client.Refreshable;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.WriteDocumentEvent;
import stroom.explorer.shared.DocumentType;
import stroom.docref.DocRef;
import stroom.security.client.ClientSecurityContext;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.util.client.ImageUtil;
import stroom.util.shared.HasType;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import com.gwtplatform.mvp.client.Layer;
import stroom.widget.tab.client.presenter.TabData;

import java.util.ArrayList;
import java.util.List;

public abstract class DocumentEditTabPresenter<V extends LinkTabPanelView, D>
        extends DocumentEditPresenter<V, D> implements DocumentTabData, Refreshable, HasType {
    private final List<TabData> tabs = new ArrayList<>();
    private final ButtonView saveButton;
    private TabData selectedTab;
    private String lastLabel;
    private ButtonPanel leftButtons;
    private ButtonPanel rightButtons;
    private PresenterWidget<?> currentContent;
    private DocRef docRef;

    public DocumentEditTabPresenter(final EventBus eventBus, final V view,
                                    final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);

        saveButton = addButtonLeft(SvgPresets.SAVE);
        saveButton.setEnabled(false);

        registerHandler(saveButton.addClickHandler(event -> {
            if (saveButton.isEnabled()) {
                WriteDocumentEvent.fire(DocumentEditTabPresenter.this, DocumentEditTabPresenter.this);
            }
        }));
        registerHandler(getView().getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem())));
    }


//    public ImageButtonView addButtonLeft(final String title, final ImageResource enabledImage,
//                                         final ImageResource disabledImage) {
//        if (leftButtons == null) {
//            leftButtons = new ButtonPanel();
//            addWidgetLeft(leftButtons);
//        }
//
//        final ImageButtonView button = leftButtons.add(title, enabledImage, disabledImage, true);
//        return button;
//    }

    public ButtonView addButtonLeft(final SvgPreset preset) {
        if (leftButtons == null) {
            leftButtons = new ButtonPanel();
//            leftButtons.getElement().getStyle().setPaddingLeft(1, Style.Unit.PX);
            addWidgetLeft(leftButtons);
        }

        return leftButtons.add(preset);
    }

//    public ImageButtonView addButtonRight(final String title, final ImageResource enabledImage,
//                                          final ImageResource disabledImage) {
//        if (rightButtons == null) {
//            rightButtons = new ButtonPanel();
//            leftButtons.getElement().getStyle().setPaddingRight(1, Style.Unit.PX);
//            addWidgetLeft(rightButtons);
//        }
//
//        final ImageButtonView button = rightButtons.add(title, enabledImage, disabledImage, true);
//        return button;
//    }

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
        return new SvgPreset(ImageUtil.getImageURL() + DocumentType.DOC_IMAGE_URL + getType() + ".svg", null, true);
    }

    @Override
    public void onPermissionsCheck(final boolean readOnly) {
        super.onPermissionsCheck(readOnly);
        saveButton.setEnabled(isDirty() && !readOnly);
    }

    @Override
    public void onDirty(final boolean dirty) {
        if (!isReadOnly()) {
            // Only fire tab refresh if the tab has changed.
            if (lastLabel == null || !lastLabel.equals(getLabel())) {
                lastLabel = getLabel();
                RefreshContentTabEvent.fire(this, this);
            }

            saveButton.setEnabled(dirty);
        }
    }
}
