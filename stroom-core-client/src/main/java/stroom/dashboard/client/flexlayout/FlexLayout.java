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

package stroom.dashboard.client.flexlayout;

import stroom.dashboard.client.flexlayout.Splitter.SplitInfo;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.Components;
import stroom.dashboard.client.main.TabManager;
import stroom.dashboard.shared.Dimension;
import stroom.dashboard.shared.LayoutConfig;
import stroom.dashboard.shared.LayoutConstraints;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.data.grid.client.Glass;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.view.GlobalResizeObserver;
import stroom.widget.tab.client.view.LinkTab;
import stroom.widget.tab.client.view.LinkTabBar;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.Rect;
import stroom.widget.util.client.Size;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import javax.inject.Inject;

public class FlexLayout extends Composite {

    private static final double DRAG_ZONE = 20;
    private static final double MIN_COMPONENT_WIDTH = 50;
    private static final double SPLIT_SIZE = 1;
    private static final double SPLIT_OFFSET = 0;
    private static final double OUTER_SPLIT_BOUNDARY = 5;
    private static Glass marker;
    private static Glass glass;
    private final EventBus eventBus;
    private final FlowPanel designSurface;
    private final SimplePanel scrollPanel;
    private final Map<Object, PositionAndSize> positionAndSizeMap = new HashMap<>();
    private final Map<SplitInfo, Splitter> splitToWidgetMap = new HashMap<>();
    private final Map<LayoutConfig, TabLayout> layoutToWidgetMap = new HashMap<>();
    private final Element designSurfaceElement;
    private Components components;
    private final LayoutConfigContainer layoutConfigContainer = new LayoutConfigContainer();
    private boolean maximised;
    private LayoutConstraints layoutConstraints;
    private stroom.dashboard.shared.Size preferredSize;
    private double offset;
    private double min;
    private double max;
    private boolean mouseDown;
    private boolean busy;
    private Element targetElement;
    private MouseTarget selection;
    private double[] startPos;
    private boolean draggingTab;
    private Splitter selectedSplitter;
    private boolean clear = true;
    private TabManager tabManager;

    private FlexLayoutChangeHandler changeHandler;

    private Size outerSize;
    private Size designSurfaceSize;
    private final SplitInfo outerAcrossSplit =
            new SplitInfo(new SplitLayoutConfig(Dimension.X), -1);
    private final SplitInfo outerDownSplit =
            new SplitInfo(new SplitLayoutConfig(Dimension.Y), -1);

    private boolean designMode;

    private List<Component> newComponents;
    private Component addedComponent;

    @Inject
    public FlexLayout(final EventBus eventBus) {
        this.eventBus = eventBus;

        if (glass == null) {
            glass = new Glass("flexLayout-glass", "flexLayout-glassVisible");
        }
        if (marker == null) {
            marker = new Glass("flexLayout-marker", "flexLayout-markerVisible");
        }

        designSurface = new FlowPanel();
        designSurface.setStyleName("dashboard-designSurface");

        final SimplePanel layout = new SimplePanel(designSurface) {
            @Override
            protected void onAttach() {
                super.onAttach();
                GlobalResizeObserver.addListener(getElement(), e -> onResize());
            }

            @Override
            protected void onDetach() {
                GlobalResizeObserver.removeListener(getElement());
                super.onDetach();
            }
        };
        layout.setStyleName("dashboard-layout");

        scrollPanel = new SimplePanel(layout);
        scrollPanel.setStyleName("dashboard-scrollPanel");
        scrollPanel.addDomHandler(event -> resizeChildWidgets(), ScrollEvent.getType());

        final SimplePanel outerPanel = new SimplePanel(scrollPanel);
        outerPanel.setStyleName("dashboard-outerPanel");

        initWidget(outerPanel);

        designSurfaceElement = designSurface.getElement();
        sinkEvents(Event.ONMOUSEMOVE | Event.ONMOUSEDOWN | Event.ONMOUSEUP);
    }

    public void setTabManager(final TabManager tabManager) {
        this.tabManager = tabManager;
    }

    @Override
    public void onBrowserEvent(final Event event) {
        final int eventType = event.getTypeInt();
//        if (designMode) {
        if (Event.ONMOUSEMOVE == eventType) {
            onMouseMove(event);
        } else if (MouseUtil.isPrimary(event)) {
            if (Event.ONMOUSEDOWN == eventType) {
                onMouseDown(event);
            } else if (Event.ONMOUSEUP == eventType) {
                onMouseUp(event);
            }
        }
//        }
//        else if (MouseUtil.isPrimary(event) && Event.ONMOUSEUP == eventType) {
//            showMenu(event);
//        }
    }

    private void onMouseMove(final Event event) {
        // We need to know what the mouse is over.
        final double x = event.getClientX();
        final double y = event.getClientY();
        final Element target = event.getEventTarget().cast();

//        // If we are adding a new component then let the user start to select where to put it.
//        if (!busy && newComponents != null) {
//            enterNewComponentDestinationMode(newComponents, x, y);
//        }

        if (mouseDown) {
            // The mouse is down, so we might be moving a splitter or dragging
            // (or about to drag) a tab.
            if (busy) {
                // Busy means the mouse is down on a splitter or tab.
                if (selectedSplitter != null) {
                    moveSplit(x, y);

                } else if (selection != null) {
                    if (!maximised && !draggingTab) {
                        // See if the use wants to start dragging this tab.
                        if ((Math.abs(startPos[0] - event.getClientX()) > DRAG_ZONE)
                                || (Math.abs(startPos[1] - event.getClientY()) > DRAG_ZONE)) {
                            // The user has exceeded the drag zone so start
                            // dragging.
                            draggingTab = true;
                            selection.tabWidget.setHighlight(true);
                        }
                    }

                    if (draggingTab) {
                        final MouseTarget mouseTarget = getMouseTarget(x, y, true, false);
                        highlightMouseTarget(mouseTarget);
                    }
                }

                event.preventDefault();
            }

        } else {
            // If the mouse isn't down then we want to highlight splitters if
            // the mouse moves over them.
            selectedSplitter = getTargetSplitter(target);
        }
    }

    private void onMouseDown(final Event event) {
        if (newComponents == null) {
            if (!busy) {
                startSelection(event);
            }
        } else {
            finishSelection(event);
        }
    }

    private void onMouseUp(final Event event) {
        if (newComponents == null) {
            finishSelection(event);
        }
    }

    private void startSelection(final Event event) {
        // Reset vars.
        startPos = null;
        draggingTab = false;
        setDefaultCursor();

        // Set the mouse down flag.
        mouseDown = true;

        // Find the target.
        final double x = event.getClientX();
        final double y = event.getClientY();
        final MouseTarget mouseTarget = getMouseTarget(x, y, true, true);

        // We need to know what the mouse is over.
        final Element element = event.getEventTarget().cast();

        // See if a splitter is the target.
        selectedSplitter = getTargetSplitter(element);

        if (selectedSplitter != null) {
            // If we found a splitter as the event target then start split
            // resize handling.
            startPos = new double[]{x, y};
            startSplitResize(x, y);
            busy = true;

        } else if (mouseTarget != null) {
            // If the event target was not a splitter then see if it was a tab.
            final List<TabData> tabs = mouseTarget.tabs;
            if (tabs != null && Pos.TAB == mouseTarget.pos) {
                selection = mouseTarget;
                // The event target was a tab so store the start position of
                // the mouse.
                startPos = new double[]{x, y};
                busy = true;
            }
        }

        // If we have clicked a hotspot then capture the mouse.
        if (busy) {
            capture();
            event.preventDefault();
            targetElement = element.getParentElement();
            targetElement.addClassName("flexLayout-selected");
        }
    }

    private void showMenu(final Event event) {
        final double x = event.getClientX();
        final double y = event.getClientY();

        // Find the selection target.
        final MouseTarget mouseTarget = getMouseTarget(x, y, true, true);
        if (mouseTarget != null) {
            // If the event target was not a splitter then see if it was a tab.
//            final TabData tab = mouseTarget.getFirstTab();
//            if (Objects.equals(selection.getFirstTab(), tab)) {
//                final TabLayout tabLayout = mouseTarget.tabLayout;
            if (tabManager != null && mouseTarget.tabLayout != null && mouseTarget.tabWidget != null) {
                final TabConfig tabConfig = mouseTarget.tabLayout.getTabLayoutConfig().get(mouseTarget.tabIndex);
                tabManager.showMenu(
                        mouseTarget.tabWidget.getElement(),
                        this,
                        mouseTarget.tabLayout,
                        tabConfig);
            }
//            }
        }
    }

