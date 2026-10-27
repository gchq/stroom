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
import stroom.entity.client.presenter.HasToolbar;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.component.client.PresenterAdapter;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/// Something shown in tabs, e.g. a document ([DocumentEditor]) or a folder: a tab bar, a row for
/// toolbars, and the tab showing. Each tab's content, a controller or a presenter, is made and owned
/// when the tab is first shown, is read into whenever this is ([#read(DocRef, Object)]), and may
/// write what it shows back ([#write()]).
///
/// The toolbar row shows this controller's toolbar ([#getToolbar()]) if it has any buttons, with
/// those of the tab showing if its content is a [HasToolbar], and the content's other toolbars.
///
/// @param <D> The type of what is shown, e.g. a document's, which must compare by value (`equals`)
///            if it is written.
public abstract class TabbedController<D> extends Controller {

    // Why something shown is read only: it is only ever read only for a user who can't change it
    private static final String READ_ONLY_REASON = "You don't have permission to change this";

    private final LinkTabPanelView view;
    private final Toolbar toolbar = new Toolbar("Document actions");
    // The buttons the tab showing added to the toolbar, removed when another tab is shown
    private final List<Widget> tabButtons = new ArrayList<>();
    // Each tab's content now
    private final Map<TabData, Tab<?>> tabs = new LinkedHashMap<>();
    // Every tab content there has been, read into and written from once made, including content a
    // tab shows no longer (replaceTab)
    private final List<Tab<?>> allTabs = new ArrayList<>();
    // The tabs a link can open directly (its Documentation, its Permissions)
    private final Map<CommonDocLinkTab, TabData> commonTabs = new EnumMap<>(CommonDocLinkTab.class);

    // Says that what is shown is read only, at the far right of the tab bar, for those that show it
    // (showReadOnlyStatus)
    private HTML readOnlyStatus;
    private DocRef docRef;
    private D document;
    private TabData selectedTab;
    // A tab asked for before this is on the page, shown once it is (as its content may measure itself)
    private TabData pendingTab;

    /// @param eventBus The application's event bus.
    /// @param view     The tab bar, toolbar row and tab content.
    protected TabbedController(final EventBus eventBus, final LinkTabPanelView view) {
        super(eventBus);
        this.view = view;
        view.getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem()));
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
    /// @param docRef   The reference of what is shown, e.g. the document's.
    /// @param document What is shown.
    public final void read(final DocRef docRef, final D document) {
        this.docRef = Objects.requireNonNull(docRef, "docRef");
        this.document = Objects.requireNonNull(document, "document");
        beforeRead(docRef, document);
        for (final Tab<?> tab : allTabs) {
            tab.read(docRef, document);
        }
        onRead(docRef, document);
        afterRead();
        if (readOnlyStatus != null) {
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
    /// @param make   Makes its content, a controller or a presenter, when it is first shown.
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
    /// @param make   Makes its content, a controller or a presenter, when it is first shown.
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
        if (readOnlyStatus == null) {
            readOnlyStatus = new HTML();
            readOnlyStatus.setStyleName("docTab-readOnlyStatus");
            readOnlyStatus.getElement().setAttribute("role", "status");
            view.setTabBarStatus(readOnlyStatus);
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
        if (!asWidget().isAttached()) {
            // Its content is made, and read, once this is on the page, as it was before
            pendingTab = tab;
            return;
        }
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
        view.clearToolbar();
        final HasToolbar hasToolbar = selected.getToolbar();
        if (hasToolbar != null) {
            for (final Widget widget : hasToolbar.getDocumentButtons()) {
                toolbar.add(widget);
                tabButtons.add(widget);
            }
        }
        if (toolbar.getWidgetCount() > 0) {
            view.addToolbar(toolbar);
        }
        if (hasToolbar != null) {
            for (final Widget widget : hasToolbar.getToolbars()) {
                view.addToolbar(widget);
            }
        }
    }

    @Override
    protected void onAttach() {
        if (pendingTab != null) {
            final TabData tab = pendingTab;
            pendingTab = null;
            selectTab(tab);
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
    protected final LinkTabPanelView getTabView() {
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

    // For DocumentEditor, after onRead
    void afterRead() {
        // Nothing by default
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

    // Shown once something is read, as until then there is nothing to change (e.g. traces shown
    // without a document)
    private void updateReadOnlyStatus(final boolean readOnly) {
        if (readOnly && document != null) {
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
        view.getTabBar().refresh();
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

        // What adds to the toolbar row: the content, or the presenter an adapter shows
        private HasToolbar getToolbar() {
            if (content instanceof final HasToolbar hasToolbar) {
                return hasToolbar;
            } else if (content instanceof final PresenterAdapter<?> adapter
                       && adapter.getPresenter() instanceof final HasToolbar hasToolbar) {
                return hasToolbar;
            }
            return null;
        }
    }
}
