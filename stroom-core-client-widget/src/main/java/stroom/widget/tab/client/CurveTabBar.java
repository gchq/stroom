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

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiTemplate;

/// The document tabs' bar, whose tabs can be dragged to reorder them. Its markup is
/// `CurveTabBar.ui.xml`.
public class CurveTabBar extends DraggableTabBar implements HasShowTabMenuHandlers {

    private static final Binder BINDER = GWT.create(Binder.class);

    public CurveTabBar() {
        this(new Parts());
    }

    private CurveTabBar(final Parts parts) {
        super(BINDER.createAndBindUi(parts), parts.tabList);
    }

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
    }


    // --------------------------------------------------------------------------------


    @UiTemplate("CurveTabBar.ui.xml")
    interface Binder extends UiBinder<DivElement, Parts> {

    }
}
