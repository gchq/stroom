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

package stroom.widget.tab.client.presenter;

import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashMap;
import java.util.Map;

public class LinkTabsPresenter extends MyPresenterWidget<LinkTabsLayoutView> {

    private final Map<TabData, Layer> tabViewMap = new HashMap<>();

    private TabData firstTab;
    private TabData selectedTab;

    @Inject
    public LinkTabsPresenter(final EventBus eventBus, final LinkTabsLayoutView view) {
        super(eventBus, view);
    }

    public TabData addTab(final String text, final Layer layer) {
        final TabData tab = new TabDataImpl(text, false);
        tabViewMap.put(tab, layer);
        getView().getTabBar().addTab(tab);

        if (firstTab == null) {
            firstTab = tab;
        }

        return tab;
    }

    public void removeTab(final TabData tab) {
        getView().getTabBar().removeTab(tab);
        changeSelectedTab(firstTab);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getView().getTabBar().addSelectionHandler(new SelectionHandler<TabData>() {
            @Override
            public void onSelection(final SelectionEvent<TabData> event) {
                final TabData tab = event.getSelectedItem();
                if (tab != null && tab != selectedTab) {
                    changeSelectedTab(tab);
                }
            }
        }));
        registerHandler(getView().getTabBar().addShowMenuHandler(e -> getEventBus().fireEvent(e)));
    }

    public void changeSelectedTab(final TabData tab) {
        if (selectedTab != tab) {
            selectedTab = tab;
            if (selectedTab != null) {
                final Layer layer = tabViewMap.get(selectedTab);
                if (layer != null) {
                    getView().getTabBar().selectTab(tab);
                    getView().getLayerContainer().show(layer);
                }
            }
        }
    }
}
