/*
 * Copyright 2026 Crown Copyright
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

package stroom.entity.client.component;

import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.entity.client.HasToolbar;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.tab.client.TabData;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/// Something shown in tabs, e.g. a document ([DocumentEditor]) or a folder: a tab bar, a row for
/// toolbars, and the tab showing. Each tab's content, a controller, is made and owned
/// when the tab is first shown, is read into whenever this is ([#read(DocRef, Object)]), and may
/// write what it shows back ([#write()]).
///
/// The toolbar row shows this controller's toolbar ([#getToolbar()]) if it has any buttons, with
/// those of the tab showing if its content is a [HasToolbar], and the content's other toolbars.
///
/// @param <D> The type of what is shown, e.g. a document's, which must compare by value (`equals`)
///            if it is written.
public abstract class TabbedController<D> extends Controller {

    private final LinkTabPanel view;
    private final Toolbar toolbar;
    // The buttons the tab showing added to the toolbar, removed when another tab is shown
    private final List<Widget> tabButtons = new ArrayList<>();
    // Each tab's content now
    private final Map<TabData, Tab<?>> tabs = new LinkedHashMap<>();
    // Every tab content there has been, read into and written from once made, including content a
    // tab shows no longer (replaceTab)
    private final List<Tab<?>> allTabs = new ArrayList<>();
    // The tabs a link can open directly (its Documentation, its Permissions)
    private final Map<CommonDocLinkTab, TabData> commonTabs = new EnumMap<>(CommonDocLinkTab.class);

    // Whether the tab bar says when what is shown is read only (showReadOnlyStatus)
    private boolean showsReadOnlyStatus;
    private DocRef docRef;
    private D document;
    private TabData selectedTab;
    // The tab shown once this is on the page, unless another is shown first
    private TabData defaultTab;

    /// @param eventBus The application's event bus.
    /// @param view     The tab bar, toolbar row and tab content.
    protected TabbedController(final EventBus eventBus, final LinkTabPanel view) {
        super(eventBus);
        this.view = view;
        toolbar = view.getToolbar();
        own(view.getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem())));
        initWidget(view.asWidget());
        // A tab's buttons may depend on whether what is shown may be changed (e.g. the
        // Documentation tab's Edit), so they are shown again when that changes, once the tab has
        // seen the change too
        watch(Contexts.READ_ONLY, readOnly -> Scheduler.get().scheduleDeferred(() -> {
            if (!isDisposed() && selectedTab != null) {
                showToolbars(tabs.get(selectedTab));
            }
        }));
    }

    /// Reads what to show into this and its tabs.
    ///
    /// @param docRef   The reference of what is shown, e.g. the document's, or null if it isn't a
    ///                 document (e.g. a user).
    /// @param document What is shown.
    public final void read(final DocRef docRef, final D document) {
        this.docRef = docRef;
        this.document = Objects.requireNonNull(document, "document");
        beforeRead(docRef, document);
        for (final Tab<?> tab : allTabs) {
            tab.read(docRef, document);
        }
        onRead(docRef, document);
        afterRead();
        if (showsReadOnlyStatus) {
            updateReadOnlyStatus(context(Contexts.READ_ONLY));
        }
    }

    /// @return What is shown, as the tabs show it (e.g. to save it), or null before it is read.
    public final D write() {
        return write(document);
    }

    /// Writes what the tabs show into a document, e.g. a new one when saving a copy.
    ///
    /// @param document The document to write into, or null.
    /// @return A copy of it with what the tabs show, or null if it is null.
    public final D write(final D document) {
        if (document == null) {
            return null;
        }
        D written = document;
        for (final Tab<?> tab : allTabs) {
            written = tab.write(written);
        }
        return afterWrite(written);
    }

    /// @return The label of the tab this is shown in, if not the name of what is shown, e.g. an
    /// annotation's number and title; null by default.
    public String getTabLabel() {
        return null;
    }

    /// @return What is shown, as it was read, or null before it is read.
    public final D getDocument() {
        return document;
    }

    /// @return The reference of what is shown, or null before it is read.
    public final DocRef getDocRef() {
        return docRef;
    }

    /// Focuses the selected tab, e.g. when what is shown is opened.
    ///
    /// @return Whether it was focused: not if the tabs aren't shown.
    public final boolean focusTabs() {
        return view.focusTabs();
    }

    /// Shows one of the tabs a link can open directly, if this has it.
    ///
    /// @param commonDocLinkTab The tab, or null for none.
    public final void selectCommonTab(final CommonDocLinkTab commonDocLinkTab) {
        if (commonDocLinkTab != null) {
            final TabData tab = commonTabs.get(commonDocLinkTab);
            if (tab != null) {
                selectTab(tab);
            }
        }
    }

    /// Adds a tab that only shows what is read, e.g. a document's permissions.
    ///
    /// @param tab    The tab.
    /// @param make   Makes its content, a controller, when it is first shown.
    /// @param reader Reads what is shown into it.
    /// @param <C>    Its content's type.
    protected final <C extends HasControllerNode & IsWidget> void addTab(final TabData tab,
                                                                      final Supplier<C> make,
                                                                      final TabReader<C, D> reader) {
        addTab(tab, make, reader, (content, document) -> document);
    }

    /// Adds a tab that shows and changes part of what is read.
    ///
    /// @param tab    The tab.
    /// @param make   Makes its content, a controller, when it is first shown.
    /// @param reader Reads what is shown into it.
    /// @param writer Writes what it shows into a copy of what is shown.
    /// @param <C>    Its content's type.
    protected final <C extends HasControllerNode & IsWidget> void addTab(final TabData tab,
                                                                      final Supplier<C> make,
                                                                      final TabReader<C, D> reader,
                                                                      final BiFunction<C, D, D> writer) {
        putTab(tab, new Tab<>(make, reader, writer, true));
        view.getTabBar().addTab(tab);
    }

    /// Adds a tab that only shows what is read, with content this has made and owns already, e.g.
    /// to give it inputs before its tab is first shown.
    ///
    /// @param tab     The tab.
    /// @param content Its content, owned by this.
    /// @param reader  Reads what is shown into it.
    /// @param <C>     Its content's type.
    protected final <C extends HasControllerNode & IsWidget> void addOwnedTab(final TabData tab,
                                                                           final C content,
                                                                           final TabReader<C, D> reader) {
        addOwnedTab(tab, content, reader, (owned, document) -> document);
    }

    /// Adds a tab that shows and changes part of what is read, with content this has made and owns
    /// already, e.g. to give it inputs before its tab is first shown.
    ///
    /// @param tab     The tab.
    /// @param content Its content, owned by this.
    /// @param reader  Reads what is shown into it.
    /// @param writer  Writes what it shows into a copy of what is shown.
    /// @param <C>     Its content's type.
    protected final <C extends HasControllerNode & IsWidget> void addOwnedTab(final TabData tab,
                                                                           final C content,
                                                                           final TabReader<C, D> reader,
                                                                           final BiFunction<C, D, D> writer) {
        Objects.requireNonNull(content, "content");
        putTab(tab, new Tab<>(() -> content, reader, writer, false));
        view.getTabBar().addTab(tab);
    }

    /// Shows other content in a tab, in place of what it shows (e.g. a pipeline's Stepping in place of
    /// its Structure), and shows the tab. Content a tab has shown before stays read into and
    /// written from, as it may still hold changes.
    ///
    /// @param tab     The tab, added with `addTab` or `addOwnedTab`.
    /// @param content The content to show, owned by this; the same object to show it again.
    /// @param reader  Reads what is shown into it.
    /// @param writer  Writes what it shows into a copy of what is shown.
    /// @param <C>     Its content's type.
    protected final <C extends HasControllerNode & IsWidget> void replaceTab(final TabData tab,
                                                                          final C content,
                                                                          final TabReader<C, D> reader,
                                                                          final BiFunction<C, D, D> writer) {
        if (!tabs.containsKey(tab)) {
            throw new IllegalArgumentException("No tab " + tab.getLabel());
        }
        Objects.requireNonNull(content, "content");
        Tab<?> replacement = null;
        for (final Tab<?> existing : allTabs) {
            if (existing.isOf(content)) {
                replacement = existing;
            }
        }
        if (replacement == null) {
            replacement = new Tab<>(() -> content, reader, writer, false);
            allTabs.add(replacement);
        }
        tabs.put(tab, replacement);
        selectTab(tab);
    }

    private void putTab(final TabData tab, final Tab<?> content) {
        if (tabs.containsKey(tab)) {
            throw new IllegalArgumentException("Tab " + tab.getLabel() + " added twice");
        }
        tabs.put(tab, content);
        allTabs.add(content);
    }

    /// Shows whether what is shown is read only ([Contexts#READ_ONLY]), and why, at the far right of
    /// the tab bar: a polite status, so screen readers announce it when something read only opens.
    protected final void showReadOnlyStatus() {
        if (!showsReadOnlyStatus) {
            showsReadOnlyStatus = true;
            watch(Contexts.READ_ONLY, this::updateReadOnlyStatus);
        }
    }

    /// Hides or shows a tab in the tab bar.
    ///
    /// @param tab    The tab.
    /// @param hidden Whether to hide it.
    protected final void setTabHidden(final TabData tab, final boolean hidden) {
        view.getTabBar().setTabHidden(tab, hidden);
    }

    /// Says which tab a link to the Documentation or Permissions opens.
    ///
    /// @param commonDocLinkTab What the link is to.
    /// @param tab              The tab, added with `addTab`.
    protected final void setCommonTab(final CommonDocLinkTab commonDocLinkTab, final TabData tab) {
        if (!tabs.containsKey(tab)) {
            throw new IllegalArgumentException("No tab " + tab.getLabel());
        }
        commonTabs.put(commonDocLinkTab, tab);
    }

    /// Shows a tab, making its content if it is the first time.
    ///
    /// @param tab The tab.
    protected final void selectTab(final TabData tab) {
        final Tab<?> selected = Objects.requireNonNull(tabs.get(tab), "tab");
        view.getLayerContainer().show(selected.getLayer());
        showToolbars(selected);
        view.getTabBar().selectTab(tab);
        selectedTab = tab;
        afterSelectTab(tab);
    }

    // The tab's own buttons join the toolbar, and its other toolbars follow it
    private void showToolbars(final Tab<?> selected) {
        for (final Widget widget : tabButtons) {
            toolbar.remove(widget);
        }
        tabButtons.clear();
        final HasToolbar hasToolbar = selected.getToolbar();
        if (hasToolbar != null) {
            for (final Widget widget : hasToolbar.getDocumentButtons()) {
                toolbar.add(widget);
                tabButtons.add(widget);
            }
        }
        view.setToolbarVisible(toolbar.getWidgetCount() > 0);
        view.setTabToolbars(hasToolbar != null
                ? hasToolbar.getToolbars()
                : Collections.emptyList());
    }

    /// Says which tab to show once this is on the page, unless another is shown first (e.g. a
    /// link to the documentation, or a pipeline's stepping): its content is made, and read, only
    /// then.
    ///
    /// @param tab The tab, added with `addTab` or `addOwnedTab`.
    protected final void setDefaultTab(final TabData tab) {
        if (!tabs.containsKey(tab)) {
            throw new IllegalArgumentException("No tab " + tab.getLabel());
        }
        defaultTab = tab;
        if (selectedTab == null && asWidget().isAttached()) {
            selectTab(tab);
        }
    }

    /// @param tab A tab.
    /// @return Whether this has the tab (some are added only for users with a permission).
    protected final boolean hasTab(final TabData tab) {
        return tabs.containsKey(tab);
    }

    @Override
    protected void onAttach() {
        if (selectedTab == null && defaultTab != null) {
            selectTab(defaultTab);
        }
    }

    /// @return The tab showing, or null before one is shown.
    protected final TabData getSelectedTab() {
        return selectedTab;
    }

    /// @return The toolbar, at the start of the toolbar row, to add buttons to; the tab showing
    /// adds its own after them.
    protected final Toolbar getToolbar() {
        return toolbar;
    }

    /// @return The tab bar, toolbar row and tab content.
    protected final LinkTabPanel getTabView() {
        return view;
    }

    /// Called before what is shown is read into the tabs, e.g. to make what they share from it.
    ///
    /// @param docRef   The reference of what is shown.
    /// @param document What is shown.
    protected void beforeRead(final DocRef docRef, final D document) {
        // Nothing by default
    }

    /// Called once the tabs have written what they show, e.g. to add what they share.
    ///
    /// @param written What the tabs wrote.
    /// @return What is shown, as written; by default what the tabs wrote.
    protected D afterWrite(final D written) {
        return written;
    }

    /// Called once what is shown is read into the tabs, e.g. to enable buttons.
    ///
    /// @param docRef   The reference of what is shown.
    /// @param document What is shown.
    protected void onRead(final DocRef docRef, final D document) {
        // Nothing by default
    }

    /// Handles a key action while this is the tab showing, e.g. to run a query with Ctrl+Enter.
    ///
    /// @param action The action.
    /// @return Whether it was handled, so goes no further; false by default.
    public boolean handleKeyAction(final Action action) {
        return false;
    }

    /// Called when the app's tab this is shown in is closed for good, before it is disposed, e.g. to
    /// stop what it started.
    public void onClose() {
        // Nothing by default
    }

    /// Called when the app's tab this is shown in becomes the one showing, or stops being it, e.g. to
    /// stop what needn't run while hidden.
    ///
    /// @param visible Whether it is showing.
    public void onContentTabVisible(final boolean visible) {
        // Nothing by default
    }

    /// Called once a tab is shown, e.g. to bring what it shows up to date.
    ///
    /// @param tab The tab.
    protected void afterSelectTab(final TabData tab) {
        // Nothing by default
    }

    /// Called after [#onRead(DocRef, Object)], for [DocumentEditor] to enable its buttons.
    void afterRead() {
        // Nothing by default
    }

    // Said once something is read, as until then there is nothing to change (e.g. traces shown
    // without a document)
    private void updateReadOnlyStatus(final boolean readOnly) {
        view.setReadOnlyStatus(readOnly && document != null);
    }

    /// Reads what is shown into a tab's content.
    ///
    /// @param <C> The content's type.
    /// @param <D> The type of what is shown.
    @FunctionalInterface
    public interface TabReader<C, D> {

        /// @param content  The tab's content.
        /// @param docRef   The reference of what is shown.
        /// @param document What is shown.
        void read(C content, DocRef docRef, D document);
    }

    // A tab: how to make its content, read into it and write from it, and the content once made
    private final class Tab<C extends HasControllerNode & IsWidget> {

        private final Supplier<C> make;
        private final TabReader<C, D> reader;
        private final BiFunction<C, D, D> writer;
        // Whether the tab owns its content once it is made, rather than this owning it already
        private final boolean owns;
        private C content;
        private ControllerLayer layer;

        private Tab(final Supplier<C> make,
                    final TabReader<C, D> reader,
                    final BiFunction<C, D, D> writer,
                    final boolean owns) {
            this.make = make;
            this.reader = reader;
            this.writer = writer;
            this.owns = owns;
        }

        private ControllerLayer getLayer() {
            if (content == null) {
                content = owns
                        ? own(make.get())
                        : make.get();
                layer = new ControllerLayer(content);
                if (document != null) {
                    reader.read(content, docRef, document);
                }
            }
            return layer;
        }

        private void read(final DocRef docRef, final D document) {
            if (content != null) {
                reader.read(content, docRef, document);
            }
        }

        // Whether its content is this, made already or always this (an owned tab's)
        private boolean isOf(final Object candidate) {
            return content == candidate || (!owns && make.get() == candidate);
        }

        private D write(final D document) {
            return content == null
                    ? document
                    : writer.apply(content, document);
        }

        // What adds to the toolbar row: the content, if it has toolbars of its own
        private HasToolbar getToolbar() {
            return content instanceof final HasToolbar hasToolbar
                    ? hasToolbar
                    : null;
        }
    }
}
