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

package stroom.main.client.component;

import stroom.ai.shared.AskStroomAiConfig.DockLocation;
import stroom.explorer.client.component.Navigation;
import stroom.main.client.DynamicStyles;
import stroom.widget.component.client.AriaPanel;
import stroom.widget.component.client.Component;
import stroom.widget.layout.client.SplitPane;
import stroom.widget.tab.client.component.CurveTabLayout;
import stroom.widget.util.client.ContentFocus;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.Size;
import stroom.widget.util.client.TabOrder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.ResizeFlowPanel;
import com.google.gwt.user.client.ui.ResizeLayoutPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// The main screen: the main toolbar, the explorer beside the document tabs, the maintenance banner
/// above them and, when docked, the Ask Stroom AI panel on an edge.
///
/// The explorer and the document tabs are always there, in a split pane its template declares;
/// hiding the explorer hides its pane, which keeps its width for when it is shown again. Only the
/// docked panel and a view maximised in place of the main area are chosen at runtime.
public class Main extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    // The main area, below the maintenance banner while it is shown (main-screen.css)
    private static final String BANNER_SHOWN = "main-screen__main--banner";
    // The explorer hidden, which a document's body may pad for (dashboard-body.css)
    private static final String EXPLORER_HIDDEN = "main-screen--explorer-hidden";
    // The theme's page border, a rule of the theme's declarations (setBorderStyle)
    private static final String THEMED = "main-screen--themed";
    // The content filling its wrapper beside the docked panel
    private static final String DOCKED_CONTENT = "main-screen__docked-content";

    private Consumer<Size> onDockResize = size -> {
    };

    @UiField
    AriaPanel banner;
    @UiField
    FlowPanel main;
    @UiField
    ResizeLayoutPanel contentPanel;
    @UiField
    MainToolbar mainToolbar;
    @UiField
    SplitPane explorerSplit;
    @UiField
    Navigation explorer;
    @UiField
    CurveTabLayout content;

    // Where F6 puts the focus in the explorer and the content: the explorer's tree, and the open
    // document's layers, below the content's tab bar
    private static final String EXPLORER_START_SELECTOR = "[role=\"tree\"]";
    private static final String DOCUMENT_SELECTOR = ".curve-tab-layout__layer-container-outer";

    // Layout state — the single source of truth for how the layout should look.
    private final LayoutState state = new LayoutState();

    // What is shown in the main area: the explorer's split pane, or a maximised view
    private Widget shownContent;
    private SplitPane outerSplitPanel;

    private String currentBanner;

    /// Makes the screen, with the explorer beside no document tabs.
    public Main() {
        initWidget(BINDER.createAndBindUi(this));
        sinkEvents(Event.KEYEVENTS);
        shownContent = explorerSplit;
    }

    /// @return The explorer, which its controller attaches to.
    public Navigation getExplorer() {
        return explorer;
    }

    /// @return The document tabs, which their controller attaches to.
    public CurveTabLayout getContent() {
        return content;
    }

    /// Hides the explorer, or shows a view in place of the whole main area, or puts them back.
    ///
    /// @param view What to show in place of the main area, or null to hide or show the explorer. It
    ///             is compared by identity, so pass the same object each time.
    public void maximise(final IsWidget view) {
        if (view == null) {
            // Toggle explorer visibility.
            state.explorerHidden = !state.explorerHidden;
            explorerSplit.setPaneHidden(explorer, state.explorerHidden);
            setStyleName(EXPLORER_HIDDEN, state.explorerHidden);
            if (state.maximisedView != null) {
                state.maximisedView = null;
                layoutContent();
            }
        } else {
            // Toggle specific content maximise.
            if (state.maximisedView == null || state.maximisedView != view) {
                state.maximisedView = view;
            } else {
                state.maximisedView = null;
            }
            layoutContent();
        }

        // Focus the appropriate widget after the change.
        if (state.explorerHidden || state.maximisedView != null) {
            final Widget focusTarget = state.maximisedView != null
                    ? state.maximisedView.asWidget()
                    : content;
            if (focusTarget instanceof Focus) {
                ((Focus) focusTarget).focus();
            }
        } else {
            if (explorer instanceof Focus) {
                ((Focus) explorer).focus();
            }
        }
    }

    /// Docks a panel on an edge of the main area, beside the content and the main toolbar.
    ///
    /// @param widget       The panel.
    /// @param dockLocation The edge.
    /// @param size         Its size.
    public void dock(final Widget widget,
                     final DockLocation dockLocation,
                     final Size size) {
        captureDockSize();
        if (state.dockedWidget != null) {
            state.dockedWidget.removeFromParent();
        }
        state.docked = true;
        state.dockedWidget = widget;
        state.dockLocation = dockLocation;
        state.dockSize = getDockDimension(dockLocation, size);
        layoutContent();
    }

    /// Undocks the docked panel, if one is docked.
    public void undock() {
        if (!state.docked) {
            return;
        }
        captureDockSize();
        state.docked = false;
        // Note: we null the widget in state but the widget itself is just
        // detached — AskStroomAiController still holds a reference to it.
        if (state.dockedWidget != null) {
            state.dockedWidget.removeFromParent();
        }
        state.dockedWidget = null;
        state.dockLocation = null;
        layoutContent();
    }

    // ── Layout ──────────────────────────────────────────────────────────────

    // Puts what is shown (the explorer's split pane, or the maximised view) in the main area, beside
    // the docked panel if one is docked. The widgets are detached from where they were and attached
    // again, which keeps their DOM state, handlers, scroll positions and controllers.
    private void layoutContent() {
        // ── Step 1: Detach the shown widgets from their current parents ──
        shownContent.removeStyleName(DOCKED_CONTENT);
        shownContent.removeFromParent();
        if (state.dockedWidget != null) {
            state.dockedWidget.removeFromParent();
        }
        mainToolbar.removeFromParent();

        // ── Step 2: Discard the docked panel's containers ──
        contentPanel.clear();
        outerSplitPanel = null;

        // ── Step 3: What to show: a specific view maximised (a full screen takeover), else the
        // explorer beside the content ──
        shownContent = state.maximisedView != null
                ? state.maximisedView.asWidget()
                : explorerSplit;

        // ── Step 4: Wrap with dock panel if AI is docked ──
        if (state.docked && state.dockedWidget != null) {
            // The docked panel on its edge of the content (and the main toolbar), at the size it
            // was given; a size the user drags it to is reported
            final ResizeFlowPanel contentWrapper = new ResizeFlowPanel();
            contentWrapper.addStyleName("main-screen__content-wrapper");
            shownContent.addStyleName(DOCKED_CONTENT);
            // The toolbar first, so that the Tab key reaches it before the content it is shown over
            contentWrapper.add(mainToolbar);
            contentWrapper.add(shownContent);

            final String dockSize = Math.round(state.dockSize) + "px";
            final boolean dockedFirst = state.dockLocation == DockLocation.LEFT
                                        || state.dockLocation == DockLocation.TOP;
            outerSplitPanel = new SplitPane();
            outerSplitPanel.setThin(true);
            outerSplitPanel.setVertical(state.dockLocation == DockLocation.TOP
                                        || state.dockLocation == DockLocation.BOTTOM);
            outerSplitPanel.addStyleName("main-screen__outer-split-panel");
            outerSplitPanel.setOnResized(this::scheduleDockResizeCheck);
            if (dockedFirst) {
                outerSplitPanel.setSizes(dockSize + ", *");
                outerSplitPanel.setNames("Ask Stroom AI, Content");
                outerSplitPanel.add(state.dockedWidget);
                outerSplitPanel.add(contentWrapper);
            } else {
                outerSplitPanel.setSizes("*, " + dockSize);
                outerSplitPanel.setNames("Content, Ask Stroom AI");
                outerSplitPanel.add(contentWrapper);
                outerSplitPanel.add(state.dockedWidget);
            }
            contentPanel.setWidget(outerSplitPanel);
        } else {
            // No dock — toolbar goes back to the main FlowPanel, before the content.
            main.insert(mainToolbar, 0);
            contentPanel.setWidget(shownContent);
        }
    }

    // ── Size capture helpers ────────────────────────────────────────────────

    // Capture current dock size from the live DOM before undocking/re-docking.
    private void captureDockSize() {
        if (state.docked && state.dockedWidget != null && state.dockLocation != null) {
            final double currentSize;
            if (state.dockLocation == DockLocation.LEFT || state.dockLocation == DockLocation.RIGHT) {
                currentSize = state.dockedWidget.getOffsetWidth();
            } else {
                currentSize = state.dockedWidget.getOffsetHeight();
            }
            if (currentSize > 0) {
                state.dockSize = currentSize;
            }
        }
    }

    private double getDockDimension(final DockLocation location, final Size size) {
        if (location == DockLocation.LEFT || location == DockLocation.RIGHT) {
            return size.getWidth();
        } else {
            return size.getHeight();
        }
    }

    // Checks if the dock panel size has changed (the user moved its splitter)
    // and fires a DockResizeEvent when it does.
    private void scheduleDockResizeCheck() {
        if (!state.docked || state.dockedWidget == null || outerSplitPanel == null) {
            return;
        }
        // Use a deferred command to observe the actual rendered size after layout.
        Scheduler.get().scheduleDeferred(() -> {
            if (state.docked && state.dockedWidget != null && outerSplitPanel != null) {
                final double currentSize;
                if (state.dockLocation == DockLocation.LEFT
                    || state.dockLocation == DockLocation.RIGHT) {
                    currentSize = state.dockedWidget.getOffsetWidth();
                } else {
                    currentSize = state.dockedWidget.getOffsetHeight();
                }
                if (currentSize > 0 && Double.compare(currentSize, state.dockSize) != 0) {
                    state.dockSize = currentSize;
                    final Size newSize = new Size.Builder()
                            .width(state.dockedWidget.getOffsetWidth())
                            .height(state.dockedWidget.getOffsetHeight())
                            .build();
                    onDockResize.accept(newSize);
                }
            }
        });
    }

    /// @param onDockResize Given the docked panel's new size when the user drags its splitter.
    public void setOnDockResize(final Consumer<Size> onDockResize) {
        this.onDockResize = Objects.requireNonNull(onDockResize, "onDockResize");
    }

    /// @return The main toolbar.
    public MainToolbar getMainToolBar() {
        return mainToolbar;
    }

    /// @param style The theme's page border, as CSS declarations, or null or empty for none.
    public void setBorderStyle(final String style) {
        if (style != null && !style.isEmpty()) {
            // Any declarations the theme gives (e.g. a border and its widths), so a rule of them
            // rather than a custom property one declaration could use
            DynamicStyles.put(SafeHtmlUtils.fromTrustedString("." + THEMED),
                    SafeStylesUtils.fromTrustedString(style));
            addStyleName(THEMED);
        }
    }

    /// @param colour The theme's colour of the selected document tab, or null or blank for the default.
    public void setSelectedTabColour(final String colour) {
        // The theme's variable, for the curved tabs, which are all within this screen (tab-bar.css)
        if (colour != null && !colour.isBlank()) {
            CssProperties.set(main.getElement(), "--curve-tab__selected-color", colour);
        }
    }

    /// Moves the focus to the next (or previous) main area that is shown: the main toolbar,
    /// the explorer and the open content, going round.
    ///
    /// @param backwards Whether to go to the previous area.
    public void focusArea(final boolean backwards) {
        final List<Widget> areas = new ArrayList<>();
        for (final Widget area : new Widget[]{mainToolbar, explorer, content}) {
            // Only those shown, e.g. not the explorer while the sidebar is hidden
            if (area != null && area.isAttached() && area.getElement().getOffsetWidth() > 0) {
                areas.add(area);
            }
        }
        if (areas.isEmpty()) {
            return;
        }
        final Element active = getActiveElement();
        int current = -1;
        for (int i = 0; i < areas.size(); i++) {
            if (active != null && areas.get(i).getElement().isOrHasChild(active)) {
                current = i;
            }
        }
        final int next = current < 0
                ? (backwards
                ? areas.size() - 1
                : 0)
                : (current + (backwards
                ? areas.size() - 1
                : 1)) % areas.size();
        final Widget area = areas.get(next);
        focusAreaWidget(area, area == explorer
                ? EXPLORER_START_SELECTOR
                : area == content
                        ? DOCUMENT_SELECTOR
                        : null);
    }

    // Focuses an area at the first tab stop in the part of it where its work is (the explorer's tree,
    // the content's open document, past the buttons above them), if it has one; otherwise as the
    // area focuses itself, or else at its first tab stop
    private static void focusAreaWidget(final Widget area, final String startSelector) {
        final Element part = startSelector == null
                ? null
                : querySelector(area.getElement(), startSelector);
        final Element partStart = part == null
                ? null
                : ContentFocus.firstTabStop(part);
        if (partStart != null) {
            partStart.focus();
        } else if (area instanceof final Focus focus) {
            focus.focus();
        } else {
            final JsArray<Element> stops = TabOrder.tabStops(area.getElement());
            if (stops.length() > 0) {
                stops.get(0).focus();
            }
        }
    }

    // The first element inside another matching a selector, or null
    private static native Element querySelector(Element element, String selector) /*-{
        return element.querySelector(selector);
    }-*/;

    // The element with the focus, or null
    private static native Element getActiveElement() /*-{
        return $doc.activeElement;
    }-*/;

    /// Shows the maintenance message above the main area, or hides it.
    ///
    /// @param text The message, or null or blank for none.
    public void setBanner(final String text) {
        if (!Objects.equals(currentBanner, text)) {
            currentBanner = text;
            if (text == null || text.trim().isEmpty()) {
                main.removeStyleName(BANNER_SHOWN);
                banner.setVisible(false);
                banner.getElement().setInnerText("");
            } else {
                main.addStyleName(BANNER_SHOWN);
                banner.setVisible(true);
                banner.getElement().setInnerText(text);
            }
        }
    }

    interface Binder extends UiBinder<Widget, Main> {

    }

    // Captures all layout state as a single source of truth.
    // All state-changing methods update this object, then call
    // layoutContent() to put the widgets where they go.
    private static class LayoutState {

        boolean explorerHidden;

        boolean docked;
        DockLocation dockLocation;
        double dockSize;
        Widget dockedWidget;

        IsWidget maximisedView;
    }

}
