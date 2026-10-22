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
import stroom.svg.shared.SvgImage;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.presenter.client.Layer;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class DocTabPresenter<V extends LinkTabPanelView, D>
        extends DocPresenter<V, D>
        implements DocumentTabData, Refreshable, HasType, HasSave {

    // Made in the toolbar by createToolbar, so null if a subclass's toolbar has none
    private ToolbarButton saveButton;
    private ToolbarButton saveAsButton;
    private boolean saveEnabled;
    private boolean saveAsEnabled;
    private TabData selectedTab;
    private String lastLabel;
    // The document's toolbar: Save, Save As and any buttons subclasses add, then the buttons of the
    // tab showing (HasToolbar.getDocumentButtons), all one group (one tab stop)
    protected final Toolbar toolbar;
    // The buttons the tab showing added to the toolbar, removed when another tab is shown
    private final List<Widget> tabButtons = new ArrayList<>();
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
        toolbar = createToolbar();
        readOnlyStatus.setStyleName("docTab-readOnlyStatus");
        readOnlyStatus.getElement().setAttribute("role", "status");
        getView().setTabBarStatus(readOnlyStatus);

        registerHandler(getView().getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem())));

        tabContentProvider = new TabContentProvider<>(eventBus, this);

        commonTabsMap = new EnumMap<>(CommonDocLinkTab.class);
        addEntry(commonTabsMap, CommonDocLinkTab.DOCUMENTATION, this::getDocumentationTab);
        addEntry(commonTabsMap, CommonDocLinkTab.PERMISSIONS, this::getPermissionsTab);
    }

    /// Makes the document's toolbar, with its Save and Save As buttons. A subclass may add its own
    /// buttons to [#toolbar] once it is made.
    ///
    /// @return The toolbar.
    protected Toolbar createToolbar() {
        final Toolbar toolbar = new Toolbar("Document actions");
        saveButton = toolbar.button(SvgImage.SAVE, "Save", this::save);
        saveButton.setEnabled(false);
        saveAsButton = toolbar.button(SvgImage.SAVEAS, "Save As", this::saveAs);
        saveAsButton.setEnabled(false);
        return toolbar;
    }

    private void setSaveEnabled(final boolean enabled) {
        saveEnabled = enabled;
        if (saveButton != null) {
            saveButton.setEnabled(enabled);
        }
    }

    private void setSaveAsEnabled(final boolean enabled) {
        saveAsEnabled = enabled;
        if (saveAsButton != null) {
            saveAsButton.setEnabled(enabled);
        }
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
        if (saveEnabled) {
            SaveDocumentEvent.fire(DocTabPresenter.this, DocTabPresenter.this);
        }
    }

    private void saveAs() {
        if (saveAsEnabled) {
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
        callback.onReady(tabContentProvider.getPresenter(tab));
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

                        // Update the buttons: the tab's own buttons join the document's toolbar,
                        // and its other toolbars follow it
                        for (final Widget widget : tabButtons) {
                            toolbar.remove(widget);
                        }
                        tabButtons.clear();
                        getView().clearToolbar();
                        getView().addToolbar(toolbar);
                        if (currentContent instanceof final HasToolbar hasToolbar) {
                            for (final Widget widget : hasToolbar.getDocumentButtons()) {
                                toolbar.add(widget);
                                tabButtons.add(widget);
                            }
                            for (final Widget widget : hasToolbar.getToolbars()) {
                                getView().addToolbar(widget);
                            }
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
        setSaveEnabled(isDirty());
        setSaveAsEnabled(true);
        // Set on every read, so a document read again as editable gets its plain name back
        if (saveButton != null) {
            saveButton.setTitle(readOnly
                    ? "Save (read only)"
                    : "Save");
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
        setSaveEnabled(isDirty());
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
