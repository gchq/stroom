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

package stroom.app.client.gin;

import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import com.gwtplatform.mvp.client.RootPresenter;
import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;
import com.gwtplatform.mvp.client.proxy.ParameterTokenFormatter;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.TokenFormatter;
import stroom.dashboard.client.main.DashboardAppPresenter;
import stroom.dashboard.client.main.DashboardAppViewImpl;
import stroom.explorer.client.presenter.EntityTreePresenter;
import stroom.explorer.client.view.EntityTreeViewImpl;
import stroom.widget.dropdowntree.client.presenter.DropDownPresenter;
import stroom.widget.dropdowntree.client.presenter.DropDownTreePresenter;
import stroom.widget.dropdowntree.client.view.DropDownTreeViewImpl;
import stroom.widget.dropdowntree.client.view.DropDownViewImpl;

public class DashboardAppModule extends AbstractPresenterModule {
    @Override
    protected void configure() {
        // Default implementation of standard resources
        bind(EventBus.class).to(SimpleEventBus.class).in(Singleton.class);
        bind(TokenFormatter.class).to(ParameterTokenFormatter.class).in(Singleton.class);
        bind(RootPresenter.class).asEagerSingleton();
        bind(PlaceManager.class).to(InactivePlaceManager.class).in(Singleton.class);

        bindPresenter(DashboardAppPresenter.class, DashboardAppPresenter.DashboardAppView.class, DashboardAppViewImpl.class, DashboardAppPresenter.DashboardAppProxy.class);

        bindSharedView(DropDownPresenter.DropDrownView.class, DropDownViewImpl.class);
        bindSharedView(DropDownTreePresenter.DropDownTreeView.class, DropDownTreeViewImpl.class);
        bindPresenterWidget(EntityTreePresenter.class, EntityTreePresenter.EntityTreeView.class, EntityTreeViewImpl.class);
    }
}