    private void finishSelection(final Event event) {
        final double x = event.getClientX();
        final double y = event.getClientY();

        if (selectedSplitter != null) {
            if (startPos != null) {
                stopSplitResize(x, y);
            }

        } else if (selection != null) {
            if (draggingTab) {
                // Find the drop target.
                final MouseTarget mouseTarget = getMouseTarget(x, y, true, false);

                // If a drag target is found then move the selected tab.
                if (mouseTarget != null) {
                    final Pos targetPos = mouseTarget.pos;
                    final LayoutConfig targetLayout = mouseTarget.layoutConfig;

                    final List<TabConfig> tabGroup = new ArrayList<>();
                    for (final TabData tabData : selection.tabs) {
                        tabGroup.add(((Component) tabData).getTabConfig());
                    }

                    // Add all invisible tabs, so we can move them all together if this is the only tab.
                    final TabLayoutConfig currentParent = selection.currentParent;
                    if (currentParent != null && currentParent.getVisibleTabCount() == 1) {
                        for (final TabConfig tabConfig : currentParent.getTabs()) {
                            if (!tabConfig.visible()) {
                                tabGroup.add(tabConfig);
                            }
                        }
                    }

                    final boolean changed = moveTab(mouseTarget, tabGroup, targetLayout, targetPos);

                    if (changed) {
                        // Show the component settings now we have placed.
                        if (addedComponent != null) {
                            addedComponent.showSettings();
                        }

                        // Don't keep trying to add the same new component.
                        addedComponent = null;
                        newComponents = null;

                        // Clear and refresh the layout.
                        clear();
                        refresh();

                        // Let the handler know the layout is dirty.
                        changeHandler.onDirty();
                    }
                }

                // Remove the highlight from the hotspot.
                if (selection.tabWidget != null) {
                    selection.tabWidget.setHighlight(false);
                }

            } else {
                // Find the selection target.
                final MouseTarget mouseTarget = getMouseTarget(x, y, true, true);
                if (mouseTarget != null) {
                    // If the event target was not a splitter then see if it was a tab.
                    final TabData tab = mouseTarget.getFirstTab();
                    if (Objects.equals(selection.getFirstTab(), tab)) {
                        final TabLayout tabLayout = mouseTarget.tabLayout;
                        final int index = tabLayout.getTabBar().getTabs().indexOf(tab);
                        if (tabLayout.getTabLayoutConfig().getSelected() == null
                                || !tabLayout.getTabLayoutConfig().getSelected().equals(index)) {
                            tabLayout.selectTab(index);
                            tabLayout.getTabLayoutConfig().setSelected(index);

                            if (!maximised) {
                                // Let the handler know the layout is dirty.
                                changeHandler.onDirty();
                            }
                        } else if (tabManager != null) {
                            final TabConfig tabConfig = mouseTarget
                                    .tabLayout
                                    .getTabLayoutConfig()
                                    .get(mouseTarget.tabIndex);
                            tabManager.showMenu(
                                    mouseTarget.tabWidget.getElement(),
                                    this,
                                    tabLayout,
                                    tabConfig);
                        }
                    }
                }
            }
        }

        releaseCapture();
        event.preventDefault();

        // Reset vars.
        startPos = null;
        draggingTab = false;
        busy = false;

        // Reset the mouse down flag.
        mouseDown = false;

        if (targetElement != null) {
            targetElement.removeClassName("flexLayout-selected");
            targetElement = null;
        }
    }

    public void enterNewComponentDestinationMode(
            final Component addedComponent,
            final List<Component> newComponents,
            final double x,
            final double y) {
        this.addedComponent = addedComponent;
        this.newComponents = newComponents;

        // Reset vars.
        selectedSplitter = null;
        setDefaultCursor();

        // Set the mouse down flag.
        mouseDown = true;

        // We need to know what the mouse is over.
        final List<TabData> tabs = new ArrayList<>(newComponents);
        selection = new MouseTarget(
                null,
                null,
                Pos.TAB,
                null,
                null,
                tabs,
                -1,
                null);

        // The event target was a tab so store the start position of
        // the mouse.
        busy = true;
        draggingTab = true;

        capture();

        final MouseTarget mouseTarget = getMouseTarget(x, y, true, false);
        highlightMouseTarget(mouseTarget);
    }

    private boolean moveTab(final MouseTarget mouseTarget,
                            final List<TabConfig> tabGroup,
                            final LayoutConfig targetLayout,
                            final Pos targetPos) {
        boolean moved = false;

        if (targetLayout instanceof TabLayoutConfig) {
            final TabLayoutConfig targetTabLayoutConfig = (TabLayoutConfig) targetLayout;

            if (Pos.CENTER == targetPos || Pos.TAB == targetPos || Pos.AFTER_TAB == targetPos) {
                moved = moveTabOntoTab(mouseTarget, tabGroup, targetTabLayoutConfig, targetPos);

            } else {
                moved = moveTabOutside(mouseTarget, tabGroup, targetTabLayoutConfig, targetPos);
            }

        } else if (targetLayout instanceof SplitLayoutConfig) {
            final SplitLayoutConfig targetSplitLayoutConfig = (SplitLayoutConfig) targetLayout;

            moved = moveTabOntoSplit(mouseTarget, tabGroup, targetSplitLayoutConfig, targetPos);
        }

        return moved;
    }

    private boolean moveTabOntoTab(final MouseTarget mouseTarget,
                                   final List<TabConfig> tabGroup,
                                   final TabLayoutConfig targetTabLayoutConfig,
                                   final Pos targetPos) {
//        GWT.log("moveTabOntoTab");
        boolean moved = false;

        for (final TabConfig tabConfig : tabGroup) {
            final TabLayoutConfig currentParent = tabConfig.getParent();

            // If we don't already have a parent (i.e. a new component) then just add the tab.
            if (currentParent == null) {
                // Figure out what the target index ought to be.
                int targetIndex = targetTabLayoutConfig.getTabs().size();
                if (mouseTarget.tabIndex != -1) {
                    targetIndex = mouseTarget.tabIndex;
                }

                // Add the tab to the target tab layout.
                targetTabLayoutConfig.add(targetIndex, tabConfig);

                // Select the new tab.
                targetTabLayoutConfig.setSelected(targetIndex);

                moved = true;

            } else if (!currentParent.equals(targetTabLayoutConfig) ||
                    currentParent.getVisibleTabCount() > 1) {
                // If dropping a tab onto the same container that
                // only has this one tab then do nothing.

                // Change the selected tab in the source tab
                // layout if we have moved the selected tab.
                final int sourceIndex = currentParent.indexOf(tabConfig);
                final Integer selectedIndex = currentParent.getSelected();
                if (selectedIndex != null && selectedIndex.equals(sourceIndex)) {
                    currentParent.setSelected(sourceIndex - 1);
                }

                // Figure out what the target index ought to be.
                int targetIndex = targetTabLayoutConfig.getTabs().size();
                if (mouseTarget.tabIndex != -1) {
                    targetIndex = mouseTarget.tabIndex;
                }

                boolean move = true;
                if (currentParent.equals(targetTabLayoutConfig)) {
                    // If we are dropping a tab onto itself then don't move.
                    if (targetIndex == sourceIndex || targetIndex == sourceIndex + 1) {
                        move = false;

                    } else {
                        // If we are dropping a tab after itself then remove 1 from target as we will be removing the
                        // tab from a previous index.
                        if (sourceIndex < targetIndex) {
                            targetIndex--;
                        }
                    }
                }

                if (move) {
                    // Remove the tab if we can.
                    currentParent.remove(tabConfig);

                    // Add the tab to the target tab layout.
                    targetTabLayoutConfig.add(targetIndex, tabConfig);

                    // Select the new tab.
                    targetTabLayoutConfig.setSelected(targetIndex);

                    // Cascade removal if necessary.
                    cascadeRemoval(currentParent);

                    moved = true;
                }
            }
        }

        return moved;
    }

