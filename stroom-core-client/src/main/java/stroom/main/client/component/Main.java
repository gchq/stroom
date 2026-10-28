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
import stroom.main.client.DynamicStyles;
import stroom.widget.component.client.Component;
import stroom.widget.layout.client.SplitPane;
import stroom.widget.util.client.ContentFocus;
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
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// The main screen: the main toolbar, the explorer beside the document tabs, the maintenance banner
/// above them and, when docked, the Ask Stroom AI panel on an edge.
public class Main extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    // The main area, below the maintenance banner while it is shown (components.css)
    private static final String BANNER_SHOWN = "mainViewImpl--banner";

    private Consumer<Size> onDockResize = size -> {
    };

    @UiField
    SimplePanel root;
    @UiField
    SimplePanel banner;
    @UiField
    FlowPanel main;
    @UiField
    ResizeLayoutPanel contentPanel;
    @UiField
    MainToolbar mainToolbar;

    // Where F6 puts the focus in the explorer and the content: the explorer's tree, and the open
    // document's layers, below the content's tab bar
    private static final String EXPLORER_START_SELECTOR = "[role=\"tree\"]";
    private static final String DOCUMENT_SELECTOR = ".curveTabLayoutViewImpl-layerContainerOuter";

    // Persistent content widgets — set by setExplorerView and setContentView, never destroyed.
    private Widget westWidget;
    private Widget centerWidget;

    // Layout state — the single source of truth for how the layout should look.
    private final LayoutState state = new LayoutState();

    private SplitPane outerSplitPanel;

    private String currentBanner;

    /// Makes the screen, with no content until it is given it.
    public Main() {
        initWidget(BINDER.createAndBindUi(this));
        banner.setVisible(false);
        // An alert, so that a screen reader says the maintenance message when it is shown. The role
        // is set before any text is, as a live region must exist before what it announces
        banner.getElement().setAttribute("role", "alert");
        sinkEvents(Event.KEYEVENTS);
    }

    /// Shows the explorer.
    ///
    /// @param view Its view.
    public void setExplorerView(final IsWidget view) {
        westWidget = view.asWidget();
        // A landmark screen readers can go to
        setLandmark(westWidget, "navigation", "Explorer");
        rebuildLayout();
    }

    /// Shows the document tabs.
    ///
    /// @param view Its view.
    public void setContentView(final IsWidget view) {
        centerWidget = view.asWidget();
        setLandmark(centerWidget, "main", null);
        rebuildLayout();
    }

    /// Hides the explorer, or shows a view in place of the whole main area, or puts them back.
    ///
    /// @param content What to show in place of the main area, or null to hide or show the explorer. It
    ///                is compared by identity, so pass the same object each time.
    public void maximise(final IsWidget content) {
        if (content == null) {
            // Toggle explorer visibility.
            if (!state.explorerMaximised) {
                captureSplitPos();
                state.explorerMaximised = true;
            } else {
                state.explorerMaximised = false;
            }
            state.maximisedView = null;
        } else {
            // Toggle specific content maximise.
            if (state.maximisedView == null || state.maximisedView != content) {
                captureSplitPos();
                state.maximisedView = content;
            } else {
                state.maximisedView = null;
            }
        }

        rebuildLayout();

        // Focus the appropriate widget after rebuild.
        if (state.explorerMaximised || state.maximisedView != null) {
            final Widget focusTarget = state.maximisedView != null
                    ? state.maximisedView.asWidget()
                    : centerWidget;
            if (focusTarget instanceof Focus) {
                ((Focus) focusTarget).focus();
            }
        } else {
            if (westWidget instanceof Focus) {
                ((Focus) westWidget).focus();
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
        state.docked = true;
        state.dockedWidget = widget;
        state.dockLocation = dockLocation;
        state.dockSize = getDockDimension(dockLocation, size);
        rebuildLayout();
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
        state.dockedWidget = null;
        state.dockLocation = null;
        rebuildLayout();
    }

    // ── Layout rebuild ──────────────────────────────────────────────────────

    // Builds the correct widget tree from the current LayoutState.
    // Persistent content widgets (westWidget, centerWidget, state.dockedWidget,
    // mainToolbar) are detached from their current parents and re-attached
    // into fresh disposable containers. This preserves all DOM state, event
    // handlers, scroll positions, and controller bindings.
    private void rebuildLayout() {
        // ── Step 1: Detach all persistent widgets from current parents ──
        if (westWidget != null) {
            westWidget.removeFromParent();
        }
        if (centerWidget != null) {
            centerWidget.removeFromParent();
        }
        if (state.dockedWidget != null) {
            state.dockedWidget.removeFromParent();
        }
        mainToolbar.removeFromParent();

        // ── Step 2: Discard old disposable containers ──
        contentPanel.clear();
        // Disposable container widgets — recreated by rebuildLayout().
        outerSplitPanel = null;

        // ── Step 3: Determine the "inner content" ──
        final Widget innerContent;

        if (state.maximisedView != null) {
            // A specific view is maximised (full-screen takeover).
            innerContent = state.maximisedView.asWidget();

        } else if (state.explorerMaximised) {
            // Explorer hidden — content fills the space.
            centerWidget.getElement().addClassName("maximised");
            innerContent = centerWidget;

        } else {
            // Normal — explorer + content in a split panel.
            centerWidget.getElement().removeClassName("maximised");

            // Ensure the split position isn't too small.
            final int splitWidth = Math.max(state.explorerWidth, 10);

            // The explorer beside the content, its splitter in the navigation colours; the
            // explorer's width is kept whenever the user changes it, so a rebuild (e.g. docking
            // the AI panel) keeps it too
            final SplitPane splitPanel = new SplitPane();
            splitPanel.setThin(true);
            splitPanel.addStyleName("split-pane--navigation");
            splitPanel.addStyleName("mainViewImpl-splitPanel");
            splitPanel.setSizes(splitWidth + "px, *");
            splitPanel.setNames("Explorer, Content");
            splitPanel.setOnResized(this::captureSplitPos);
            if (westWidget != null) {
                splitPanel.add(westWidget);
            }
            if (centerWidget != null) {
                splitPanel.add(centerWidget);
            }
            innerContent = splitPanel;
        }

        // ── Step 4: Wrap with dock panel if AI is docked ──
        if (state.docked && state.dockedWidget != null) {
            // The docked panel on its edge of the content (and the main toolbar), at the size it
            // was given; a size the user drags it to is reported
            final ResizeFlowPanel contentWrapper = new ResizeFlowPanel();
            contentWrapper.addStyleName("mainViewImpl-contentWrapper");
            innerContent.setSize("100%", "100%");
            // The toolbar first, so that the Tab key reaches it before the content it is shown over
            contentWrapper.add(mainToolbar);
            contentWrapper.add(innerContent);

            final String dockSize = Math.round(state.dockSize) + "px";
            final boolean dockedFirst = state.dockLocation == DockLocation.LEFT
                                        || state.dockLocation == DockLocation.TOP;
            outerSplitPanel = new SplitPane();
            outerSplitPanel.setThin(true);
            outerSplitPanel.setVertical(state.dockLocation == DockLocation.TOP
                                        || state.dockLocation == DockLocation.BOTTOM);
            outerSplitPanel.addStyleName("mainViewImpl-outerSplitPanel");
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
            contentPanel.setWidget(innerContent);
        }
    }

    // ── Size capture helpers ────────────────────────────────────────────────

    // Capture current explorer width from the live DOM before hiding it.
    private void captureSplitPos() {
        if (westWidget != null && westWidget.getOffsetWidth() > 0) {
            state.explorerWidth = westWidget.getOffsetWidth();
        }
    }

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
            root.getElement().setPropertyString("style", style);
        }
    }

    /// @param colour The theme's colour of the selected document tab, or null or blank for the default.
    public void setSelectedTabColour(final String colour) {
        if (colour != null && !colour.isBlank()) {
            DynamicStyles.put(SafeHtmlUtils.fromTrustedString(".tab-bar--curved .tab-bar__tab[aria-selected=\"true\"]"),
                    SafeStylesUtils.fromTrustedNameAndValue("border-bottom-color", colour));
        }
    }

    /// Moves the focus to the next (or previous) main area that is shown: the main toolbar,
    /// the explorer and the open content, going round.
    ///
    /// @param backwards Whether to go to the previous area.
    public void focusArea(final boolean backwards) {
        final List<Widget> areas = new ArrayList<>();
        for (final Widget area : new Widget[]{mainToolbar, westWidget, centerWidget}) {
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
        focusAreaWidget(area, area == westWidget
                ? EXPLORER_START_SELECTOR
                : area == centerWidget
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

    private static void setLandmark(final Widget widget, final String role, final String name) {
        if (widget != null) {
            widget.getElement().setAttribute("role", role);
            if (name != null) {
                widget.getElement().setAttribute("aria-label", name);
            }
        }
    }

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
    // rebuildLayout() to construct the correct widget tree.
    private static class LayoutState {

        boolean explorerMaximised;
        int explorerWidth = 300;

        boolean docked;
        DockLocation dockLocation;
        double dockSize;
        Widget dockedWidget;

        IsWidget maximisedView;
    }

}
