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

import stroom.widget.tab.client.event.RequestCloseTabEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CurveTabLayoutPresenter<P extends Proxy<?>> extends MyPresenter<CurveTabLayoutView, P> {

    private final Map<TabData, Layer> tabContentMap = new HashMap<>();

    @Inject
    public CurveTabLayoutPresenter(final EventBus eventBus, final CurveTabLayoutView view, final P proxy) {
        super(eventBus, view, proxy);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getTabBar().addSelectionHandler(event ->
                selectTab(event.getSelectedItem())));

        registerHandler(getView().getTabBar().addRequestCloseTabHandler(event ->
                RequestCloseTabEvent.fire(
                        CurveTabLayoutPresenter.this,
                        event.getTabData(),
                        event.isForce())));

        registerHandler(getView().getTabBar().addShowMenuHandler(event ->
                getEventBus().fireEvent(event)));
    }

    public void add(final TabData tabData, final Layer layer) {
        // If this item already exists then select it.
        final int index = getView().getTabBar().getTabs().indexOf(tabData);

        if (index != -1) {
            // Select the existing item.
            selectTab(tabData);

        } else {
            getView().getTabBar().addTab(tabData);
            tabContentMap.put(tabData, layer);

            // Select the newly added item.
            selectTab(tabData);
        }
    }

    public void clear() {
        final List<TabData> copy = new ArrayList<>(getView().getTabBar().getTabs());
        for (final TabData tabData : copy) {
            remove(tabData);
        }
    }

    public void remove(final TabData tabData) {
        remove(tabData, true);
    }

    public void remove(final TabData tabData, final boolean resizeTabBar) {
        final Layer layer = tabContentMap.remove(tabData);
        if (layer != null) {
            getView().getTabBar().removeTab(tabData, resizeTabBar);
            layer.removeLayer();
            if (resizeTabBar) {
                fireSelectedTabChange(getSelectedTab());
            }
        }
    }

    public void selectTab(final TabData tabData) {
        final Layer layer = tabContentMap.get(tabData);
        if (layer != null) {
            fireSelectedTabChange(tabData);
            getView().getTabBar().selectTab(tabData);
            getView().getLayerContainer().show(layer);
        }
    }

    public void moveTab(final TabData tabData, final int tabPos) {
        final Layer layer = tabContentMap.get(tabData);
        if (layer != null) {
            getView().getTabBar().moveTab(tabData, tabPos);
            getView().getTabBar().selectTab(tabData);
            getView().getLayerContainer().show(layer);
        }
    }

    public void refresh(final TabData tabData) {
        getView().getTabBar().refresh();
    }

    protected TabData getSelectedTab() {
        return getView().getTabBar().getSelectedTab();
    }

    protected abstract void fireSelectedTabChange(TabData tabData);
}
