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

import stroom.dashboard.shared.Dimension;
import stroom.util.shared.NullSafe;
import stroom.widget.tab.client.TabData;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.function.Function;

/// A splitter of a [FlexLayout]: a line between two panels (or groups of panels) that resizes
/// them, by dragging it or, as a focusable `role="separator"`, with the keyboard (see
/// [#getKeyChange]).
///
/// Its accessible name names what it resizes (e.g. "Resize Query and Table"), which depends on the
/// panels' selected tabs, so it is named again whenever it takes the focus.
public class Splitter extends Widget {

    // How far an arrow key moves a splitter, and with Shift (as SplitPane's KEY_STEP_PX and
    // BIG_KEY_STEP_PX)
    private static final double KEY_STEP = 10;
    private static final double BIG_KEY_STEP = 50;
    private static final Binder BINDER = GWT.create(Binder.class);

    private final SplitInfo splitInfo;
    private final Function<MutableLayoutConfig, TabLayout> panels;

    /// @param splitInfo The split and the gap between its children that the splitter resizes.
    /// @param panels    Gives the panel showing a tab layout, if there is one.
    public Splitter(final SplitInfo splitInfo, final Function<MutableLayoutConfig, TabLayout> panels) {
        this.splitInfo = splitInfo;
        this.panels = panels;

        final Element element = BINDER.createAndBindUi(this);

        // A split across lays its children out side by side, so its splitter is a vertical line
        // (flex-layout.css draws it by its aria-orientation)
        element.setAttribute("aria-orientation", splitInfo.layoutConfig.getDimension() == Dimension.X
                ? "vertical"
                : "horizontal");

        setElement(element);
        sinkEvents(Event.ONFOCUS);
    }

    @Override
    public void onBrowserEvent(final Event event) {
        if (Event.ONFOCUS == event.getTypeInt()) {
            refreshName();
        }
        super.onBrowserEvent(event);
    }

    /// Names the splitter again, from what it resizes now: the selected tabs of the panels either
    /// side of it (the last panel before it and the first after it), e.g. "Resize Query and Table";
    /// or, for a splitter that resizes the whole layout, the layout's width or height.
    public void refreshName() {
        final MutableSplitLayoutConfig layoutConfig = splitInfo.getLayoutConfig();
        final String name;
        if (splitInfo.getIndex() == -1) {
            name = Dimension.X == layoutConfig.getDimension()
                    ? "Resize the dashboard's width"
                    : "Resize the dashboard's height";
        } else {
            final TabLayout after = getEdgePanel(layoutConfig.get(splitInfo.getIndex() + 1), false);
            name = "Resize " + getPanelName(getPanelBefore()) + " and " + getPanelName(after);
        }
        getElement().setAttribute("aria-label", name);
    }

    /// @return The last panel before the splitter, or null if there is none (as for a splitter
    /// that resizes the whole layout).
    public TabLayout getPanelBefore() {
        return splitInfo.getIndex() >= 0
                ? getEdgePanel(splitInfo.getLayoutConfig().get(splitInfo.getIndex()), true)
                : null;
    }

    /// How far a key moves the splitter, each key press as one drag and release: an arrow key (the
    /// left and right arrows for a splitter between panels side by side, the up and down arrows for
    /// one between stacked panels) moves it [#KEY_STEP], or [#BIG_KEY_STEP] with Shift, and Home and
    /// End move it as far as it can go.
    ///
    /// @param event  The key down event.
    /// @param pos    Where the splitter is.
    /// @param limits The least and the most it can move to.
    /// @return How far to move the splitter (right or down when positive), or null if the key
    /// doesn't move it.
    public Double getKeyChange(final Event event, final double pos, final double[] limits) {
        final int dim = splitInfo.getLayoutConfig().getDimension();
        final int keyCode = event.getKeyCode();
        final double step = event.getShiftKey()
                ? BIG_KEY_STEP
                : KEY_STEP;
        final int back = Dimension.X == dim
                ? KeyCodes.KEY_LEFT
                : KeyCodes.KEY_UP;
        final int forward = Dimension.X == dim
                ? KeyCodes.KEY_RIGHT
                : KeyCodes.KEY_DOWN;

        if (keyCode == back) {
            return -step;
        } else if (keyCode == forward) {
            return step;
        } else if (keyCode == KeyCodes.KEY_HOME) {
            return limits[0] - pos;
        } else if (keyCode == KeyCodes.KEY_END) {
            return limits[1] - pos;
        }
        return null;
    }

    /// Sets the splitter's value: where it is between the least and the most it can move to, as
    /// percentages of the split it divides (or, for a splitter that resizes the whole layout, of the
    /// space the layout can take).
    ///
    /// @param pos    Where the splitter is.
    /// @param start  Where the split (or the space) starts.
    /// @param size   How big the split (or the space) is; when it is nothing, no value is set.
    /// @param limits The least and the most the splitter can move to.
    public void setValue(final double pos, final double start, final double size, final double[] limits) {
        if (size <= 0) {
            return;
        }
        final int now = percent(pos - start, size);
        final String dimension = Dimension.X == splitInfo.getLayoutConfig().getDimension()
                ? "width"
                : "height";
        final Element element = getElement();
        element.setAttribute("aria-valuenow", String.valueOf(now));
        element.setAttribute("aria-valuemin", String.valueOf(percent(limits[0] - start, size)));
        element.setAttribute("aria-valuemax", String.valueOf(percent(limits[1] - start, size)));
        element.setAttribute("aria-valuetext", now + "% of the " + dimension);
    }

    /// @return The split and the gap between its children that the splitter resizes.
    public SplitInfo getSplitInfo() {
        return splitInfo;
    }

    // The panel last in a layout (the last panel of its last child, and so on), or first
    private TabLayout getEdgePanel(final MutableLayoutConfig layoutConfig, final boolean last) {
        MutableLayoutConfig config = layoutConfig;
        while (config instanceof final MutableSplitLayoutConfig split && split.count() > 0) {
            config = split.get(last
                    ? split.count() - 1
                    : 0);
        }
        return panels.apply(config);
    }

    // The name of a panel's selected tab, or "panel"
    private static String getPanelName(final TabLayout tabLayout) {
        final TabData selected = tabLayout != null
                ? tabLayout.getTabBar().getSelectedTab()
                : null;
        return selected != null && NullSafe.isNonBlankString(selected.getLabel())
                ? selected.getLabel()
                : "panel";
    }

    private static int percent(final double part, final double whole) {
        return (int) Math.round(Math.max(0, Math.min(100, 100 * part / whole)));
    }

    public static class SplitInfo {

        private final MutableSplitLayoutConfig layoutConfig;
        private final int index;

        public SplitInfo(final MutableSplitLayoutConfig layoutConfig, final int index) {
            this.layoutConfig = layoutConfig;
            this.index = index;
        }

        public MutableSplitLayoutConfig getLayoutConfig() {
            return layoutConfig;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final SplitInfo splitInfo = (SplitInfo) o;
            return index == splitInfo.index && Objects.equals(layoutConfig, splitInfo.layoutConfig);
        }

        @Override
        public int hashCode() {
            return Objects.hash(layoutConfig, index);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<DivElement, Splitter> {

    }
}
