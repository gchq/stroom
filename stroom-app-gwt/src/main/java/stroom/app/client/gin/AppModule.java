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

import stroom.content.client.component.ContentTabPaneController;
import stroom.core.client.ContentManager;
import stroom.core.client.HasSaveRegistry;
import stroom.core.client.LocationManager;
import stroom.core.client.component.CoreController;
import stroom.core.client.component.FullScreenController;
import stroom.core.client.presenter.CoreScreens;
import stroom.event.client.StaticEventBus;
import stroom.explorer.client.component.NavigationController;
import stroom.hyperlink.client.HyperlinkEventHandlerImpl;
import stroom.main.client.component.MainController;
import stroom.main.client.presenter.GlobalKeyHandlerImpl;
import stroom.main.client.presenter.MainScreens;
import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.MenuManager;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.inject.client.AbstractGinModule;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;

public class AppModule extends AbstractGinModule {

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
        bind(CoreController.class).in(Singleton.class);

        bind(FullScreenController.class).in(Singleton.class);

        bind(MainController.class).in(Singleton.class);
        bind(NavigationController.class).in(Singleton.class);
        bind(ContentTabPaneController.class).in(Singleton.class);

        // Menu
        bind(MenuManager.class).asEagerSingleton();
        // Stroom's edit menu on text fields, in place of the browser's context menu
        bind(ContextMenuManager.class).asEagerSingleton();
        bind(MenuItems.class).in(Singleton.class);

        // Widgets
    }
}