    private boolean moveTabOutside(final MouseTarget mouseTarget,
                                   final List<TabConfig> tabGroup,
                                   final TabLayoutConfig targetTabLayoutConfig,
                                   final Pos targetPos) {
//        GWT.log("moveTabOutside");
        boolean moved = false;

        // Ensure we have a parent.
        SplitLayoutConfig parent = targetTabLayoutConfig.getParent();
        if (parent == null) {
            int dim = Dimension.X;
            if (Pos.TOP == targetPos || Pos.BOTTOM == targetPos) {
                dim = Dimension.Y;
            }
            final SplitLayoutConfig splitLayoutConfig =
                    new SplitLayoutConfig(targetTabLayoutConfig.getPreferredSize().copy().build(), dim);
            splitLayoutConfig.add(targetTabLayoutConfig);

            final PositionAndSize positionAndSize = positionAndSizeMap.get(targetTabLayoutConfig);
            positionAndSizeMap.put(splitLayoutConfig, positionAndSize.copy());

            layoutConfigContainer.set(splitLayoutConfig);
            parent = splitLayoutConfig;
        }

        // Get the index of the target layout and
        // therefore the insert position.
        int insertPos = parent.indexOf(targetTabLayoutConfig);
        final TabLayoutConfig newTabLayout = new TabLayoutConfig();
        newTabLayout.setSelected(0);

        // Move all tabs from the tab group onto the new tab layout.
        for (final TabConfig tabConfig : tabGroup) {
            final TabLayoutConfig currentParent = tabConfig.getParent();

            // If we don't already have a parent (i.e. a new component) then just add the tab.
            if (currentParent == null) {
                newTabLayout.add(tabConfig);

            } else if (!currentParent.equals(targetTabLayoutConfig) ||
                    currentParent.getVisibleTabCount() > 1) {
                // If dropping a tab onto the same container that
                // only has this one tab then do nothing.

                // Change the selected tab in the source tab
                // layout if we have moved the selected tab.
                final int tabIndex = currentParent.indexOf(tabConfig);
                final Integer selectedIndex = currentParent.getSelected();
                if (selectedIndex != null && selectedIndex.equals(tabIndex)) {
                    currentParent.setSelected(tabIndex - 1);
                }

                // Remove the tab.
                currentParent.remove(tabConfig);

                // Cascade removal if necessary.
                cascadeRemoval(currentParent);

                newTabLayout.add(tabConfig);
            }
        }

        // If tabs have been moved to the new tab layout then add the new layout.
        if (newTabLayout.getTabs().size() > 0) {
            // Recalculate the sizes for the parent so that
            // the target layout is resized.
            recalculateSingleLayout(parent);

            int dim = Dimension.X;
            if (Pos.TOP == targetPos || Pos.BOTTOM == targetPos) {
                dim = Dimension.Y;
            }

            // Set the size of the target layout and the new
            // layout to be half the size of the original so
            // combined they take up the same space.
            divideSize(targetTabLayoutConfig,
                    targetTabLayoutConfig,
                    newTabLayout,
                    dim);

            if (parent.getDimension() == dim) {
                if (Pos.RIGHT == targetPos || Pos.BOTTOM == targetPos) {
                    insertPos++;
                }

                // If the parent direction is already across
                // then just add the new tab layout in.
                parent.add(insertPos, newTabLayout);

            } else {
                // We need to replace the target layout with
                // a new split layout.
                parent.remove(targetTabLayoutConfig);

                final stroom.dashboard.shared.Size preferredSize =
                        targetTabLayoutConfig.getPreferredSize().copy().build();
                final SplitLayoutConfig newSplit = new SplitLayoutConfig(preferredSize, dim, null);
                if (Pos.RIGHT == targetPos || Pos.BOTTOM == targetPos) {
                    newSplit.add(targetTabLayoutConfig);
                    newSplit.add(newTabLayout);
                } else {
                    newSplit.add(newTabLayout);
                    newSplit.add(targetTabLayoutConfig);
                }
                parent.add(insertPos, newSplit);
            }

            moved = true;
        }

        return moved;
    }

    private boolean moveTabOntoSplit(final MouseTarget mouseTarget,
                                     final List<TabConfig> tabGroup,
                                     final SplitLayoutConfig targetSplitLayoutConfig,
                                     final Pos targetPos) {
//        GWT.log("moveTabOntoSplit");
        boolean moved = false;

        for (final TabConfig tabConfig : tabGroup) {
            final TabLayoutConfig currentParent = tabConfig.getParent();
            if (Pos.CENTER != targetPos) {
                int dim = Dimension.X;
                if (Pos.TOP == targetPos || Pos.BOTTOM == targetPos) {
                    dim = Dimension.Y;
                }

                // Remove the tab.
                if (currentParent != null) {
                    currentParent.remove(tabConfig);
                }

                // Create a new tab layout for the tab being moved.
                final TabLayoutConfig tabLayoutConfig = new TabLayoutConfig();
                tabLayoutConfig.add(tabConfig);
                tabLayoutConfig.setSelected(0);

                // We can add the new tab layout to the target split if the dimension is the same. If it isn't then we
                // need to create a new split layout with the correct split dimension and insert it.
                if (targetSplitLayoutConfig.getDimension() != dim) {
                    // The target split layout is not the correct dimension so we will need to look at the parent
                    // layout.
                    final SplitLayoutConfig parentSplitLayoutConfig = targetSplitLayoutConfig.getParent();
                    if (parentSplitLayoutConfig == null) {
                        // There is no parent of the target so we will create a new parent split to wrap the current
                        // target and insert the new tab layout in the correct location.
                        final SplitLayoutConfig newSplitLayoutConfig =
                                new SplitLayoutConfig(targetSplitLayoutConfig.getPreferredSize().copy().build(), dim);

                        // Divide the original size between the new children.
                        tabLayoutConfig.setPreferredSize(targetSplitLayoutConfig.getPreferredSize().copy().build());
                        final PositionAndSize positionAndSize = positionAndSizeMap.get(targetSplitLayoutConfig);
                        positionAndSizeMap.put(newSplitLayoutConfig, positionAndSize.copy());
                        divideSize(targetSplitLayoutConfig,
                                targetSplitLayoutConfig,
                                tabLayoutConfig,
                                dim);

                        if (Pos.LEFT == targetPos || Pos.TOP == targetPos) {
                            newSplitLayoutConfig.add(tabLayoutConfig);
                            newSplitLayoutConfig.add(targetSplitLayoutConfig);
                        } else {
                            newSplitLayoutConfig.add(targetSplitLayoutConfig);
                            newSplitLayoutConfig.add(tabLayoutConfig);
                        }

                        layoutConfigContainer.set(newSplitLayoutConfig);

                    } else if (parentSplitLayoutConfig.getDimension() != dim) {
                        // If the parent split dimension is still not what we want then insert a new split layout to
                        // wrap the current target and insert the new tab layout in the correct location.
                        final int insertPos = parentSplitLayoutConfig.indexOf(targetSplitLayoutConfig);
                        parentSplitLayoutConfig.remove(targetSplitLayoutConfig);

                        final SplitLayoutConfig newSplitLayoutConfig =
                                new SplitLayoutConfig(targetSplitLayoutConfig.getPreferredSize().copy().build(), dim);

                        // Divide the original size between the new children.
                        tabLayoutConfig.setPreferredSize(targetSplitLayoutConfig.getPreferredSize().copy().build());
                        final PositionAndSize positionAndSize = positionAndSizeMap.get(targetSplitLayoutConfig);
                        positionAndSizeMap.put(newSplitLayoutConfig, positionAndSize.copy());
                        divideSize(targetSplitLayoutConfig,
                                targetSplitLayoutConfig,
                                tabLayoutConfig,
                                dim);

                        if (Pos.LEFT == targetPos || Pos.TOP == targetPos) {
                            newSplitLayoutConfig.add(tabLayoutConfig);
                            newSplitLayoutConfig.add(targetSplitLayoutConfig);
                        } else {
                            newSplitLayoutConfig.add(targetSplitLayoutConfig);
                            newSplitLayoutConfig.add(tabLayoutConfig);
                        }

                        parentSplitLayoutConfig.add(insertPos, newSplitLayoutConfig);

                    } else {
                        // The parent split dimension is correct so just insert the new tab layout in the correct
                        // location.

                        // Divide the original size between the new children.
                        tabLayoutConfig.setPreferredSize(targetSplitLayoutConfig.getPreferredSize().copy().build());
                        divideSize(targetSplitLayoutConfig,
                                targetSplitLayoutConfig,
                                tabLayoutConfig,
                                dim);

                        final int insertPos = parentSplitLayoutConfig.indexOf(targetSplitLayoutConfig);
                        if (Pos.LEFT == targetPos || Pos.TOP == targetPos) {
                            parentSplitLayoutConfig.add(insertPos, tabLayoutConfig);
                        } else {
                            parentSplitLayoutConfig.add(insertPos + 1, tabLayoutConfig);
                        }
                    }
                } else {
                    // The target layout has the correct split dimension so just insert the new tab layout in the
                    // correct location.

                    // Divide the original size between the new children.
                    tabLayoutConfig.setPreferredSize(targetSplitLayoutConfig.getPreferredSize().copy().build());
                    divideSize(targetSplitLayoutConfig,
                            tabLayoutConfig,
                            tabLayoutConfig,
                            dim);

                    if (Pos.LEFT == targetPos || Pos.TOP == targetPos) {
                        targetSplitLayoutConfig.add(0, tabLayoutConfig);
                    } else {
                        targetSplitLayoutConfig.add(tabLayoutConfig);
                    }
                }

                // Cascade removal if necessary.
                cascadeRemoval(currentParent);

                moved = true;
            }
        }

        return moved;
    }

