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

import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.core.client.ContentManager;
import stroom.core.client.HasSaveRegistry;
import stroom.core.client.LocationManager;
import stroom.core.client.presenter.CorePresenter;
import stroom.core.client.presenter.CorePresenter.CoreView;
import stroom.core.client.presenter.CoreScreens;
import stroom.core.client.presenter.FullScreenPresenter;
import stroom.core.client.presenter.FullScreenPresenter.FullScreenView;
import stroom.core.client.view.CoreViewImpl;
import stroom.core.client.view.FullScreenViewImpl;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.view.LinkTabPanelViewImpl;
import stroom.event.client.StaticEventBus;
import stroom.explorer.client.presenter.NavigationPresenter;
import stroom.explorer.client.presenter.NavigationPresenter.NavigationView;
import stroom.explorer.client.view.NavigationViewImpl;
import stroom.hyperlink.client.HyperlinkEventHandlerImpl;
import stroom.iframe.client.presenter.IFrameContentPresenter;
import stroom.iframe.client.presenter.IFrameContentPresenter.IFrameContentView;
import stroom.iframe.client.view.IFrameContentViewImpl;
import stroom.main.client.presenter.GlobalKeyHandlerImpl;
import stroom.main.client.presenter.MainPresenter;
import stroom.main.client.presenter.MainPresenter.MainView;
import stroom.main.client.presenter.MainScreens;
import stroom.main.client.view.MainViewImpl;
import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.MenuManager;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;
import stroom.widget.tab.client.presenter.CurveTabLayoutView;
import stroom.widget.tab.client.view.CurveTabLayoutViewImpl;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;

public class AppModule extends AbstractPresenterModule {

    @Override
    protected void configure() {
        // Default implementation of standard resources
        bind(EventBus.class).to(SimpleEventBus.class).in(Singleton.class);
        bind(StaticEventBus.class).asEagerSingleton();

        bind(GlobalKeyHandler.class).to(GlobalKeyHandlerImpl.class).in(Singleton.class);

        bind(HyperlinkEventHandlerImpl.class).asEagerSingleton();

        // bind(CurrentUser.class).in(Singleton.class);

        bind(HasSaveRegistry.class).asEagerSingleton();
        bind(LocationManager.class).asEagerSingleton();
        bind(ContentManager.class).asEagerSingleton();

        // Presenters
        // Hear the app-wide events that open its screens, for the life of the app
        bind(CoreScreens.class).asEagerSingleton();
        bind(MainScreens.class).asEagerSingleton();
        bindPresenter(CorePresenter.class, CoreView.class, CoreViewImpl.class);

        bindPresenter(
                FullScreenPresenter.class,
                FullScreenView.class,
                FullScreenViewImpl.class);

        bindPresenter(MainPresenter.class, MainView.class, MainViewImpl.class);
        bindPresenter(
                NavigationPresenter.class,
                NavigationView.class,
                NavigationViewImpl.class);
        bindPresenter(ContentTabPanePresenter.class);

        bindPresenterWidget(IFrameContentPresenter.class, IFrameContentView.class, IFrameContentViewImpl.class);


        // Menu
        bind(MenuManager.class).asEagerSingleton();
        // Stroom's edit menu on text fields, in place of the browser's context menu
        bind(ContextMenuManager.class).asEagerSingleton();
        bind(MenuItems.class).in(Singleton.class);

        // Widgets
        bindSharedView(CurveTabLayoutView.class, CurveTabLayoutViewImpl.class);
        bindSharedView(LinkTabPanelView.class, LinkTabPanelViewImpl.class);
    }
}
