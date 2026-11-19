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

package stroom.widget.tab.client;

import stroom.widget.tab.client.event.HasShowTabMenuHandlers;
import stroom.widget.tab.client.event.ShowTabMenuEvent;
import stroom.widget.tab.client.event.ShowTabMenuEvent.Handler;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.GlobalResizeObserver;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiTemplate;
import com.google.gwt.user.client.DOM;

import java.util.List;

/// The document tabs' bar, whose tabs can be dragged to reorder them. Its markup is
/// `CurveTabBar.ui.xml`.
///
/// When its tabs don't all fit, their labels are cut, the widest first, down to the next widest,
/// then all alike ([TabLabelCap]), but no narrower than about 10 characters (`tab-bar.css`); the
/// tabs that still don't fit overflow into its selector.
public class CurveTabBar extends DraggableTabBar implements HasShowTabMenuHandlers {

    private static final Binder BINDER = GWT.create(Binder.class);
    // The widest a tab's label may be, set on the bar while its tabs don't all fit (tab-bar.css)
    private static final String LABEL_CAP = "--tab-bar__label-cap";

    // Where screen readers are told that the selected tab is loading and has loaded
    private final Element loadStatus;
    // What a loading tab is described by
    private final Element loadDescription;

    public CurveTabBar() {
        this(new Parts());
    }

    private CurveTabBar(final Parts parts) {
        super(BINDER.createAndBindUi(parts), parts.tabList);
        loadStatus = parts.loadStatus;
        loadDescription = parts.loadDescription;
        loadDescription.setId(DOM.createUniqueId());
    }

    @Override
    protected Element getLoadStatus() {
        return loadStatus;
    }

    @Override
    protected Element getLoadDescription() {
        return loadDescription;
    }

    @Override
    protected void fitTabs(final List<AbstractTab> tabs, final int available) {
        // Measured uncut
        CssProperties.remove(getElement(), LABEL_CAP);
        final double[] labelWidths = new double[tabs.size()];
        int rest = 0;
        for (int i = 0; i < tabs.size(); i++) {
            final AbstractTab tab = tabs.get(i);
            final int tabWidth = tab.getOffsetWidth();
            if (tab instanceof final CurveTab curveTab) {
                // Its text's width, which the cap is (tab-bar.css)
                final Element label = curveTab.getLabelElement();
                labelWidths[i] = label.getOffsetWidth() - horizontalPadding(label);
            }
            rest += tabWidth - (int) labelWidths[i];
        }
        // Less a pixel a tab, as the widths measured are rounded
        final double cap = TabLabelCap.cap(labelWidths, available - rest - tabs.size());
        if (cap != TabLabelCap.NONE) {
            CssProperties.set(getElement(), LABEL_CAP, (int) Math.floor(cap) + "px");
        }
    }

    private static native int horizontalPadding(Element element) /*-{
        var style = $wnd.getComputedStyle(element);
        return Math.ceil(parseFloat(style.paddingLeft) + parseFloat(style.paddingRight));
    }-*/;

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

    @Override
    protected AbstractTab createDraggableTab(final TabData tabData) {
        return new CurveTab(tabData.getIcon(),
                tabData.getIconColour(),
                tabData.getLabel(),
                tabData.getTooltip().orElse(null),
                tabData.isCloseable());
    }

    /// The room between document tabs, for a focused tab's focus ring, which is drawn outside it.
    ///
    /// @return The gap in pixels.
    @Override
    protected int getTabGap() {
        return 6;
    }

    @Override
    public HandlerRegistration addShowTabMenuHandler(final Handler handler) {
        return addHandler(handler, ShowTabMenuEvent.getType());
    }


    // --------------------------------------------------------------------------------


    // The parts of the template the bar needs; the bar's element itself is the template's root
    static final class Parts {

        @UiField
        DivElement tabList;
        @UiField
        DivElement loadStatus;
        @UiField
        SpanElement loadDescription;
    }


    // --------------------------------------------------------------------------------


    @UiTemplate("CurveTabBar.ui.xml")
    interface Binder extends UiBinder<DivElement, Parts> {

    }
}