    private void divideSize(final LayoutConfig originalLayout,
                            final LayoutConfig layout1,
                            final LayoutConfig layout2,
                            final int dim) {
        // Set the size of the target layout and the new
        // layout to be half the size of the original so
        // combined they take up the same space.
        final double oldLayoutSize = positionAndSizeMap.get(originalLayout).getSize(dim);
        double newLayoutSize = oldLayoutSize / 2D;
        newLayoutSize = Math.max(newLayoutSize, MIN_COMPONENT_WIDTH);

        layout1.getPreferredSize().set(dim, (int) newLayoutSize);
        layout2.getPreferredSize().set(dim, (int) newLayoutSize);
    }

    private void capture() {
        glass.show();

        Event.setCapture(getElement());
    }

    private void releaseCapture() {
        glass.hide();
        hideMarker();

        Event.releaseCapture(getElement());
    }

    private void cascadeRemoval(final TabLayoutConfig tabLayoutConfig) {
        if (tabLayoutConfig != null && tabLayoutConfig.getAllTabCount() == 0) {
            LayoutConfig child = tabLayoutConfig;
            SplitLayoutConfig parent = child.getParent();

            if (parent != null) {
                parent.remove(child);
                while (parent != null && parent.count() == 0) {
                    child = parent;
                    parent = child.getParent();
                    if (parent != null) {
                        parent.remove(child);
                    }
                }
            }

            if (parent == null) {
                layoutConfigContainer.set(null);
            }
        }
    }

    private void startSplitResize(final double x, final double y) {
        final SplitInfo splitInfo = selectedSplitter.getSplitInfo();
        final SplitLayoutConfig layoutConfig = splitInfo.getLayoutConfig();
        final int dim = layoutConfig.getDimension();

        final PositionAndSize positionAndSize = positionAndSizeMap.get(layoutConfig);
        final Element elem = selectedSplitter.getElement();

        setMarkerCursor(dim);
        showMarker(
                ElementUtil.getClientLeft(elem),
                ElementUtil.getClientTop(elem),
                ElementUtil.getSubPixelOffsetWidth(elem),
                ElementUtil.getSubPixelOffsetHeight(elem));


        // Calculate the mouse offset from the top or left of the splitter and the min and max positions that the
        // splitter should be able to move to.
        offset = getEventPos(dim, x, y) - getAbsolutePos(dim, elem);
        final double containerPos = getAbsolutePos(dim, designSurfaceElement);

        // If this is a canvas resize split then treat it differently.
        if (splitInfo.getIndex() == -1) {
            min = containerPos + getMinRequired(layoutConfigContainer.get(), dim);
            max = containerPos + designSurfaceSize.get(dim);
        } else {
            final double parentMin = containerPos + positionAndSize.getPos(dim);
            final double parentMax = parentMin + positionAndSize.getSize(dim);
            min = parentMin + getMinRequired(layoutConfig, dim, splitInfo.getIndex(), -1);
            max = parentMax - getMinRequired(layoutConfig, dim, splitInfo.getIndex() + 1, 1);
        }
        min -= SPLIT_OFFSET;
        max -= SPLIT_OFFSET;
    }

    private void setDefaultCursor() {
        marker.getElement().removeClassName("flexLayout-splitter");
        marker.getElement().removeClassName("flexLayout-splitterVisible");
        marker.getElement().removeClassName("flexLayout-splitterDown");
        marker.getElement().removeClassName("flexLayout-splitterAcross");
        setGlassCursor(Cursor.DEFAULT);
    }

    private void setMoveCursor() {
        marker.getElement().removeClassName("flexLayout-splitter");
        marker.getElement().removeClassName("flexLayout-splitterVisible");
        marker.getElement().removeClassName("flexLayout-splitterDown");
        marker.getElement().removeClassName("flexLayout-splitterAcross");
        setGlassCursor(Cursor.MOVE);
    }

    private void setMarkerCursor(final int dim) {
        marker.getElement().addClassName("flexLayout-splitter");
        marker.getElement().addClassName("flexLayout-splitterVisible");

        if (Dimension.X == dim) {
            setGlassCursor(Cursor.COL_RESIZE);
            marker.getElement().addClassName("flexLayout-splitterAcross");
        } else if (Dimension.Y == dim) {
            setGlassCursor(Cursor.ROW_RESIZE);
            marker.getElement().addClassName("flexLayout-splitterDown");
        }
    }

    private void setGlassCursor(final Cursor cursor) {
        glass.getElement().getStyle().setCursor(cursor);
    }

    private void stopSplitResize(final double x, final double y) {
        final SplitInfo splitInfo = selectedSplitter.getSplitInfo();
        final SplitLayoutConfig layoutConfig = splitInfo.getLayoutConfig();
        final int dim = layoutConfig.getDimension();
        final double initialChange = getEventPos(dim, x, y) - startPos[dim];

        // Don't do anything if there is no change.
        if (initialChange != 0) {
            double totalChange = -Math.abs(initialChange);

            // If this is canvas resize split then treat it differently.
            if (splitInfo.getIndex() == -1) {
                min = getMinRequired(layoutConfig, dim);
                max = designSurfaceSize.get(dim);
                final double val = constrain(preferredSize.get(dim) +
                        initialChange, min, max);
                preferredSize.set(dim, (int) val);
                refresh();

            } else {
                // Change code after the split first if the change is positive.
                boolean changeAfterSplit = initialChange > 0;

                // We have got to change sizes before the split and after the split
                // so run the following code twice.
                for (int j = 0; j < 2; j++) {
                    if (changeAfterSplit) {
                        // Change the sizes of items after the split.
                        totalChange =
                                resizeChildren(layoutConfig, totalChange, 1, splitInfo.getIndex() + 1);
                    } else {
                        // Change the sizes of items before the split.
                        totalChange =
                                resizeChildren(layoutConfig, totalChange, -1, splitInfo.getIndex());
                    }

                    changeAfterSplit = !changeAfterSplit;
                }

                // Calculate the position and size of all widgets.
                recalculateSingleLayout(layoutConfig);
                // Set the new position and size of all widgets.
                layout();
            }

            // Let the handler know the layout is dirty.
            changeHandler.onDirty();
        }
    }

    private double resizeChildren(final SplitLayoutConfig layoutConfig,
                                  final double change) {
        return resizeChildren(layoutConfig, change, 1, 0);
    }

