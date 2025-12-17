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

package stroom.app.client.gin;

import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.content.client.presenter.ContentTabPanePresenter.ContentTabPaneProxy;
import stroom.core.client.ContentManager;
import stroom.core.client.HasSaveRegistry;
import stroom.core.client.LocationManager;
import stroom.core.client.NameTokens;
import stroom.core.client.gin.InactivePlaceManager;
import stroom.core.client.presenter.CorePresenter;
import stroom.core.client.presenter.CorePresenter.CoreProxy;
import stroom.core.client.presenter.CorePresenter.CoreView;
import stroom.core.client.presenter.FullScreenPresenter;
import stroom.core.client.presenter.FullScreenPresenter.FullScreenProxy;
import stroom.core.client.presenter.FullScreenPresenter.FullScreenView;
import stroom.core.client.view.CoreViewImpl;
import stroom.core.client.view.FullScreenViewImpl;
import stroom.data.grid.client.PagerView;
import stroom.data.grid.client.PagerViewImpl;
import stroom.data.grid.client.PagerViewWithHeading;
import stroom.data.grid.client.PagerViewWithHeadingImpl;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.view.LinkTabPanelViewImpl;
import stroom.event.client.StaticEventBus;
import stroom.explorer.client.presenter.EntityCheckTreePresenter;
import stroom.explorer.client.presenter.EntityCheckTreePresenter.EntityCheckTreeView;
import stroom.explorer.client.presenter.EntityTreePresenter;
import stroom.explorer.client.presenter.EntityTreePresenter.EntityTreeView;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter.ExplorerNodeEditTagsProxy;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter.ExplorerNodeEditTagsView;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter.ExplorerNodeRemoveTagsProxy;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter.ExplorerNodeRemoveTagsView;
import stroom.explorer.client.presenter.FindInContentPresenter;
import stroom.explorer.client.presenter.FindInContentPresenter.FindInContentProxy;
import stroom.explorer.client.presenter.FindInContentPresenter.FindInContentView;
import stroom.explorer.client.presenter.FindPresenter;
import stroom.explorer.client.presenter.FindPresenter.FindProxy;
import stroom.explorer.client.presenter.NavigationPresenter;
import stroom.explorer.client.presenter.NavigationPresenter.NavigationProxy;
import stroom.explorer.client.presenter.NavigationPresenter.NavigationView;
import stroom.explorer.client.presenter.RecentItemsPresenter;
import stroom.explorer.client.presenter.RecentItemsPresenter.RecentItemsProxy;
import stroom.explorer.client.presenter.TypeFilterPresenter;
import stroom.explorer.client.presenter.TypeFilterPresenter.TypeFilterView;
import stroom.explorer.client.presenter.TypeFilterViewImpl;
import stroom.explorer.client.view.EntityCheckTreeViewImpl;
import stroom.explorer.client.view.EntityTreeViewImpl;
import stroom.explorer.client.view.ExplorerNodeEditTagsViewImpl;
import stroom.explorer.client.view.ExplorerNodeRemoveTagsViewImpl;
import stroom.explorer.client.view.FindInContentViewImpl;
import stroom.explorer.client.view.NavigationViewImpl;
import stroom.hyperlink.client.HyperlinkEventHandlerImpl;
import stroom.iframe.client.presenter.IFrameContentPresenter;
import stroom.iframe.client.presenter.IFrameContentPresenter.IFrameContentView;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.iframe.client.presenter.IFramePresenter.IFrameView;
import stroom.iframe.client.view.IFrameContentViewImpl;
import stroom.iframe.client.view.IFrameViewImpl;
import stroom.main.client.presenter.GlobalKeyHandlerImpl;
import stroom.main.client.presenter.MainPresenter;
import stroom.main.client.presenter.MainPresenter.MainProxy;
import stroom.main.client.presenter.MainPresenter.MainView;
import stroom.main.client.view.MainViewImpl;
import stroom.widget.menu.client.presenter.Menu;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.MenuPresenter;
import stroom.widget.menu.client.presenter.MenuPresenter.MenuView;
import stroom.widget.menu.client.presenter.MenuViewImpl;
import stroom.widget.tab.client.presenter.CurveTabLayoutView;
import stroom.widget.tab.client.view.CurveTabLayoutViewImpl;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipPresenter.TooltipView;
import stroom.widget.tooltip.client.view.TooltipViewImpl;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import com.gwtplatform.mvp.client.RootPresenter;
import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;
import com.gwtplatform.mvp.client.proxy.ParameterTokenFormatter;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.TokenFormatter;

