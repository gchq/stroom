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

package stroom.entity.client.presenter;

import stroom.content.client.event.RefreshContentTabEvent;
import stroom.core.client.HasSave;
import stroom.data.table.client.Refreshable;
import stroom.docref.DocRef;
import stroom.docref.HasType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.document.client.event.SaveAsDocumentEvent;
import stroom.document.client.event.SaveDocumentEvent;
import stroom.entity.client.presenter.TabContentProvider.TabProvider;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.SvgButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class DocTabPresenter<V extends LinkTabPanelView, D>
        extends DocPresenter<V, D>
        implements DocumentTabData, Refreshable, HasType, HasSave {

    private final ButtonView saveButton;
    private final ButtonView saveAsButton;
    private TabData selectedTab;
    private String lastLabel;
    protected final ButtonPanel toolbar;
    // Why a document is read only: it is only ever opened read only for a user who can't edit it
    private static final String READ_ONLY_REASON = "You don't have permission to change this";

    // Says that the document is read only, at the far right of the tab bar; a polite status, so
    // screen readers announce it (and why) when a read-only document opens
    private final HTML readOnlyStatus = new HTML();
    private PresenterWidget<?> currentContent;
    protected DocRef docRef;
    private TabData defaultTab;

    private final TabContentProvider<D> tabContentProvider;
    private final Map<CommonDocLinkTab, TabData> commonTabsMap;

    public DocTabPresenter(final EventBus eventBus,
                           final V view) {
        super(eventBus, view);
        saveButton = SvgButton.create(SvgPresets.SAVE);
        saveAsButton = SvgButton.create(SvgPresets.SAVE_AS);
        saveButton.setEnabled(false);
        saveAsButton.setEnabled(false);

        toolbar = createToolbar();
        readOnlyStatus.setStyleName("docTab-readOnlyStatus");
        readOnlyStatus.getElement().setAttribute("role", "status");
        getView().setTabBarStatus(readOnlyStatus);

        registerHandler(getView().getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem())));

        tabContentProvider = new TabContentProvider<>(eventBus);

        commonTabsMap = new EnumMap<>(CommonDocLinkTab.class);
        addEntry(commonTabsMap, CommonDocLinkTab.DOCUMENTATION, this::getDocumentationTab);
        addEntry(commonTabsMap, CommonDocLinkTab.PERMISSIONS, this::getPermissionsTab);
    }

    protected ButtonPanel createToolbar() {
        final ButtonPanel toolbar = new ButtonPanel();
        toolbar.addButton(saveButton);
        toolbar.addButton(saveAsButton);
        registerHandler(saveButton.addClickHandler(event -> save()));
        registerHandler(saveAsButton.addClickHandler(event -> saveAs()));
        return toolbar;
    }

    private void addEntry(final Map<CommonDocLinkTab, TabData> commonTabsMap,
                          final CommonDocLinkTab commonDocLinkTab,
                          final Supplier<TabData> tabDataSupplier) {
        final TabData tabData = tabDataSupplier.get();
        if (tabData != null) {
            commonTabsMap.put(commonDocLinkTab, tabData);
        }
    }

    @Override
    protected void onBind() {
        super.onBind();
        tabContentProvider.bind();
        registerHandler(tabContentProvider.addDirtyHandler(event -> {
            onChange();
        }));
    }

    @Override
    protected void onUnbind() {
        super.onUnbind();
        tabContentProvider.unbind();
    }


    @Override
    public void save() {
        if (saveButton.isEnabled()) {
            SaveDocumentEvent.fire(DocTabPresenter.this, DocTabPresenter.this);
        }
    }

    private void saveAs() {
        if (saveAsButton.isEnabled()) {
            SaveAsDocumentEvent.fire(DocTabPresenter.this, DocTabPresenter.this);
        }
    }

    public void addTab(final TabData tab, final TabProvider<D> provider) {
        tabContentProvider.add(tab, provider);
        getView().getTabBar().addTab(tab);
    }

    public void replaceTab(final TabData tab, final TabProvider<D> provider) {
        tabContentProvider.replace(tab, provider);
        getView().getTabBar().selectTab(tab);
        selectTab(tab);
    }

    public void setTabHidden(final TabData tab, final boolean hidden) {
        getView().getTabBar().setTabHidden(tab, hidden);
    }

    private void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(tabContentProvider.getPresenter(tab, this));
    }

    public void selectCommonTab(final CommonDocLinkTab commonDocLinkTab) {
        if (commonDocLinkTab != null) {
            final TabData tabData = commonTabsMap.get(commonDocLinkTab);
            if (tabData != null) {
                selectTab(tabData);
            }
        }
    }

    public void selectTab(final TabData tab) {
//        GWT.log("docRef: " + docRef + ", selecting tab " + NullSafe.get(tab, TabData::getLabel));
        final TaskMonitor taskMonitor = createTaskMonitor();
        final Task task = new SimpleTask("Selecting tab");
        taskMonitor.onStart(task);
        Scheduler.get().scheduleDeferred(() -> {
            if (tab != null) {
                getContent(tab, content -> {
                    if (content != null) {
                        currentContent = content;

                        // Set the content.
                        getView().getLayerContainer().show((Layer) currentContent);

                        // Update the buttons.
                        if (currentContent instanceof final HasToolbar hasToolbar) {
                            getView().clearToolbar();
                            getView().addToolbar(toolbar);

                            for (final Widget widget : hasToolbar.getToolbars()) {
                                getView().addToolbar(widget);
                            }
                        } else {
                            getView().clearToolbar();
                            getView().addToolbar(toolbar);
                        }

                        // Update the selected tab.
                        getView().getTabBar().selectTab(tab);
                        selectedTab = tab;

                        afterSelectTab(content);
                    }
                });
            }
            taskMonitor.onEnd(task);
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

    private void setReadOnlyStatus(final boolean readOnly) {
        if (readOnly) {
            final SafeHtmlBuilder html = new SafeHtmlBuilder();
            html.appendHtmlConstant("<span class=\"docTab-readOnlyNote\">");
            html.appendHtmlConstant("<span class=\"docTab-readOnlyIcon\" aria-hidden=\"true\">");
            html.append(SafeHtmlUtil.getSafeHtmlFromSafeConstant(SvgImage.LOCKED.getSvg()));
            html.appendHtmlConstant("</span>");
            html.appendEscaped("Read only");
            html.appendHtmlConstant("</span>");
            // The reason, which a tooltip doesn't give screen readers
            html.appendHtmlConstant("<span class=\"sr-only\">");
            html.appendEscaped(". " + READ_ONLY_REASON + ".");
            html.appendHtmlConstant("</span>");
            readOnlyStatus.setHTML(html.toSafeHtml());
            readOnlyStatus.setTitle(READ_ONLY_REASON);
        } else {
            readOnlyStatus.setHTML("");
            readOnlyStatus.setTitle(null);
        }
        // The status takes room from the tab bar, so it lays its tabs out again
        getView().getTabBar().refresh();
    }

    @Override
    protected void onRead(final DocRef docRef, final D document, final boolean readOnly) {
        this.docRef = docRef;
        saveButton.setEnabled(isDirty());
        saveAsButton.setEnabled(true);
        if (readOnly) {
            saveButton.setTitle("Save is not available as this document is read only");
        }
        setReadOnlyStatus(readOnly);
        tabContentProvider.read(docRef, document, readOnly);
        // The name may have changed (e.g. after a rename or Save As), and a clean document doesn't
        // become dirty to refresh the tab's label
        refreshTabIfLabelChanged();
    }

    @Override
    protected D onWrite(final D document) {
        return tabContentProvider.write(document);
    }

    @Override
    public void onClose() {
        super.onClose();
        tabContentProvider.onClose();
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
    public SvgImage getIcon() {
        return DocumentTypeRegistry.getIcon(getType());
    }

    @Override
    public Optional<String> getTooltip() {
        final String type = docRef.getType();
        if (type != null) {
            return Optional.of(type + " - " + docRef.getName());
        } else {
            return Optional.of(docRef.getName());
        }
    }

    @Override
    public void onDirty() {
        refreshTabIfLabelChanged();
        saveButton.setEnabled(isDirty());
    }

    private void refreshTabIfLabelChanged() {
        // Only fire tab refresh if the tab has changed.
        if (lastLabel == null || !lastLabel.equals(getLabel())) {
            lastLabel = getLabel();
            RefreshContentTabEvent.fire(this, this);
        }
    }

    @Override
    public DocRef getDocRef() {
        return docRef;
    }

    public void setDefaultTab(final TabData defaultTab) {
        this.defaultTab = defaultTab;
    }

    public Optional<TabData> getDefaultTab() {
        return Optional.ofNullable(defaultTab);
    }

    /**
     * @return The {@link TabData} instance for the Permissions tab.
     */
    protected abstract TabData getPermissionsTab();

    /**
     * @return The {@link TabData} instance for the Documentation tab.
     */
    protected abstract TabData getDocumentationTab();
}