    private double resizeChildren(final SplitLayoutConfig layoutConfig,
                                  final double change,
                                  final int step,
                                  final int splitIndex) {
        final int dim = layoutConfig.getDimension();
        double totalChange = change;

        double realChange = 0;
        for (int i = splitIndex; i >= 0 && i < layoutConfig.count() && totalChange != 0; i += step) {
            final LayoutConfig child = layoutConfig.get(i);
            final double currentSize = positionAndSizeMap.get(child).getSize(dim);
            final double minSize = getMinRequired(child, dim);

            double newSize = currentSize + totalChange;
            newSize = Math.max(newSize, minSize);
            child.getPreferredSize().set(dim, (int) newSize);

            final double diff = newSize - currentSize;
            totalChange = totalChange - diff;
            realChange += diff;
        }
        totalChange = -realChange;
        return totalChange;
    }

    private void moveSplit(final double x, final double y) {
        final SplitLayoutConfig layoutConfig = selectedSplitter.getSplitInfo().getLayoutConfig();
        final Element elem = selectedSplitter.getElement();

        setMarkerCursor(layoutConfig.getDimension());
        if (Dimension.X == layoutConfig.getDimension()) {
            double left = x - offset;
            left = constrain(left, min, max);
            setMarkerCursor(Dimension.X);
            showMarker(left,
                    ElementUtil.getClientTop(elem),
                    ElementUtil.getSubPixelOffsetWidth(elem),
                    ElementUtil.getSubPixelOffsetHeight(elem));
        } else {
            double top = y - offset;
            top = constrain(top, min, max);
            setMarkerCursor(Dimension.Y);
            showMarker(ElementUtil.getClientLeft(elem),
                    top,
                    ElementUtil.getSubPixelOffsetWidth(elem),
                    elem.getOffsetHeight());
        }
    }

    private double constrain(final double val, final double min, final double max) {
        return Math.min(Math.max(val, min), max);
    }

    /**
     * Gets the current target of the mouse.
     *
     * @param x The mouse x coordinate.
     * @param y The mouse y coordinate.
     * @return An object describing the target layout, tab and target position.
     */
    private MouseTarget getMouseTarget(final double x,
                                       final double y,
                                       final boolean includeSplitLayout,
                                       final boolean selecting) {
        if (includeSplitLayout) {
            for (final Entry<Object, PositionAndSize> entry : positionAndSizeMap.entrySet()) {
                if (entry.getKey() instanceof SplitLayoutConfig) {
                    final LayoutConfig layoutConfig = (LayoutConfig) entry.getKey();
                    final PositionAndSize positionAndSize = entry.getValue();
                    final MouseTarget mouseTarget = findTargetLayout(
                            x,
                            y,
                            true,
                            layoutConfig,
                            positionAndSize,
                            selecting);
                    if (mouseTarget != null) {
                        return mouseTarget;
                    }
                }
            }
        }

        for (final Entry<Object, PositionAndSize> entry : positionAndSizeMap.entrySet()) {
            if (entry.getKey() instanceof TabLayoutConfig) {
                final LayoutConfig layoutConfig = (LayoutConfig) entry.getKey();
                final PositionAndSize positionAndSize = entry.getValue();
                final MouseTarget mouseTarget = findTargetLayout(
                        x,
                        y,
                        false,
                        layoutConfig,
                        positionAndSize,
                        selecting);
                if (mouseTarget != null) {
                    return mouseTarget;
                }
            }
        }

        return null;
    }

    /**
     * Tests the supplied layout to see if it is the current mouse target and
     * returns mouse target if it is.
     *
     * @param x               The mouse x coordinate.
     * @param y               The mouse y coordinate.
     * @param splitter        True if the layout is a splitter. Splitters are targeted
     *                        outside their rect.
     * @param layoutConfig    The layout data to test to see if it is a target.
     * @param positionAndSize The position and size of the layout being tested.
     * @return The mouse target if the tested layout is the target, else null.
     */
    private MouseTarget findTargetLayout(final double x,
                                         final double y,
                                         final boolean splitter,
                                         final LayoutConfig layoutConfig,
                                         final PositionAndSize positionAndSize,
                                         final boolean selecting) {
        final double width = positionAndSize.getWidth();
        final double height = positionAndSize.getHeight();
        final double left = ElementUtil.getClientLeft(designSurfaceElement) + positionAndSize.getLeft();
        final double right = left + width;
        final double top = ElementUtil.getClientTop(designSurfaceElement) + positionAndSize.getTop();
        final double bottom = top + height;

        // Splitters activate outside their rect so a border is added for
        // activation.
        double border = 0;
        if (splitter) {
            border = OUTER_SPLIT_BOUNDARY;
        }

        // First see if the mouse is even over the layout.
        if (x >= left - border && x <= right + border && y >= top - border && y <= bottom + border) {
            if (!splitter) {
                // If this isn't a splitter then test if the mouse is over a tab.
                final MouseTarget mouseTarget = findTargetTab(
                        x,
                        y,
                        (TabLayoutConfig) layoutConfig,
                        positionAndSize,
                        selecting);
                if (mouseTarget != null) {
                    return mouseTarget;
                }
            }

            final double xDiff = Math.min(x - left, right - x);
            final double yDiff = Math.min(y - top, bottom - y);
            double activeWidth = OUTER_SPLIT_BOUNDARY;
            double activeHeight = OUTER_SPLIT_BOUNDARY;

            if (!splitter) {
                activeWidth = width / 3;
                activeHeight = height / 3;
            }

            Pos pos = Pos.CENTER;

            if (x <= left + activeWidth) {
                pos = Pos.LEFT;
            } else if (x >= right - activeWidth) {
                pos = Pos.RIGHT;
            }

            if (y <= top + activeHeight) {
                if (pos == Pos.LEFT || pos == Pos.RIGHT) {
                    if (yDiff < xDiff) {
                        pos = Pos.TOP;
                    }
                } else {
                    pos = Pos.TOP;
                }
            } else if (y >= bottom - activeHeight) {
                if (pos == Pos.LEFT || pos == Pos.RIGHT) {
                    if (yDiff < xDiff) {
                        pos = Pos.BOTTOM;
                    }
                } else {
                    pos = Pos.BOTTOM;
                }
            }

            if (pos == Pos.CENTER && splitter) {
                return null;
            }

            return new MouseTarget(
                    layoutConfig,
                    positionAndSize,
                    pos,
                    null,
                    null,
                    null,
                    -1,
                    null);
        }

        return null;
    }

