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

package stroom.dashboard.client.flexlayout;

import stroom.dashboard.client.flexlayout.Splitter.SplitInfo;
import stroom.dashboard.client.main.Components;
import stroom.dashboard.client.main.DashboardPane;
import stroom.dashboard.shared.Dimension;
import stroom.dashboard.shared.LayoutConstraints;
import stroom.util.shared.NullSafe;
import stroom.widget.datagrid.client.Glass;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.tab.client.GlobalResizeObserver;
import stroom.widget.tab.client.LinkTab;
import stroom.widget.tab.client.LinkTabBar;
import stroom.widget.tab.client.TabData;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.Rect;
import stroom.widget.util.client.Size;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;

public class FlexLayout extends Composite {

    private static final double DRAG_ZONE = 20;
    private static final double OUTER_SPLIT_BOUNDARY = 5;
    private static Glass marker;
    private static Glass glass;
    private static final Binder BINDER = GWT.create(Binder.class);
    // The glass's mouse cursor, while a tab or splitter is dragged
    private static final String[] GLASS_MODES = {
            "flex-layout__glass--move",
            "flex-layout__glass--col-resize",
            "flex-layout__glass--row-resize"};
    @UiField
    FlowPanel designSurface;
    @UiField
    SimplePanel scrollPanel;
    @UiField
    SimplePanel layout;
    private final Map<Object, PositionAndSize> positionAndSizeMap = new HashMap<>();
    private final Map<SplitInfo, Splitter> splitToWidgetMap = new HashMap<>();
    private final Map<MutableLayoutConfig, TabLayout> layoutToWidgetMap = new HashMap<>();
    private final Element designSurfaceElement;
    private Components components;
    private final LayoutConfigContainer layoutConfigContainer = new LayoutConfigContainer();
    private boolean maximised;
    private LayoutConstraints layoutConstraints;
    private MutableSize preferredSize;
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
    private TabMenuOpener onShowTabMenu;
    private Consumer<ShowMenuEvent> onShowMenu = event -> {
    };

    private FlexLayoutChangeHandler changeHandler;

    private Size outerSize;
    private Size designSurfaceSize;
    private final SplitInfo outerAcrossSplit =
            new SplitInfo(new MutableSplitLayoutConfig(Dimension.X), -1);
    private final SplitInfo outerDownSplit =
            new SplitInfo(new MutableSplitLayoutConfig(Dimension.Y), -1);

    private boolean designMode;

    private final LayoutSizes layoutSizes = new LayoutSizes(positionAndSizeMap);
    private final FocusMemory focusMemory;

    private List<DashboardPane> newComponents;
    private DashboardPane addedComponent;
    private double[] placementStart;
    private final PlacementKeys placementKeys = new PlacementKeys();

    /// Makes the layout, with nothing in it.
    public FlexLayout() {
        if (glass == null) {
            glass = new Glass("flex-layout__glass");
        }
        if (marker == null) {
            marker = new Glass("flex-layout__marker");
        }

        initWidget(BINDER.createAndBindUi(this));
        scrollPanel.addDomHandler(event -> resizeChildWidgets(), ScrollEvent.getType());

        designSurfaceElement = designSurface.getElement();
        focusMemory = new FocusMemory(layoutToWidgetMap, splitToWidgetMap, designSurface);
        sinkEvents(Event.ONMOUSEMOVE | Event.ONMOUSEDOWN | Event.ONMOUSEUP | Event.ONKEYDOWN);
        // Focus doesn't bubble; focusin does
        sinkBitlessEvent("focusin");
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        GlobalResizeObserver.addListener(layout.getElement(), e -> onResize());
    }

    @Override
    protected void onUnload() {
        GlobalResizeObserver.removeListener(layout.getElement());
        super.onUnload();
    }

    /// @param onShowTabMenu Shows a tab's menu, e.g. when its pane's Tab Actions button is pressed.
    public void setOnShowTabMenu(final TabMenuOpener onShowTabMenu) {
        this.onShowTabMenu = onShowTabMenu;
    }

    /// @param onShowMenu Shows a menu a pane's tab bar asks for, e.g. the list of its hidden tabs.
    public void setOnShowMenu(final Consumer<ShowMenuEvent> onShowMenu) {
        this.onShowMenu = onShowMenu;
    }

