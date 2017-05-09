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

package stroom.explorer.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;
import stroom.explorer.client.event.CloseExplorerTabEvent;
import stroom.explorer.client.event.OpenExplorerTabEvent;
import stroom.main.client.presenter.MainPresenter;
import stroom.widget.tab.client.presenter.CurveTabLayoutPresenter;
import stroom.widget.tab.client.presenter.CurveTabLayoutView;
import stroom.widget.tab.client.presenter.TabData;

import java.util.HashSet;
import java.util.Set;

public class ExplorerTabPanePresenter extends CurveTabLayoutPresenter<ExplorerTabPanePresenter.ExplorerTabPaneProxy>
        implements OpenExplorerTabEvent.Handler, CloseExplorerTabEvent.Handler {
    @ProxyCodeSplit
    public interface ExplorerTabPaneProxy extends Proxy<ExplorerTabPanePresenter> {
    }

    private final Set<TabData> openItems = new HashSet<TabData>();

    @Inject
    public ExplorerTabPanePresenter(final EventBus eventBus, final CurveTabLayoutView view,
            final ExplorerTabPaneProxy proxy) {
        super(eventBus, view, proxy);
    }

    @Override
    protected void revealInParent() {
        RevealContentEvent.fire(this, MainPresenter.EXPLORER, this);
    }

    @ProxyEvent
    @Override
    public void onOpen(final OpenExplorerTabEvent event) {
        // Make sure this tab pane is revealed before we try and reveal child
        // tabs.
        revealInParent();

        final TabData tabData = event.getTabData();
        if (tabData != null && openItems.contains(tabData)) {
            selectTab(tabData);
        } else {
            openItems.add(tabData);
            add(tabData, event.getLayer());
        }
    }

    @ProxyEvent
    @Override
    public void onClose(final CloseExplorerTabEvent event) {
        final TabData tabData = event.getTabData();

        // Remove from sets.
        openItems.remove(tabData);

        // Remove from display.
        remove(tabData);
    }

    @Override
    protected void fireSelectedTabChange(final TabData tabData) {
        // Do nothing as nobody cares which explorer tab pane tab is selected.
    }
}
