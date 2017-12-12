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
import stroom.about.client.presenter.AboutPresenter;
import stroom.about.client.presenter.AboutPresenter.AboutProxy;
import stroom.about.client.presenter.AboutPresenter.AboutView;
import stroom.about.client.view.AboutViewImpl;
import stroom.app.client.presenter.AppPresenter;
import stroom.app.client.view.AppViewImpl;
import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.content.client.presenter.ContentTabPanePresenter.ContentTabPaneProxy;
import stroom.core.client.ContentManager;
import stroom.core.client.KeyboardInterceptor;
import stroom.core.client.LocationManager;
import stroom.core.client.NameTokens;
import stroom.core.client.gin.InactivePlaceManager;
import stroom.entity.client.presenter.InfoDocumentPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.view.InfoDocumentViewImpl;
import stroom.entity.client.view.LinkTabPanelViewImpl;
import stroom.explorer.client.presenter.EntityCheckTreePresenter;
import stroom.explorer.client.presenter.EntityCheckTreePresenter.EntityCheckTreeView;
import stroom.explorer.client.presenter.EntityTreePresenter;
import stroom.explorer.client.presenter.EntityTreePresenter.EntityTreeView;
import stroom.explorer.client.presenter.ExplorerTabPanePresenter;
import stroom.explorer.client.presenter.ExplorerTabPanePresenter.ExplorerTabPaneProxy;
import stroom.explorer.client.presenter.ExplorerTreePresenter;
import stroom.explorer.client.presenter.ExplorerTreePresenter.ExplorerTreeProxy;
import stroom.explorer.client.presenter.ExplorerTreePresenter.ExplorerTreeView;
import stroom.explorer.client.view.EntityCheckTreeViewImpl;
import stroom.explorer.client.view.EntityTreeViewImpl;
import stroom.explorer.client.view.ExplorerTreeViewImpl;
import stroom.item.client.presenter.ListPresenter.ListView;
import stroom.item.client.view.ListViewImpl;
import stroom.main.client.presenter.MainPresenter;
import stroom.main.client.presenter.MainPresenter.MainProxy;
import stroom.main.client.presenter.MainPresenter.MainView;
import stroom.main.client.view.MainViewImpl;
import stroom.menubar.client.presenter.MenubarPresenter;
import stroom.menubar.client.presenter.MenubarPresenter.MenubarProxy;
import stroom.menubar.client.presenter.MenubarPresenter.MenubarView;
import stroom.menubar.client.view.MenubarViewImpl;
import stroom.widget.iframe.client.presenter.IFramePresenter;
import stroom.widget.iframe.client.presenter.IFramePresenter.IFrameView;
import stroom.widget.iframe.client.view.IFrameViewImpl;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.tab.client.presenter.CurveTabLayoutView;
import stroom.widget.tab.client.view.CurveTabLayoutViewImpl;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipPresenter.TooltipView;
import stroom.widget.tooltip.client.view.TooltipViewImpl;

public class AppModule extends AbstractPresenterModule {
    @Override
    protected void configure() {
        // Default implementation of standard resources
        bind(EventBus.class).to(SimpleEventBus.class).in(Singleton.class);
        bind(TokenFormatter.class).to(ParameterTokenFormatter.class).in(Singleton.class);
        bind(RootPresenter.class).asEagerSingleton();
        bind(PlaceManager.class).to(InactivePlaceManager.class).in(Singleton.class);
        // bind(PlaceManager.class).to(AppPlaceManager.class).in(Singleton.class);
        // install(new DefaultModule(AppPlaceManager.class));

        bind(KeyboardInterceptor.class).asEagerSingleton();

        // bind(CurrentUser.class).in(Singleton.class);

        bind(LocationManager.class).asEagerSingleton();
        bind(ContentManager.class).asEagerSingleton();

        // Constants
        bindConstant().annotatedWith(DefaultPlace.class).to(NameTokens.LOGIN);

        // Presenters
        bindPresenter(AppPresenter.class, AppPresenter.AppView.class, AppViewImpl.class, AppPresenter.AppProxy.class);

        bindPresenter(MainPresenter.class, MainView.class, MainViewImpl.class, MainProxy.class);
        bindPresenter(MenubarPresenter.class, MenubarView.class, MenubarViewImpl.class, MenubarProxy.class);
        bindPresenter(ExplorerTabPanePresenter.class, ExplorerTabPaneProxy.class);
        bindPresenter(ContentTabPanePresenter.class, ContentTabPaneProxy.class);

        bindPresenter(ExplorerTreePresenter.class, ExplorerTreeView.class, ExplorerTreeViewImpl.class,
                ExplorerTreeProxy.class);

        bindPresenter(AboutPresenter.class, AboutView.class, AboutViewImpl.class, AboutProxy.class);

        bindPresenterWidget(TooltipPresenter.class, TooltipView.class, TooltipViewImpl.class);
        bindPresenterWidget(IFramePresenter.class, IFrameView.class, IFrameViewImpl.class);

        bindPresenterWidget(EntityTreePresenter.class, EntityTreeView.class, EntityTreeViewImpl.class);
        bindPresenterWidget(EntityCheckTreePresenter.class, EntityCheckTreeView.class, EntityCheckTreeViewImpl.class);

        bind(MenuItems.class).in(Singleton.class);

        // Widgets
        // bindPresenterWidget(CurveTabPresenter.class, CurveTabView.class,
        // CurveTabLayoutViewImpl.class);
        bind(MenuListPresenter.class);
        // bindSharedView(CellTreeView.class, CellTreeViewImpl.class);
        // bind(LinkTabPanelPresenter.class);
        bindSharedView(CurveTabLayoutView.class, CurveTabLayoutViewImpl.class);
        bindSharedView(LinkTabPanelView.class, LinkTabPanelViewImpl.class);
        bindSharedView(ListView.class, ListViewImpl.class);
    }
}