    // Shows a tab's menu, below an element
    void showTabMenu(final Element target, final TabLayout tabLayout, final MutableTabConfig tabConfig) {
        if (onShowTabMenu != null) {
            onShowTabMenu.show(target, this, tabLayout, tabConfig);
        }
    }

    // Shows a menu a pane's tab bar asks for
    void showMenu(final ShowMenuEvent event) {
        onShowMenu.accept(event);
    }

    @Override
    public void onBrowserEvent(final Event event) {
        final int eventType = event.getTypeInt();
//        if (designMode) {
        if ("focusin".equals(event.getType())) {
            final Element target = event.getEventTarget().cast();
            focusMemory.remember(target, getTargetSplitter(target));
        } else if (Event.ONKEYDOWN == eventType) {
            final Splitter splitter = getTargetSplitter(event.getEventTarget().cast());
            if (splitter != null && !busy) {
                onSplitterKey(splitter, event);
            }
        } else if (Event.ONMOUSEMOVE == eventType) {
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
            if (mouseTarget.tabLayout != null && mouseTarget.tabWidget != null) {
                final MutableTabConfig tabConfig = mouseTarget.tabLayout.getTabLayoutConfig().get(mouseTarget.tabIndex);
                if (tabConfig != null) {
                    showTabMenu(
                            mouseTarget.tabWidget.getElement(),
                            mouseTarget.tabLayout,
                            tabConfig);
                }
            }
//            }
        }
    }

    private void finishSelection(final Event event) {
        finishSelectionAt(event.getClientX(), event.getClientY());
        event.preventDefault();
    }