public class AppModule extends AbstractPresenterModule {

    @Override
    protected void configure() {
        // Default implementation of standard resources
        bind(EventBus.class).to(SimpleEventBus.class).in(Singleton.class);
        bind(StaticEventBus.class).asEagerSingleton();

        bind(TokenFormatter.class).to(ParameterTokenFormatter.class).in(Singleton.class);
        bind(RootPresenter.class).asEagerSingleton();
        bind(PlaceManager.class).to(InactivePlaceManager.class).in(Singleton.class);
        bind(GlobalKeyHandler.class).to(GlobalKeyHandlerImpl.class).in(Singleton.class);
        // bind(PlaceManager.class).to(AppPlaceManager.class).in(Singleton.class);
        // install(new DefaultModule(AppPlaceManager.class));

        bind(HyperlinkEventHandlerImpl.class).asEagerSingleton();

        // bind(CurrentUser.class).in(Singleton.class);

        bind(HasSaveRegistry.class).asEagerSingleton();
        bind(LocationManager.class).asEagerSingleton();
        bind(ContentManager.class).asEagerSingleton();

        // Constants
        bindConstant().annotatedWith(DefaultPlace.class).to(NameTokens.LOGIN);

        // Presenters
        bindPresenter(CorePresenter.class, CoreView.class, CoreViewImpl.class, CoreProxy.class);

        bindPresenter(
                FullScreenPresenter.class,
                FullScreenView.class,
                FullScreenViewImpl.class,
                FullScreenProxy.class);

        bindPresenter(MainPresenter.class, MainView.class, MainViewImpl.class, MainProxy.class);
        bindPresenter(
                NavigationPresenter.class,
                NavigationView.class,
                NavigationViewImpl.class,
                NavigationProxy.class);
        bindPresenter(ContentTabPanePresenter.class, ContentTabPaneProxy.class);

        bindPresenterWidget(TypeFilterPresenter.class, TypeFilterView.class, TypeFilterViewImpl.class);

        bindPresenterWidget(TooltipPresenter.class, TooltipView.class, TooltipViewImpl.class);

        bindPresenterWidget(IFramePresenter.class, IFrameView.class, IFrameViewImpl.class);
        bindPresenterWidget(IFrameContentPresenter.class, IFrameContentView.class, IFrameContentViewImpl.class);

        bindPresenterWidget(EntityTreePresenter.class, EntityTreeView.class, EntityTreeViewImpl.class);
        bindPresenterWidget(EntityCheckTreePresenter.class, EntityCheckTreeView.class, EntityCheckTreeViewImpl.class);
        bindPresenter(
                ExplorerNodeEditTagsPresenter.class,
                ExplorerNodeEditTagsView.class,
                ExplorerNodeEditTagsViewImpl.class,
                ExplorerNodeEditTagsProxy.class);
        bindPresenter(
                ExplorerNodeRemoveTagsPresenter.class,
                ExplorerNodeRemoveTagsView.class,
                ExplorerNodeRemoveTagsViewImpl.class,
                ExplorerNodeRemoveTagsProxy.class);

        bindPresenter(
                FindInContentPresenter.class,
                FindInContentView.class,
                FindInContentViewImpl.class,
                FindInContentProxy.class);
        bindPresenter(
                FindPresenter.class,
                FindProxy.class);
        bindPresenter(
                RecentItemsPresenter.class,
                RecentItemsProxy.class);

        // Menu
        bind(Menu.class).asEagerSingleton();
        bind(MenuItems.class).in(Singleton.class);
        bindPresenterWidget(MenuPresenter.class, MenuView.class, MenuViewImpl.class);


        // Widgets
        bindSharedView(CurveTabLayoutView.class, CurveTabLayoutViewImpl.class);
        bindSharedView(PagerView.class, PagerViewImpl.class);
        bindSharedView(PagerViewWithHeading.class, PagerViewWithHeadingImpl.class);
        bindSharedView(LinkTabPanelView.class, LinkTabPanelViewImpl.class);
    }
}