    private MouseTarget findTargetTab(final double x,
                                      final double y,
                                      final TabLayoutConfig layoutConfig,
                                      final PositionAndSize positionAndSize,
                                      final boolean selecting) {
        MouseTarget mouseTarget = null;

        final double width = positionAndSize.getWidth();
        final double height = positionAndSize.getHeight();
        final double left = ElementUtil.getClientLeft(designSurfaceElement) + positionAndSize.getLeft();
        final double right = left + width;
        final double top = ElementUtil.getClientTop(designSurfaceElement) + positionAndSize.getTop();
        final double bottom = top + height;

        // First see if the mouse is even over the layout.
        if (x >= left && x <= right && y >= top && y <= bottom) {
            // Test if the mouse is over a tab.
            final TabLayout tabLayout = layoutToWidgetMap.get(layoutConfig);
            if (tabLayout != null && tabLayout.getTabBar().getTabs() != null) {
                final LinkTabBar tabBar = tabLayout.getTabBar();
                if (x >= ElementUtil.getClientLeft(tabBar.getElement()) &&
                        x <= ElementUtil.getClientLeft(tabBar.getElement()) +
                                ElementUtil.getSubPixelOffsetWidth(tabBar.getElement()) &&
                        y >= ElementUtil.getClientTop(tabBar.getElement()) &&
                        y <= ElementUtil.getClientTop(tabBar.getElement()) + tabBar.getOffsetHeight()) {

                    final List<TabConfig> tabConfigList = layoutConfig.getTabs();
                    int visibleTabIndex = 0;
                    for (int i = 0; i < tabConfigList.size(); i++) {
                        final TabConfig tabConfig = tabConfigList.get(i);
                        if (tabConfig.visible()) {
                            final TabData tabData = tabBar.getTabs().get(visibleTabIndex);

                            // This is somewhat confusing but there is a difference between tabs that have been hidden
                            // by the user and tabs that are not currently visible due to UI size constraints.
                            if (tabBar.getVisibleTabs().contains(tabData)) {
                                final LinkTab linkTab = (LinkTab) tabBar.getTab(tabData);
                                final Element tabElement = linkTab.getElement();

                                if (selecting) {
                                    if (x >= ElementUtil.getClientLeft(tabElement) &&
                                            x <= ElementUtil.getClientRight(tabElement)) {
                                        // If the mouse position is left or equal to the right-hand side of the tab
                                        // then this tab might be the one to select.
                                        mouseTarget = new MouseTarget(layoutConfig,
                                                positionAndSize,
                                                Pos.TAB,
                                                tabLayout,
                                                layoutConfig,
                                                Collections.singletonList(tabData),
                                                i,
                                                linkTab);
                                    }
                                } else if (selection == null || !tabData.equals(selection.getFirstTab())) {
                                    final double clientLeft = ElementUtil.getClientLeft(tabElement);
                                    final double clientRight = ElementUtil.getClientRight(tabElement);
//                                    Console.log(tabData.getLabel() +
//                                            " " +
//                                            "clientLeft=" +
//                                            clientLeft +
//                                            " clientRight=" +
//                                            clientRight +
//                                            " x=" +
//                                            x);

                                    if (x >= clientLeft && x <= clientRight) {
                                        // If the mouse position is over the tab then we might want to insert before
                                        // this tab.
                                        mouseTarget = new MouseTarget(layoutConfig,
                                                positionAndSize,
                                                Pos.TAB,
                                                tabLayout,
                                                layoutConfig,
                                                Collections.singletonList(tabData),
                                                i,
                                                linkTab);
//                                        Console.log("tab");

                                    } else if (x > clientRight) {
                                        // If the mouse position is right of the right-hand side of the tab then we
                                        // might want to insert the tab we are dragging after the current tab.
                                        mouseTarget = new MouseTarget(layoutConfig,
                                                positionAndSize,
                                                Pos.AFTER_TAB,
                                                tabLayout,
                                                layoutConfig,
                                                Collections.singletonList(tabData),
                                                i + 1,
                                                linkTab);
//                                        Console.log("after tab");
                                    }
                                }
                            }

                            visibleTabIndex++;
                        }
                    }
                }
            }
        }

        return mouseTarget;
    }

    private void highlightMouseTarget(final MouseTarget mouseTarget) {
        setMoveCursor();
        if (mouseTarget == null) {
            hideMarker();

        } else if (mouseTarget.tabWidget != null) {
            final Element tabElement = mouseTarget.tabWidget.getElement();

            switch (mouseTarget.pos) {
                case TAB:
                    showMarker(ElementUtil.getClientLeft(tabElement),
                            ElementUtil.getClientTop(tabElement),
                            5,
                            tabElement.getOffsetHeight());
                    marker.getElement().addClassName("flexLayout-marker-tab");
                    break;
                case AFTER_TAB:
                    showMarker(ElementUtil.getClientLeft(tabElement) +
                                    ElementUtil.getSubPixelOffsetWidth(tabElement) +
                                    4,
                            ElementUtil.getClientTop(tabElement),
                            5,
                            tabElement.getOffsetHeight());
                    marker.getElement().addClassName("flexLayout-marker-tab");
                    break;
                default:
                    hideMarker();
                    break;
            }

        } else {
            final PositionAndSize positionAndSize = mouseTarget.positionAndSize;
            final double width = positionAndSize.getWidth();
            final double height = positionAndSize.getHeight();
            final double left = ElementUtil.getClientLeft(designSurfaceElement) + positionAndSize.getLeft();
            final double top = ElementUtil.getClientTop(designSurfaceElement) + positionAndSize.getTop();
            final double halfWidth = width / 2D;
            final double halfHeight = height / 2D;

            switch (mouseTarget.pos) {
                case CENTER:
                    showMarker(left, top, width, height);
                    break;
                case LEFT:
                    showMarker(left, top, halfWidth, height);
                    break;
                case RIGHT:
                    showMarker(left + halfWidth, top, halfWidth, height);
                    break;
                case TOP:
                    showMarker(left, top, width, halfHeight);
                    break;
                case BOTTOM:
                    showMarker(left, top + halfHeight, width, halfHeight);
                    break;
                default:
                    hideMarker();
                    break;
            }
        }
    }

    private Splitter getTargetSplitter(final Element target) {
        for (final Splitter splitter : splitToWidgetMap.values()) {
            if (splitter.getElement().isOrHasChild(target)) {
                return splitter;
            }
        }
        return null;
    }

    public void setComponents(final Components components) {
        this.components = components;
    }

    public LayoutConfig getLayoutConfig() {
        return layoutConfigContainer.get();
    }

    public void setDesignMode(final boolean designMode) {
        this.designMode = designMode;
        clear();
        onResize();
    }

    public void setLayoutConstraints(final LayoutConstraints layoutConstraints) {
        this.layoutConstraints = layoutConstraints;

        if (layoutConstraints.isFitWidth()) {
            scrollPanel.removeStyleName("dashboard-scrollPanel--horizontal-scroll");
        } else {
            scrollPanel.addStyleName("dashboard-scrollPanel--horizontal-scroll");
        }
        if (layoutConstraints.isFitHeight()) {
            scrollPanel.removeStyleName("dashboard-scrollPanel--vertical-scroll");
        } else {
            scrollPanel.addStyleName("dashboard-scrollPanel--vertical-scroll");
        }

        clear();
        onResize();
    }

    public void configure(final LayoutConfig layoutConfig,
                          final LayoutConstraints layoutConstraints,
                          final stroom.dashboard.shared.Size preferredSize) {
        layoutConfigContainer.set(layoutConfig);
        this.preferredSize = preferredSize;
        setLayoutConstraints(layoutConstraints);
    }

    private void showMarker(final double left, final double top, final double width, final double height) {
        final Rect outer = ElementUtil.getClientRect(scrollPanel.getElement());
        final Rect inner = new Rect(top, top + height, left, left + width);
        final Rect min = Rect.min(outer, inner);
        marker.getElement().removeClassName("flexLayout-marker-tab");
        marker.show(min);
    }

    private void hideMarker() {
        marker.hide();
    }

    public void onResize() {
        Scheduler.get().scheduleDeferred(this::refresh);
    }

    private void recalculateAll() {
        // Calculate the position and size of all layouts.
        positionAndSizeMap.clear();
        // Add outer splitters to control the canvas size.
        if (designMode) {
            if (!layoutConstraints.isFitWidth()) {
                addCanvasSplit(outerAcrossSplit, outerSize, Dimension.X);
            }
            if (!layoutConstraints.isFitHeight()) {
                addCanvasSplit(outerDownSplit, outerSize, Dimension.Y);
            }
        }
        // Recalculate widgets and splitters.
        recalculate(layoutConfigContainer.get(), 0, 0, outerSize.getWidth(), outerSize.getHeight());
    }

    public void refresh() {
        Scheduler.get().scheduleDeferred(this::doRefresh);
    }

    public stroom.dashboard.shared.Size getVisibleSize() {
        final double visibleWidth = Math.floor(ElementUtil.getSubPixelOffsetWidth(getElement()));
        final double visibleHeight = Math.floor(ElementUtil.getSubPixelOffsetHeight(getElement()));
        return new stroom.dashboard.shared.Size((int) visibleWidth, (int) visibleHeight);
    }

