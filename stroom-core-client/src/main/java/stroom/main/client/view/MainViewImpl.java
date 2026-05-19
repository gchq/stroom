/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.ai.client.AskStroomAiPresenter.DockLocation;
import stroom.main.client.event.DockResizeEvent;
import stroom.main.client.presenter.MainPresenter;
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
import com.google.gwt.user.client.ui.ResizeLayoutPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
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
    private Widget maximisedWidget;
    private int splitPos = 300;
    private ThinSplitLayoutPanel splitPanel;
    private Widget westWidget;
    private Widget centerWidget;
    private String currentBanner;

    // Dock state
    private ThinSplitLayoutPanel outerSplitPanel;
    private FlowPanel contentWrapper;
    private Widget dockedWidget;
    private DockLocation dockLocation;
    private double dockSize;

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
            showSplit();
        } else if (slot == MainPresenter.CONTENT) {
            centerWidget = content;
            showSplit();
        } else {
            super.setInSlot(slot, content);
        }
    }

    @Override
    public void maximise(final View view) {
        if (view == null) {

            if (maximisedWidget == null) {
                // Remember split panel.
                if (westWidget != null) {
                    splitPos = westWidget.getOffsetWidth();
                }

                // Maximise the passed view.
                centerWidget.getElement().addClassName("maximised");

                // Clear the split panel.
                hideSplit();
                maximisedWidget = centerWidget;

                // Set the maximised widget as the inner content.
                setInnerContent(maximisedWidget);

                if (maximisedWidget instanceof Focus) {
                    ((Focus) maximisedWidget).focus();
                }
            } else {
                centerWidget.getElement().removeClassName("maximised");

                // Restore the view.
                showSplit();
                maximisedWidget = null;

                if (westWidget instanceof Focus) {
                    ((Focus) westWidget).focus();
                }
            }

        } else {
            final Widget widget = view.asWidget();
            if (maximisedWidget == null || maximisedWidget != widget) {
                // Remember split panel.
                if (westWidget != null) {
                    splitPos = westWidget.getOffsetWidth();
                }

                // Maximise the passed view.
                // Clear the split panel.
                hideSplit();
                maximisedWidget = widget;

                // Set the maximised widget as the inner content.
                setInnerContent(maximisedWidget);

                if (maximisedWidget instanceof Focus) {
                    ((Focus) maximisedWidget).focus();
                }
            } else {
                // Restore the view.
                showSplit();
                maximisedWidget = null;

                if (westWidget instanceof Focus) {
                    ((Focus) westWidget).focus();
                }
            }
        }
    }

    private void showSplit() {
        // Ensure the split position isn't too small.
        if (splitPos < 10) {
            splitPos = 10;
        }

        splitPanel = new ThinSplitLayoutPanel();
        splitPanel.addStyleName("mainViewImpl-splitPanel");
        if (westWidget != null) {
            splitPanel.addWest(westWidget, splitPos);
        }
        if (centerWidget != null) {
            splitPanel.add(centerWidget);
        }

        setInnerContent(splitPanel);
    }

    private void hideSplit() {
        if (splitPanel != null) {
            splitPanel.clear();
            splitPanel = null;
        }
    }

    /**
     * Sets the inner content widget, respecting the outer dock panel if one is active.
     * If docked, the inner content becomes the center of the outer split panel.
     * If not docked, the inner content goes directly into contentPanel.
     */
    private void setInnerContent(final Widget innerContent) {
        if (dockedWidget != null && outerSplitPanel != null) {
            // Rebuild the outer split panel with the new inner content.
            rebuildOuterSplit(innerContent);
        } else {
            contentPanel.clear();
            contentPanel.setWidget(innerContent);
        }
    }

    /**
     * Gets the current inner content widget (the split panel or maximised widget).
     */
    private Widget getInnerContent() {
        if (maximisedWidget != null) {
            return maximisedWidget;
        } else if (splitPanel != null) {
            return splitPanel;
        }
        return centerWidget;
    }

    @Override
    public void dock(final Widget widget,
                     final DockLocation dockLocation,
                     final Size size) {
        this.dockedWidget = widget;
        this.dockLocation = dockLocation;
        this.dockSize = getDockDimension(dockLocation, size);

        // Get the current inner content (split panel or maximised widget).
        final Widget innerContent = getInnerContent();

        // Build the outer split panel with the dock widget and inner content.
        rebuildOuterSplit(innerContent);
    }

    @Override
    public void undock() {
        if (dockedWidget == null) {
            return;
        }

        // Get the inner content before tearing down the outer split.
        final Widget innerContent = getInnerContent();

        // Tear down the outer split panel and content wrapper.
        if (outerSplitPanel != null) {
            outerSplitPanel.clear();
            outerSplitPanel = null;
        }
        contentWrapper = null;

        dockedWidget = null;
        dockLocation = null;

        // Move spinner and menu back to the main FlowPanel.
        main.add(mainToolbar);

        // Restore the inner content directly into contentPanel.
        contentPanel.clear();
        contentPanel.setWidget(innerContent);
    }

    private void rebuildOuterSplit(final Widget innerContent) {
        outerSplitPanel = new ThinSplitLayoutPanel();
        outerSplitPanel.addStyleName("mainViewImpl-outerSplitPanel");

        // Add the docked widget on the appropriate edge.
        switch (dockLocation) {
            case RIGHT:
                outerSplitPanel.addEast(dockedWidget, dockSize);
                break;
            case LEFT:
                outerSplitPanel.addWest(dockedWidget, dockSize);
                break;
            case TOP:
                outerSplitPanel.addNorth(dockedWidget, dockSize);
                break;
            case BOTTOM:
                outerSplitPanel.addSouth(dockedWidget, dockSize);
                break;
        }

        // Wrap the inner content with the spinner and menu in a position:relative
        // container so they stay anchored to the content area, not the full layout.
        contentWrapper = new FlowPanel();
        contentWrapper.addStyleName("mainViewImpl-contentWrapper");
        contentWrapper.add(innerContent);
        contentWrapper.add(mainToolbar);

        // The content wrapper is the center of the outer split panel.
        outerSplitPanel.add(contentWrapper);

        contentPanel.clear();
        contentPanel.setWidget(outerSplitPanel);

        // Force a deferred layout to fix initial positioning —
        // DockLayoutPanel computes absolute positions from parent dimensions,
        // which may not be final until after the browser completes its layout pass.
        Scheduler.get().scheduleDeferred(() -> {
            if (outerSplitPanel != null) {
                outerSplitPanel.forceLayout();
            }
        });

        // Schedule a deferred check for splitter resize to persist the new size.
        scheduleDockResizeCheck();
    }

    private double getDockDimension(final DockLocation location, final Size size) {
        if (location == DockLocation.LEFT || location == DockLocation.RIGHT) {
            return size.getWidth();
        } else {
            return size.getHeight();
        }
    }

    /**
     * Periodically checks if the dock panel size has changed (via splitter drag)
     * and fires a DockResizeEvent when it does.
     */
    private void scheduleDockResizeCheck() {
        if (dockedWidget == null || outerSplitPanel == null) {
            return;
        }
        // Use a deferred command to observe the actual rendered size after layout.
        Scheduler.get().scheduleDeferred(() -> {
            if (dockedWidget != null && outerSplitPanel != null) {
                final double currentSize;
                if (dockLocation == DockLocation.LEFT || dockLocation == DockLocation.RIGHT) {
                    currentSize = dockedWidget.getOffsetWidth();
                } else {
                    currentSize = dockedWidget.getOffsetHeight();
                }
                if (currentSize > 0 && Double.compare(currentSize, dockSize) != 0) {
                    dockSize = currentSize;
                    final Size newSize = new Size.Builder()
                            .width(dockedWidget.getOffsetWidth())
                            .height(dockedWidget.getOffsetHeight())
                            .build();
                    DockResizeEvent.fire(event -> eventBus.fireEvent(event), newSize);
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
            DynamicStyles.put(SafeHtmlUtils.fromTrustedString(".curveTab-selected"),
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
}
