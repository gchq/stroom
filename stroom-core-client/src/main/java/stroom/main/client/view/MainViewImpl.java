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

package stroom.main.client.view;

import stroom.ai.shared.AskStroomAiConfig.DockLocation;
import stroom.main.client.event.DockResizeEvent;
import stroom.main.client.presenter.MainPresenter;
import stroom.widget.layout.client.SplitPane;
import stroom.widget.util.client.Size;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.ResizeFlowPanel;
import com.google.gwt.user.client.ui.ResizeLayoutPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.Objects;

public class MainViewImpl extends ViewImpl implements MainPresenter.MainView {

    private final Widget widget;
    private final EventBus eventBus;

    @UiField
    FocusPanel root;
    @UiField
    SimplePanel banner;
    @UiField
    FlowPanel main;
    @UiField
    ResizeLayoutPanel contentPanel;
    @UiField
    MainToolbar mainToolbar;

    // Persistent content widgets — set via setInSlot, never destroyed.
    private Widget westWidget;
    private Widget centerWidget;

    // Layout state — the single source of truth for how the layout should look.
    private final LayoutState state = new LayoutState();

    private SplitPane outerSplitPanel;

    private String currentBanner;

    @Inject
    public MainViewImpl(final Binder binder,
                        final EventBus eventBus) {
        this.widget = binder.createAndBindUi(this);
        this.eventBus = eventBus;
        banner.setVisible(false);
        widget.sinkEvents(Event.KEYEVENTS);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (slot == MainPresenter.EXPLORER) {
            westWidget = content;
            rebuildLayout();
        } else if (slot == MainPresenter.CONTENT) {
            centerWidget = content;
            rebuildLayout();
        } else {
            super.setInSlot(slot, content);
        }
    }

    @Override
    public void maximise(final View view) {
        if (view == null) {
            // Toggle explorer visibility.
            if (!state.explorerMaximised) {
                captureSplitPos();
                state.explorerMaximised = true;
            } else {
                state.explorerMaximised = false;
            }
            state.maximisedView = null;
        } else {
            // Toggle specific view maximise.
            if (state.maximisedView == null || state.maximisedView != view) {
                captureSplitPos();
                state.maximisedView = view;
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

    @Override
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

    @Override
    public void undock() {
        if (!state.docked) {
            return;
        }
        captureDockSize();
        state.docked = false;
        // Note: we null the widget in state but the widget itself is just
        // detached — AskStroomAiPresenter still holds a reference to it.
        state.dockedWidget = null;
        state.dockLocation = null;
        rebuildLayout();
    }

    // ── Layout rebuild ──────────────────────────────────────────────────────

    /**
     * Builds the correct widget tree from the current {@link LayoutState}.
     * <p>
     * Persistent content widgets (westWidget, centerWidget, state.dockedWidget,
     * mainToolbar) are detached from their current parents and re-attached
     * into fresh disposable containers. This preserves all DOM state, event
     * handlers, scroll positions, and presenter bindings.
     * </p>
     */
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
            contentWrapper.add(innerContent);
            contentWrapper.add(mainToolbar);

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
            // No dock — toolbar goes back to the main FlowPanel.
            main.add(mainToolbar);
            contentPanel.setWidget(innerContent);
        }
    }

    // ── Size capture helpers ────────────────────────────────────────────────

    /**
     * Capture current explorer width from the live DOM before hiding it.
     */
    private void captureSplitPos() {
        if (westWidget != null && westWidget.getOffsetWidth() > 0) {
            state.explorerWidth = westWidget.getOffsetWidth();
        }
    }

    /**
     * Capture current dock size from the live DOM before undocking/re-docking.
     */
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

    /**
     * Checks if the dock panel size has changed (the user moved its splitter)
     * and fires a DockResizeEvent when it does.
     */
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
                    DockResizeEvent.fire(eventBus::fireEvent, newSize);
                }
            }
        });
    }

    @Override
    public MainToolbar getMainToolBar() {
        return mainToolbar;
    }

    @Override
    public void setBorderStyle(final String style) {
        if (style != null && !style.isEmpty()) {
            root.getElement().setPropertyString("style", style);
        }
    }

    @Override
    public void setSelectedTabColour(final String colour) {
        if (colour != null && !colour.isBlank()) {
            DynamicStyles.put(SafeHtmlUtils.fromTrustedString(".tab-bar--curved .tab-bar__tab[aria-selected=\"true\"]"),
                    SafeStylesUtils.fromTrustedNameAndValue("border-bottom-color", colour));
        }
    }

    @Override
    public void setBanner(final String text) {
        if (!Objects.equals(currentBanner, text)) {
            currentBanner = text;
            if (text == null || text.trim().isEmpty()) {
                main.getElement().getStyle().setTop(0, Unit.PX);
                banner.setVisible(false);
                banner.getElement().setInnerText("");
            } else {
                main.getElement().getStyle().setTop(20, Unit.PX);
                banner.setVisible(true);
                banner.getElement().setInnerText(text);
            }
        }
    }

    public interface Binder extends UiBinder<Widget, MainViewImpl> {

    }

    /**
     * Captures all layout state as a single source of truth.
     * All state-changing methods update this object, then call
     * {@link #rebuildLayout()} to construct the correct widget tree.
     */
    private static class LayoutState {

        boolean explorerMaximised;
        int explorerWidth = 300;

        boolean docked;
        DockLocation dockLocation;
        double dockSize;
        Widget dockedWidget;

        View maximisedView;
    }

}