    public void doRefresh() {
        if (layoutConfigContainer.get() != null) {
            double minWidth = getMinRequired(layoutConfigContainer.get(), Dimension.X);
            double minHeight = getMinRequired(layoutConfigContainer.get(), Dimension.Y);

            final double visibleWidth = Math.floor(ElementUtil.getSubPixelClientWidth(scrollPanel.getElement()));
            final double visibleHeight = Math.floor(ElementUtil.getSubPixelClientHeight(scrollPanel.getElement()));
//            GWT.log(getElement() + " visibleWidth=" + visibleWidth + " visibleHeight=" + visibleHeight);
//            double barWidth = 12;
//            boolean scrollX = false;
//            boolean scrollY = false;

            if (!layoutConstraints.isFitWidth()) {
                if (preferredSize.getWidth() > 0) {
                    minWidth = Math.max(preferredSize.getWidth(), minWidth);
                } else {
                    minWidth = getMaxRequired(layoutConfigContainer.get(), Dimension.X);
                }
                preferredSize.setWidth((int) minWidth);
            }
            if (!layoutConstraints.isFitHeight()) {
                if (preferredSize.getHeight() > 0) {
                    minHeight = Math.max(preferredSize.getHeight(), minHeight);
                } else {
                    minHeight = getMaxRequired(layoutConfigContainer.get(), Dimension.Y);
                }
                preferredSize.setHeight((int) minHeight);
            }

            double designWidth = minWidth;
            double designHeight = minHeight;
            if (designMode) {
                if (!layoutConstraints.isFitWidth()) {
                    designWidth = minWidth * 2;
                }
                if (!layoutConstraints.isFitHeight()) {
                    designHeight = minHeight * 2;
                }
            }

//            for (int i = 0; i < 2; i++) {
//                if (!scrollX) {
//                    if (designWidth > visibleWidth) {
//                        scrollX = true;
//                        visibleHeight -= barWidth;
//                    }
//                }
//
//                if (!scrollY) {
//                    if (designHeight > visibleHeight) {
//                        scrollY = true;
//                        visibleWidth -= barWidth;
//                    }
//                }
//            }

            final double width;
            if (layoutConstraints.isFitWidth()) {
                width = Math.max(visibleWidth, minWidth);
            } else {
                width = minWidth;
            }

            final double height;
            if (layoutConstraints.isFitHeight()) {
                height = Math.max(visibleHeight, minHeight);
            } else {
                height = minHeight;
            }

            if (clear ||
                    outerSize == null ||
                    outerSize.getWidth() != width ||
                    outerSize.getHeight() != height ||
                    designSurfaceSize == null ||
                    designSurfaceSize.getWidth() != designWidth ||
                    designSurfaceSize.getHeight() != designHeight) {
                outerSize = new Size(width, height);

                if (designMode) {
                    designSurfaceSize = new Size(
                            Math.max(visibleWidth, designWidth),
                            Math.max(visibleHeight, designHeight));
                } else {
                    designSurfaceSize = outerSize;
                }

//                GWT.log("visibleWidth=" +
//                        visibleWidth +
//                        " designWidth=" +
//                        designWidth +
//                        " visibleHeight=" +
//                        visibleHeight +
//                        " designHeight=" +
//                        designHeight +
//                        " width=" +
//                        width +
//                        " height=" +
//                        height);

                designSurface.setSize(
                        designSurfaceSize.getWidth() + "px",
                        designSurfaceSize.getHeight() + "px");

                recalculateAll();

                if (clear) {
                    // Unbind tab layouts and clear.
                    for (final TabLayout tabLayout : layoutToWidgetMap.values()) {
                        tabLayout.unbind();
                    }
                    layoutToWidgetMap.clear();
                    splitToWidgetMap.clear();
                }

                // Move all widgets before they are attached to the design surface.
                layout();

                if (clear) {
                    // Add all widgets to the design surface.
                    for (final TabLayout w : layoutToWidgetMap.values()) {
                        designSurface.add(w);
                    }

                    // Add splitters to the design surface.
                    for (final Splitter w : splitToWidgetMap.values()) {
                        designSurface.add(w);
                    }
                }

                clear = false;
            }

            // Perform a resize on all child layouts.
            Scheduler.get().scheduleDeferred(this::resizeChildWidgets);
//            resizeChildWidgets();
        }
    }

    public void resizeChildWidgets() {
        Scheduler.get().scheduleDeferred(() -> {
            for (final TabLayout w : layoutToWidgetMap.values()) {
                w.onResize();
            }
        });
    }

    private void addCanvasSplit(final SplitInfo splitInfo,
                                final Size size,
                                final int dim) {
        // Add data to position a split during layout.
        final PositionAndSize positionAndSize =
                positionAndSizeMap.computeIfAbsent(splitInfo, k -> new PositionAndSize());

        positionAndSize.setPos(dim, size.get(dim) - SPLIT_OFFSET);
        positionAndSize.setSize(dim, SPLIT_SIZE);
        final int opposite = Dimension.opposite(dim);
        positionAndSize.setPos(opposite, 0);
        positionAndSize.setSize(opposite, size.get(opposite) + SPLIT_SIZE);
    }

    public void clear() {
        clear = true;
        // Clear the design surface.
        designSurface.clear();
    }

    private void recalculateSingleLayout(final LayoutConfig layoutConfig) {
        final PositionAndSize positionAndSize = positionAndSizeMap.get(layoutConfig);
        recalculate(layoutConfig, positionAndSize.getLeft(), positionAndSize.getTop(), positionAndSize.getWidth(),
                positionAndSize.getHeight());
    }

    private void recalculate(final LayoutConfig layoutConfig,
                             final double left,
                             final double top,
                             final double width,
                             final double height) {
        recalculateDimension(layoutConfig, Dimension.X, left, width, true);
        recalculateDimension(layoutConfig, Dimension.Y, top, height, true);
    }

    private double recalculateDimension(final LayoutConfig layoutConfig,
                                        final int dim,
                                        double pos,
                                        final double size,
                                        final boolean useRemaining) {
        double containerSize = 0;

        if (layoutConfig != null) {
//            Console.log(layoutConfig.toString());
//            Console.log("Preferred size: " +
//                    layoutConfig.getPreferredSize().get(dim) +
//                    " dimension " +
//                    (dim == 0
//                            ? "X"
//                            : "Y"));

            // Get the minimum size in this dimension that this splitter can be
            // to fit its contents.
            final double minSize = getMinRequired(layoutConfig, dim);
            if (useRemaining) {
                containerSize = size;
                containerSize = Math.max(containerSize, minSize);
            } else {
                final double preferredSize = layoutConfig.getPreferredSize().get(dim);
                containerSize = Math.min(preferredSize, size);
                containerSize = Math.max(containerSize, minSize);
            }

            PositionAndSize positionAndSize =
                    positionAndSizeMap.computeIfAbsent(layoutConfig, k -> new PositionAndSize());
            positionAndSize.setPos(dim, pos);
            positionAndSize.setSize(dim, containerSize);

            // Now deal with children if this is split layout data.
            if (layoutConfig instanceof SplitLayoutConfig) {
                final SplitLayoutConfig splitLayoutConfig = (SplitLayoutConfig) layoutConfig;
                double remainingSize = containerSize;

                for (int i = 0; i < splitLayoutConfig.count(); i++) {
                    final LayoutConfig child = splitLayoutConfig.get(i);

                    // See if this is the last child.
                    if (i < splitLayoutConfig.count() - 1) {
                        // Find out what the minimum space is that could be
                        // taken up by layouts after this one.
                        double minRequired = 0;
                        if (splitLayoutConfig.getDimension() == dim) {
                            minRequired = getMinRequired(splitLayoutConfig, dim, i + 1, 1);
                        }
                        final double available = remainingSize - minRequired;

                        final boolean useRest = splitLayoutConfig.getDimension() != dim;
                        final double childSize = recalculateDimension(child, dim, pos, available, useRest);
                        if (splitLayoutConfig.getDimension() == dim) {
                            // Move position for next child and reduce available
                            // size.
                            pos += childSize;
                            remainingSize -= childSize;
                        }

                        // Add data to position a split during layout.
                        final SplitInfo splitInfo = new SplitInfo(splitLayoutConfig, i);
                        positionAndSize = positionAndSizeMap.computeIfAbsent(splitInfo, k -> new PositionAndSize());

                        if (dim == splitLayoutConfig.getDimension()) {
                            positionAndSize.setPos(dim, pos - SPLIT_OFFSET);
                            positionAndSize.setSize(dim, SPLIT_SIZE);
                        } else {
                            positionAndSize.setPos(dim, pos);
                            positionAndSize.setSize(dim, containerSize + SPLIT_SIZE);
                        }

                    } else {
                        // This is the last child so assume rest of available
                        // space.
                        recalculateDimension(child, dim, pos, remainingSize, true);
                    }
                }
            }
        }

        return containerSize;
    }

