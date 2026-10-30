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

package stroom.dashboard.client.gin;

import stroom.core.client.CoreScreens;
import stroom.core.client.UrlParameters;
import stroom.core.client.component.AppPageController;
import stroom.core.client.component.FullScreenController;
import stroom.data.client.DataDisplaySupport;
import stroom.editor.client.DelegatingAceCompleter;
import stroom.event.client.StaticEventBus;
import stroom.hyperlink.client.HyperlinkOpener;
import stroom.main.client.GlobalKeyHandlerImpl;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.inject.client.AbstractGinModule;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;

public class DashboardAppModule extends AbstractGinModule {

    @Override
    protected void configure() {
        // Default implementation of standard resources
        bind(EventBus.class).to(SimpleEventBus.class).in(Singleton.class);
        // For widgets with no event bus of their own (form help, date and time dialogs, the time
        // range), as the app's AppModule does
        bind(StaticEventBus.class).asEagerSingleton();
        bind(GlobalKeyHandler.class).to(GlobalKeyHandlerImpl.class).in(Singleton.class);
        bind(UrlParameters.class).in(Singleton.class);
        bind(DelegatingAceCompleter.class).asEagerSingleton();
        bind(DataDisplaySupport.class).asEagerSingleton();
        // Follows the hyperlinks in dashboards' tables and text, as the app's AppModule does
        bind(HyperlinkOpener.class).asEagerSingleton();

        // Controllers
        // Hear the app-wide events that open its screens, for the life of the app
        bind(CoreScreens.class).asEagerSingleton();
        bind(AppPageController.class).in(Singleton.class);

        bind(FullScreenController.class).in(Singleton.class);
    }
}