    private void finishSelectionAt(final double x, final double y) {
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
                    final MutableLayoutConfig targetLayout = mouseTarget.layoutConfig;

                    final List<MutableTabConfig> tabGroup = new ArrayList<>();
                    for (final TabData tabData : selection.tabs) {
                        tabGroup.add(((DashboardPane) tabData).getTabConfig());
                    }

                    // Add all invisible tabs, so we can move them all together if this is the only tab.
                    final MutableTabLayoutConfig currentParent = selection.currentParent;
                    if (currentParent != null && currentParent.getVisibleTabCount() == 1) {
                        for (final MutableTabConfig tabConfig : currentParent.getTabs()) {
                            if (!tabConfig.isVisible()) {
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
                        changeHandler.onChange();
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
                                changeHandler.onChange();
                            }
                        } else {
                            final MutableTabConfig tabConfig = mouseTarget
                                    .tabLayout
                                    .getTabLayoutConfig()
                                    .get(mouseTarget.tabIndex);
                            if (tabConfig != null) {
                                showTabMenu(
                                        mouseTarget.tabWidget.getElement(),
                                        tabLayout,
                                        tabConfig);
                            }
                        }
                    }
                }
            }
        }

        endSelection();
    }

    // Ends a drag, a resize or a placement, whichever is under way.
    private void endSelection() {
        releaseCapture();

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
            final DashboardPane addedComponent,
            final List<DashboardPane> newComponents,
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

        // Keys can finish or cancel placing too: a keyboard user who chose Duplicate was stuck
        placementStart = new double[]{x, y};
        glass.getElement().setTitle(PlacementKeys.HELP);
        placementKeys.start(this::cancelPlacement, this::placeAtStart);

        final MouseTarget mouseTarget = getMouseTarget(x, y, true, false);
        highlightMouseTarget(mouseTarget);
    }

    // Enter places the new components where placing them started, beside the tab they came from,
    // or cancels if they can't go there
    private void placeAtStart() {
        if (newComponents != null) {
            finishSelectionAt(placementStart[0], placementStart[1]);
            if (newComponents != null) {
                cancelPlacement();
            }
        }
    }

    // Abandons placing the new components and has the dashboard remove them.
    private void cancelPlacement() {
        final List<DashboardPane> unplaced = newComponents;
        if (selection != null && selection.tabWidget != null) {
            selection.tabWidget.setHighlight(false);
        }
        addedComponent = null;
        newComponents = null;
        selection = null;
        endSelection();
        if (changeHandler != null && unplaced != null) {
            changeHandler.cancelNewComponents(unplaced);
        }
    }

    private boolean moveTab(final MouseTarget mouseTarget,
                            final List<MutableTabConfig> tabGroup,
                            final MutableLayoutConfig targetLayout,
                            final Pos targetPos) {
        boolean moved = false;

        if (targetLayout instanceof final MutableTabLayoutConfig targetTabLayoutConfig) {
            if (Pos.CENTER == targetPos || Pos.TAB == targetPos || Pos.AFTER_TAB == targetPos) {
                moved = moveTabOntoTab(mouseTarget, tabGroup, targetTabLayoutConfig, targetPos);

            } else {
                moved = moveTabOutside(mouseTarget, tabGroup, targetTabLayoutConfig, targetPos);
            }

        } else if (targetLayout instanceof final MutableSplitLayoutConfig targetSplitLayoutConfig) {
            moved = moveTabOntoSplit(mouseTarget, tabGroup, targetSplitLayoutConfig, targetPos);
        }

        return moved;
    }

    private boolean moveTabOntoTab(final MouseTarget mouseTarget,
                                   final List<MutableTabConfig> tabGroup,
                                   final MutableTabLayoutConfig targetTabLayoutConfig,
                                   final Pos targetPos) {
//        GWT.log("moveTabOntoTab");
        boolean moved = false;

        for (final MutableTabConfig tabConfig : tabGroup) {
            final MutableTabLayoutConfig currentParent = tabConfig.getParent();

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
                                   final List<MutableTabConfig> tabGroup,
                                   final MutableTabLayoutConfig targetTabLayoutConfig,
                                   final Pos targetPos) {
//        GWT.log("moveTabOutside");
        // Ensure we have a parent.
        MutableSplitLayoutConfig parent = targetTabLayoutConfig.getParent();
        if (parent == null) {
            int dim = Dimension.X;
            if (Pos.TOP == targetPos || Pos.BOTTOM == targetPos) {
                dim = Dimension.Y;
            }
            final MutableSplitLayoutConfig splitLayoutConfig =
                    new MutableSplitLayoutConfig(targetTabLayoutConfig.getPreferredSize().copy(), dim);
            splitLayoutConfig.add(targetTabLayoutConfig);

            final PositionAndSize positionAndSize = positionAndSizeMap.get(targetTabLayoutConfig);
            positionAndSizeMap.put(splitLayoutConfig, positionAndSize.copy());

            layoutConfigContainer.set(splitLayoutConfig);
            parent = splitLayoutConfig;
        }

        // Get the index of the target layout and
        // therefore the insert position.
        int insertPos = parent.indexOf(targetTabLayoutConfig);
        final MutableTabLayoutConfig newTabLayout = new MutableTabLayoutConfig();
        newTabLayout.setSelected(0);

        // Move all tabs from the tab group onto the new tab layout.
        for (final MutableTabConfig tabConfig : tabGroup) {
            final MutableTabLayoutConfig currentParent = tabConfig.getParent();

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
        boolean moved = false;
        if (NullSafe.hasItems(newTabLayout.getTabs())) {
            // Recalculate the sizes for the parent so that
            // the target layout is resized.
            layoutSizes.recalculate(parent);

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

                final MutableSize preferredSize = targetTabLayoutConfig.getPreferredSize().copy();
                final MutableSplitLayoutConfig newSplit = new MutableSplitLayoutConfig(preferredSize, dim);
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
                                     final List<MutableTabConfig> tabGroup,
                                     final MutableSplitLayoutConfig targetSplitLayoutConfig,
                                     final Pos targetPos) {
//        GWT.log("moveTabOntoSplit");
        boolean moved = false;

        for (final MutableTabConfig tabConfig : tabGroup) {
            final MutableTabLayoutConfig currentParent = tabConfig.getParent();
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
                final MutableTabLayoutConfig tabLayoutConfig = new MutableTabLayoutConfig();
                tabLayoutConfig.add(tabConfig);
                tabLayoutConfig.setSelected(0);

                // We can add the new tab layout to the target split if the dimension is the same. If it isn't then we
                // need to create a new split layout with the correct split dimension and insert it.
                if (targetSplitLayoutConfig.getDimension() != dim) {
                    // The target split layout is not the correct dimension so we will need to look at the parent
                    // layout.
                    final MutableSplitLayoutConfig parentSplitLayoutConfig = targetSplitLayoutConfig.getParent();
                    if (parentSplitLayoutConfig == null) {
                        // There is no parent of the target so we will create a new parent split to wrap the current
                        // target and insert the new tab layout in the correct location.
                        final MutableSplitLayoutConfig newSplitLayoutConfig =
                                new MutableSplitLayoutConfig(targetSplitLayoutConfig.getPreferredSize().copy(), dim);

                        // Divide the original size between the new children.
                        tabLayoutConfig.setPreferredSize(targetSplitLayoutConfig.getPreferredSize().copy());
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

                        final MutableSplitLayoutConfig newSplitLayoutConfig =
                                new MutableSplitLayoutConfig(targetSplitLayoutConfig.getPreferredSize().copy(), dim);

                        // Divide the original size between the new children.
                        tabLayoutConfig.setPreferredSize(targetSplitLayoutConfig.getPreferredSize().copy());
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
                        tabLayoutConfig.setPreferredSize(targetSplitLayoutConfig.getPreferredSize().copy());
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
                    tabLayoutConfig.setPreferredSize(targetSplitLayoutConfig.getPreferredSize().copy());
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

    private void divideSize(final MutableLayoutConfig originalLayout,
                            final MutableLayoutConfig layout1,
                            final MutableLayoutConfig layout2,
                            final int dim) {
        // Set the size of the target layout and the new
        // layout to be half the size of the original so
        // combined they take up the same space.
        final double oldLayoutSize = positionAndSizeMap.get(originalLayout).getSize(dim);
        double newLayoutSize = oldLayoutSize / 2D;
        newLayoutSize = Math.max(newLayoutSize, LayoutSizes.MIN_COMPONENT_WIDTH);

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
        if (placementKeys.isListening()) {
            placementKeys.stop();
            glass.getElement().setTitle("");
        }

        Event.releaseCapture(getElement());
    }

    private void cascadeRemoval(final MutableTabLayoutConfig tabLayoutConfig) {
        if (tabLayoutConfig != null && tabLayoutConfig.getAllTabCount() == 0) {
            MutableLayoutConfig child = tabLayoutConfig;
            MutableSplitLayoutConfig parent = child.getParent();

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
        final MutableSplitLayoutConfig layoutConfig = splitInfo.getLayoutConfig();
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
            min = containerPos + LayoutSizes.getMinRequired(layoutConfigContainer.get(), dim);
            max = containerPos + designSurfaceSize.get(dim);
        } else {
            final double parentMin = containerPos + positionAndSize.getPos(dim);
            final double parentMax = parentMin + positionAndSize.getSize(dim);
            min = parentMin + LayoutSizes.getMinRequired(layoutConfig, dim, splitInfo.getIndex(), -1);
            max = parentMax - LayoutSizes.getMinRequired(layoutConfig, dim, splitInfo.getIndex() + 1, 1);
        }
        min -= LayoutSizes.SPLIT_OFFSET;
        max -= LayoutSizes.SPLIT_OFFSET;
    }

    private void setDefaultCursor() {
        marker.getElement().removeClassName("flex-layout__marker--splitter");
        marker.getElement().removeClassName("flex-layout__marker--down");
        marker.getElement().removeClassName("flex-layout__marker--across");
        setGlassMode(null);
    }

    private void setMoveCursor() {
        marker.getElement().removeClassName("flex-layout__marker--splitter");
        marker.getElement().removeClassName("flex-layout__marker--down");
        marker.getElement().removeClassName("flex-layout__marker--across");
        setGlassMode("flex-layout__glass--move");
    }

    private void setMarkerCursor(final int dim) {
        marker.getElement().addClassName("flex-layout__marker--splitter");

        if (Dimension.X == dim) {
            setGlassMode("flex-layout__glass--col-resize");
            marker.getElement().addClassName("flex-layout__marker--across");
        } else if (Dimension.Y == dim) {
            setGlassMode("flex-layout__glass--row-resize");
            marker.getElement().addClassName("flex-layout__marker--down");
        }
    }

    // Gives the glass the mouse cursor of what is dragged, or the default one if null
    private void setGlassMode(final String mode) {
        for (final String glassMode : GLASS_MODES) {
            glass.getElement().removeClassName(glassMode);
        }
        if (mode != null) {
            glass.getElement().addClassName(mode);
        }
    }

    private void stopSplitResize(final double x, final double y) {
        final SplitInfo splitInfo = selectedSplitter.getSplitInfo();
        final int dim = splitInfo.getLayoutConfig().getDimension();
        moveSplitBy(splitInfo, getEventPos(dim, x, y) - startPos[dim]);
    }

    // Moves a splitter by the change given (right or down when positive), as releasing it there
    // after a drag does, resizing what is either side of it
    private void moveSplitBy(final SplitInfo splitInfo, final double initialChange) {
        final MutableSplitLayoutConfig layoutConfig = splitInfo.getLayoutConfig();
        final int dim = layoutConfig.getDimension();

        // Don't do anything if there is no change.
        if (initialChange != 0) {
            double totalChange = -Math.abs(initialChange);

            // If this is canvas resize split then treat it differently.
            if (splitInfo.getIndex() == -1) {
                min = LayoutSizes.getMinRequired(layoutConfig, dim);
                max = designSurfaceSize.get(dim);
                final double val = constrain(preferredSize.get(dim) + initialChange, min, max);
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
                layoutSizes.recalculate(layoutConfig);
                // Set the new position and size of all widgets.
                layout();
            }

            // Let the handler know the layout is dirty.
            changeHandler.onChange();
        }
    }

    private double resizeChildren(final MutableSplitLayoutConfig layoutConfig,
                                  final double change) {
        return resizeChildren(layoutConfig, change, 1, 0);
    }

    private double resizeChildren(final MutableSplitLayoutConfig layoutConfig,
                                  final double change,
                                  final int step,
                                  final int splitIndex) {
        final int dim = layoutConfig.getDimension();
        double totalChange = change;

        double realChange = 0;
        for (int i = splitIndex; i >= 0 && i < layoutConfig.count() && totalChange != 0; i += step) {
            final MutableLayoutConfig child = layoutConfig.get(i);
            if (child != null) {
                final double currentSize = positionAndSizeMap.get(child).getSize(dim);
                final double minSize = LayoutSizes.getMinRequired(child, dim);

                double newSize = currentSize + totalChange;
                newSize = Math.max(newSize, minSize);
                child.getPreferredSize().set(dim, (int) newSize);

                final double diff = newSize - currentSize;
                totalChange = totalChange - diff;
                realChange += diff;
            }
        }
        totalChange = -realChange;
        return totalChange;
    }

    // Moves a splitter with the keyboard, each key press as one drag and release
    private void onSplitterKey(final Splitter splitter, final Event event) {
        final SplitInfo splitInfo = splitter.getSplitInfo();
        final Double change = splitter.getKeyChange(event, getSplitPos(splitInfo), getSplitLimits(splitInfo));
        if (change != null) {
            event.preventDefault();
            moveSplitBy(splitInfo, change);
        }
    }

    // Where a splitter is: for a splitter between children, the end of the child before it, within
    // the layout; for a splitter that resizes the whole layout, the layout's preferred size
    private double getSplitPos(final SplitInfo splitInfo) {
        final int dim = splitInfo.getLayoutConfig().getDimension();
        if (splitInfo.getIndex() == -1) {
            return preferredSize.get(dim);
        }
        final PositionAndSize before = positionAndSizeMap.get(
                splitInfo.getLayoutConfig().get(splitInfo.getIndex()));
        return before.getPos(dim) + before.getSize(dim);
    }

    // The least and the most a splitter can move to, as getSplitPos measures it, so that what is
    // either side of it keeps its minimum size (as startSplitResize limits a drag)
    private double[] getSplitLimits(final SplitInfo splitInfo) {
        final MutableSplitLayoutConfig layoutConfig = splitInfo.getLayoutConfig();
        final int dim = layoutConfig.getDimension();
        if (splitInfo.getIndex() == -1) {
            return new double[]{
                    LayoutSizes.getMinRequired(layoutConfigContainer.get(), dim),
                    designSurfaceSize.get(dim)};
        }
        final PositionAndSize parent = positionAndSizeMap.get(layoutConfig);
        final double parentMin = parent.getPos(dim);
        final double parentMax = parentMin + parent.getSize(dim);
        return new double[]{
                parentMin + LayoutSizes.getMinRequired(layoutConfig, dim, splitInfo.getIndex(), -1),
                parentMax - LayoutSizes.getMinRequired(layoutConfig, dim, splitInfo.getIndex() + 1, 1)};
    }

    // Names a splitter, and sets its value: where it is between its limits, as a percentage of the
    // split it divides (or of the space the whole layout can take)
    private void updateSplitter(final Splitter splitter) {
        final SplitInfo splitInfo = splitter.getSplitInfo();
        final int dim = splitInfo.getLayoutConfig().getDimension();
        final PositionAndSize parent = splitInfo.getIndex() == -1
                ? null
                : positionAndSizeMap.get(splitInfo.getLayoutConfig());
        splitter.setValue(getSplitPos(splitInfo),
                parent != null
                        ? parent.getPos(dim)
                        : 0,
                parent != null
                        ? parent.getSize(dim)
                        : designSurfaceSize.get(dim),
                getSplitLimits(splitInfo));
        splitter.refreshName();
    }

    // Adds the panels and splitters of a layout to the design surface in the order Tab should visit
    // them: depth first, with each splitter after all that it has before it (so after the last
    // panel it resizes, and before the first panel after it), as they are on screen
    private void addInLayoutOrder(final MutableLayoutConfig layoutConfig) {
        if (layoutConfig instanceof final MutableSplitLayoutConfig split) {
            for (int i = 0; i < split.count(); i++) {
                addInLayoutOrder(split.get(i));
                if (i < split.count() - 1) {
                    final Splitter splitter = splitToWidgetMap.get(new SplitInfo(split, i));
                    if (splitter != null) {
                        designSurface.add(splitter);
                    }
                }
            }
        } else if (layoutConfig != null) {
            final TabLayout tabLayout = layoutToWidgetMap.get(layoutConfig);
            if (tabLayout != null) {
                designSurface.add(tabLayout);
            }
        }
    }

    private void moveSplit(final double x, final double y) {
        final MutableSplitLayoutConfig layoutConfig = selectedSplitter.getSplitInfo().getLayoutConfig();
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
                if (entry.getKey() instanceof final MutableSplitLayoutConfig layoutConfig) {
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
            if (entry.getKey() instanceof final MutableTabLayoutConfig layoutConfig) {
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
                                         final MutableLayoutConfig layoutConfig,
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
                        (MutableTabLayoutConfig) layoutConfig,
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
                                      final MutableTabLayoutConfig layoutConfig,
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

                    final List<MutableTabConfig> tabConfigList = layoutConfig.getTabs();
                    int visibleTabIndex = 0;
                    for (int i = 0; i < tabConfigList.size(); i++) {
                        final MutableTabConfig tabConfig = tabConfigList.get(i);
                        if (tabConfig.isVisible()) {
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
                    marker.getElement().addClassName("flex-layout__marker--tab");
                    break;
                case AFTER_TAB:
                    showMarker(ElementUtil.getClientLeft(tabElement) +
                               ElementUtil.getSubPixelOffsetWidth(tabElement) +
                               4,
                            ElementUtil.getClientTop(tabElement),
                            5,
                            tabElement.getOffsetHeight());
                    marker.getElement().addClassName("flex-layout__marker--tab");
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

    public MutableLayoutConfig getLayoutConfig() {
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

    public void configure(final MutableLayoutConfig layoutConfig,
                          final LayoutConstraints layoutConstraints,
                          final MutableSize preferredSize) {
        layoutConfigContainer.set(layoutConfig);
        this.preferredSize = preferredSize;
        setLayoutConstraints(layoutConstraints);
    }

    private void showMarker(final double left, final double top, final double width, final double height) {
        final Rect outer = ElementUtil.getClientRect(scrollPanel.getElement());
        final Rect inner = new Rect(top, top + height, left, left + width);
        final Rect min = Rect.min(outer, inner);
        marker.getElement().removeClassName("flex-layout__marker--tab");
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
        layoutSizes.recalculate(layoutConfigContainer.get(), 0, 0, outerSize.getWidth(), outerSize.getHeight());
    }

    public void refresh() {
        Scheduler.get().scheduleDeferred(this::doRefresh);
    }

    public MutableSize getVisibleSize() {
        final double visibleWidth = Math.floor(ElementUtil.getSubPixelOffsetWidth(getElement()));
        final double visibleHeight = Math.floor(ElementUtil.getSubPixelOffsetHeight(getElement()));
        return new MutableSize((int) visibleWidth, (int) visibleHeight);
    }

    public void doRefresh() {
        if (layoutConfigContainer.get() != null) {
            double minWidth = LayoutSizes.getMinRequired(layoutConfigContainer.get(), Dimension.X);
            double minHeight = LayoutSizes.getMinRequired(layoutConfigContainer.get(), Dimension.Y);

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
                    minWidth = LayoutSizes.getMaxRequired(layoutConfigContainer.get(), Dimension.X);
                }
                preferredSize.setWidth((int) minWidth);
            }
            if (!layoutConstraints.isFitHeight()) {
                if (preferredSize.getHeight() > 0) {
                    minHeight = Math.max(preferredSize.getHeight(), minHeight);
                } else {
                    minHeight = LayoutSizes.getMaxRequired(layoutConfigContainer.get(), Dimension.Y);
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
                    // Add the panels and their splitters to the design surface in the order Tab
                    // should visit them, then the splitters that resize the whole layout
                    addInLayoutOrder(layoutConfigContainer.get());
                    for (final SplitInfo outerSplit : List.of(outerAcrossSplit, outerDownSplit)) {
                        final Splitter splitter = splitToWidgetMap.get(outerSplit);
                        if (splitter != null) {
                            designSurface.add(splitter);
                        }
                    }
                    // Anything the layout doesn't reach (there should be nothing), after the rest
                    for (final TabLayout w : layoutToWidgetMap.values()) {
                        if (w.getParent() != designSurface) {
                            designSurface.add(w);
                        }
                    }
                    for (final Splitter w : splitToWidgetMap.values()) {
                        if (w.getParent() != designSurface) {
                            designSurface.add(w);
                        }
                    }
                    focusMemory.restoreLater();
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

        positionAndSize.setPos(dim, size.get(dim) - LayoutSizes.SPLIT_OFFSET);
        positionAndSize.setSize(dim, LayoutSizes.SPLIT_SIZE);
        final int opposite = Dimension.opposite(dim);
        positionAndSize.setPos(opposite, 0);
        positionAndSize.setSize(opposite, size.get(opposite) + LayoutSizes.SPLIT_SIZE);
    }

    public void clear() {
        clear = true;
        // Clear the design surface.
        designSurface.clear();
    }

    /**
     * Layout widgets using the positions and sizes stored in the map. Create
     * new tab layout and split widgets if they have not been created already.
     */
    private void layout() {
        for (final Entry<Object, PositionAndSize> entry : positionAndSizeMap.entrySet()) {
            final Object key = entry.getKey();
            if (key instanceof final MutableTabLayoutConfig tabLayoutConfig) {
                TabLayout tabLayout = layoutToWidgetMap.get(tabLayoutConfig);
                if (tabLayout == null) {
                    tabLayout = new TabLayout(this, tabLayoutConfig, changeHandler);
                    if (tabLayoutConfig.getAllTabCount() > 0) {
                        for (final MutableTabConfig tabConfig : tabLayoutConfig.getTabs()) {
                            if (tabConfig.isVisible()) {
                                final DashboardPane component = components.get(tabConfig.getId());
                                if (component != null) {
                                    tabLayout.addTab(tabConfig, component);
                                }
                            }
                        }

                        // Ensure the tab layout data has a valid tab selection.
                        Integer selectedTab = tabLayoutConfig.getSelected();
                        if (NullSafe.isEmptyCollection(tabLayout.getTabBar().getTabs())) {
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

            } else if (key instanceof final SplitInfo splitInfo) {
                final Splitter splitter = splitToWidgetMap.computeIfAbsent(splitInfo,
                        info -> new Splitter(info, layoutToWidgetMap::get));
                setPositionAndSize(splitter.getElement(), entry.getValue());
            }
        }

        // Name the splitters, and give them their values, once every panel is in place
        for (final Entry<SplitInfo, Splitter> entry : splitToWidgetMap.entrySet()) {
            if (positionAndSizeMap.containsKey(entry.getKey())) {
                updateSplitter(entry.getValue());
            }
        }
    }

    /**
     * Convenience method to transfer position and size data to an element.
     */
    private void setPositionAndSize(final Element element, final PositionAndSize positionAndSize) {
        element.addClassName("flex-layout__item");
        CssProperties.set(element, "--flex-layout__left", positionAndSize.getLeft() + "px");
        CssProperties.set(element, "--flex-layout__top", positionAndSize.getTop() + "px");
        CssProperties.set(element, "--flex-layout__width", positionAndSize.getWidth() + "px");
        CssProperties.set(element, "--flex-layout__height", positionAndSize.getHeight() + "px");
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

    public void maximiseTabs(final MutableTabConfig selectedTab) {
        if (!maximised) {
            maximised = true;

            final MutableTabLayoutConfig tabLayoutConfig = new MutableTabLayoutConfig();
            components.getComponents().stream()
                    .sorted(Comparator.comparing((DashboardPane c) -> c.getComponentConfig().getName())
                            .thenComparing(c -> c.getComponentConfig().getId()))
                    .map(DashboardPane::getTabConfig)
                    .filter(Objects::nonNull)
                    .filter(MutableTabConfig::isVisible)
                    .forEach(tabLayoutConfig::add);

            final int selected = selectedTab == null
                    ? 0
                    : tabLayoutConfig.getTabs().indexOf(selectedTab);
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

    public void closeTab(final MutableTabConfig tabConfig) {
        final MutableTabLayoutConfig tabLayoutConfig = tabConfig.getParent();
        tabLayoutConfig.remove(tabConfig);

        // Cascade removal if necessary.
        cascadeRemoval(tabLayoutConfig);

        // Clear and refresh the layout.
        clear();
        refresh();

        changeHandler.onChange();
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

        private final LinkedList<MutableLayoutConfig> layoutConfigList = new LinkedList<>();
        private MutableLayoutConfig currentLayoutConfig = null;

        public MutableLayoutConfig get() {
            return currentLayoutConfig;
        }

        public void restore() {
            currentLayoutConfig = layoutConfigList.removeLast();
        }

        public void set(final MutableLayoutConfig layoutConfig) {
            if (currentLayoutConfig != layoutConfig) {
                layoutConfigList.add(currentLayoutConfig);
                currentLayoutConfig = layoutConfig;
            }
        }
    }

    private static class MouseTarget {

        private final MutableLayoutConfig layoutConfig;
        private final PositionAndSize positionAndSize;
        private final Pos pos;
        private final TabLayout tabLayout;
        private final MutableTabLayoutConfig currentParent;
        private final List<TabData> tabs;
        private final int tabIndex;
        private final LinkTab tabWidget;

        MouseTarget(final MutableLayoutConfig layoutConfig,
                    final PositionAndSize positionAndSize,
                    final Pos pos,
                    final TabLayout tabLayout,
                    final MutableTabLayoutConfig currentParent,
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
            if (NullSafe.isEmptyCollection(tabs)) {
                return null;
            }
            return tabs.get(0);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FlexLayout> {

    }


    // --------------------------------------------------------------------------------


    /// Shows a tab's menu.
    @FunctionalInterface
    public interface TabMenuOpener {

        /// Shows a tab's menu below an element.
        ///
        /// @param target     The element, e.g. the tab or its pane's Tab Actions button.
        /// @param flexLayout The layout the tab is in.
        /// @param tabLayout  The pane the tab is in.
        /// @param tabConfig  The tab.
        void show(Element target, FlexLayout flexLayout, TabLayout tabLayout, MutableTabConfig tabConfig);
    }
}