    private double getMaxRequired(final LayoutConfig layoutConfig,
                                  final int dim) {
        return getRequiredSize(layoutConfig, dim, 0, 1, false);
    }

    private double getMinRequired(final LayoutConfig layoutConfig,
                                  final int dim) {
        return getRequiredSize(layoutConfig, dim, 0, 1, true);
    }

    private double getMinRequired(final LayoutConfig layoutConfig,
                                  final int dim,
                                  final int index,
                                  final int step) {
        return getRequiredSize(layoutConfig, dim, index, step, true);
    }

    private double getRequiredSize(final LayoutConfig layoutConfig,
                                   final int dim,
                                   final int index,
                                   final int step,
                                   final boolean min) {
        double totalSize = 0;
        if (layoutConfig instanceof TabLayoutConfig) {
            if (min) {
                totalSize = MIN_COMPONENT_WIDTH;
            } else {
                final TabLayoutConfig tabLayoutConfig = (TabLayoutConfig) layoutConfig;
                totalSize = tabLayoutConfig.getPreferredSize().get(dim);
                totalSize = Math.max(MIN_COMPONENT_WIDTH, totalSize);
            }

        } else if (layoutConfig instanceof SplitLayoutConfig) {
            final SplitLayoutConfig splitLayoutConfig = (SplitLayoutConfig) layoutConfig;
            for (int i = index; i >= 0 && i < splitLayoutConfig.count(); i += step) {
                final LayoutConfig child = splitLayoutConfig.get(i);
                final double childSize = getRequiredSize(child, dim, 0, 1, min);

                if (splitLayoutConfig.getDimension() == dim) {
                    // It this split layout is in the same direction as the
                    // dimension we are measuring then we need to sum the sizes.
                    totalSize += childSize;
                } else if (totalSize < childSize) {
                    // If this split layout is in a different direction to the
                    // dimension we are measuring then just remember the largest
                    // size.
                    totalSize = childSize;
                }
            }
        }

        return totalSize;
    }

    /**
     * Layout widgets using the positions and sizes stored in the map. Create
     * new tab layout and split widgets if they have not been created already.
     */
    private void layout() {
        for (final Entry<Object, PositionAndSize> entry : positionAndSizeMap.entrySet()) {
            final Object key = entry.getKey();
            if (key instanceof TabLayoutConfig) {
                final TabLayoutConfig tabLayoutConfig = (TabLayoutConfig) key;
                TabLayout tabLayout = layoutToWidgetMap.get(tabLayoutConfig);
                if (tabLayout == null) {
                    tabLayout = new TabLayout(eventBus, this, tabManager, tabLayoutConfig, changeHandler);
                    if (tabLayoutConfig.getAllTabCount() > 0) {
                        for (final TabConfig tabConfig : tabLayoutConfig.getTabs()) {
                            if (tabConfig.visible()) {
                                final Component component = components.get(tabConfig.getId());
                                if (component != null) {
                                    tabLayout.addTab(tabConfig, component);
                                }
                            }
                        }

                        // Ensure the tab layout data has a valid tab selection.
                        Integer selectedTab = tabLayoutConfig.getSelected();
                        if (tabLayout.getTabBar().getTabs() == null ||
                                tabLayout.getTabBar().getTabs().size() == 0) {
                            selectedTab = null;
                        } else if (selectedTab == null || selectedTab < 0
                                || selectedTab >= tabLayout.getTabBar().getTabs().size()) {
                            selectedTab = 0;
                        }

                        // Set the selected tab.
                        if (selectedTab != null) {
                            tabLayout.selectTab(selectedTab);
                        }
                        tabLayoutConfig.setSelected(selectedTab);
                    }

                    layoutToWidgetMap.put(tabLayoutConfig, tabLayout);
                }
                setPositionAndSize(tabLayout.getElement(), entry.getValue());
                tabLayout.onResize();

            } else if (key instanceof SplitInfo) {
                final SplitInfo splitInfo = (SplitInfo) key;
                final Splitter splitter = splitToWidgetMap.computeIfAbsent(splitInfo, Splitter::new);
                setPositionAndSize(splitter.getElement(), entry.getValue());
            }
        }
    }

    /**
     * Convenience method to transfer position and size data to an element.
     */
    private void setPositionAndSize(final Element element, final PositionAndSize positionAndSize) {
        element.getStyle().setPosition(Position.ABSOLUTE);
        element.getStyle().setLeft(positionAndSize.getLeft(), Unit.PX);
        element.getStyle().setTop(positionAndSize.getTop(), Unit.PX);
        element.getStyle().setWidth(positionAndSize.getWidth(), Unit.PX);
        element.getStyle().setHeight(positionAndSize.getHeight(), Unit.PX);
    }

    private double getAbsolutePos(final int dimension, final Element element) {
        if (dimension == Dimension.X) {
            return ElementUtil.getClientLeft(element);
        }

        return ElementUtil.getClientTop(element);
    }

    private double getEventPos(final int dimension, final double x, final double y) {
        if (dimension == Dimension.X) {
            return x;
        }

        return y;
    }

    public void maximiseTabs(final TabConfig selectedTab) {
        if (!maximised) {
            maximised = true;

            final TabLayoutConfig tabLayoutConfig = new TabLayoutConfig();
            components.getComponents().stream()
                    .sorted(Comparator.comparing((Component c) -> c.getComponentConfig().getName())
                            .thenComparing(c -> c.getComponentConfig().getId()))
                    .map(Component::getTabConfig)
                    .filter(TabConfig::visible)
                    .forEach(tabLayoutConfig::add);

            final int selected = selectedTab == null ? 0 : tabLayoutConfig.getTabs().indexOf(selectedTab);
            tabLayoutConfig.setSelected(selected);

            layoutConfigContainer.set(tabLayoutConfig);

            clear();
            refresh();
        }
    }

    public void restoreTabs() {
        if (maximised) {
            maximised = false;

            layoutConfigContainer.restore();

            clear();
            refresh();
        }
    }

    public boolean isMaximised() {
        return maximised;
    }

    public void closeTab(final TabConfig tabConfig) {
        final TabLayoutConfig tabLayoutConfig = tabConfig.getParent();
        tabLayoutConfig.remove(tabConfig);

        // Cascade removal if necessary.
        cascadeRemoval(tabLayoutConfig);

        // Clear and refresh the layout.
        clear();
        refresh();

        changeHandler.onDirty();
    }

    public void setChangeHandler(final FlexLayoutChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
    }

    private enum Pos {
        TAB,
        AFTER_TAB,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
        CENTER
    }

    private static class LayoutConfigContainer {

        private final LinkedList<LayoutConfig> layoutConfigList = new LinkedList<>();
        private LayoutConfig currentLayoutConfig = null;

        public LayoutConfig get() {
            return currentLayoutConfig;
        }

        public void restore() {
            currentLayoutConfig = layoutConfigList.removeLast();
        }

        public void set(final LayoutConfig layoutConfig) {
            if (currentLayoutConfig != layoutConfig) {
                layoutConfigList.add(currentLayoutConfig);
                currentLayoutConfig = layoutConfig;
            }
        }
    }

    private static class MouseTarget {

        private final LayoutConfig layoutConfig;
        private final PositionAndSize positionAndSize;
        private final Pos pos;
        private final TabLayout tabLayout;
        private final TabLayoutConfig currentParent;
        private final List<TabData> tabs;
        private final int tabIndex;
        private final LinkTab tabWidget;

        MouseTarget(final LayoutConfig layoutConfig,
                    final PositionAndSize positionAndSize,
                    final Pos pos,
                    final TabLayout tabLayout,
                    final TabLayoutConfig currentParent,
                    final List<TabData> tabs,
                    final int tabIndex,
                    final LinkTab tabWidget) {
            this.layoutConfig = layoutConfig;
            this.positionAndSize = positionAndSize;
            this.pos = pos;
            this.tabLayout = tabLayout;
            this.currentParent = currentParent;
            this.tabs = tabs;
            this.tabIndex = tabIndex;
            this.tabWidget = tabWidget;
        }

        TabData getFirstTab() {
            if (tabs == null || tabs.size() == 0) {
                return null;
            }
            return tabs.get(0);
        }
    }
}

