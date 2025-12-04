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

package stroom.widget.tab.client.view;

import stroom.widget.tab.client.event.HasShowTabMenuHandlers;
import stroom.widget.tab.client.event.ShowTabMenuEvent;
import stroom.widget.tab.client.event.ShowTabMenuEvent.Handler;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.event.shared.HandlerRegistration;

public class CurveTabBar extends DraggableTabBar implements HasShowTabMenuHandlers {

    public CurveTabBar() {
        getElement().setClassName("curveTabBar");
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

    @Override
    protected AbstractTabSelector createTabSelector() {
        return new CurveTabSelector();
    }

    @Override
    public HandlerRegistration addShowTabMenuHandler(final Handler handler) {
        return addHandler(handler, ShowTabMenuEvent.getType());
    }
}
