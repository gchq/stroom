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

import stroom.core.client.UrlParameters;
import stroom.core.client.presenter.CorePresenter;
import stroom.core.client.presenter.CorePresenter.CoreView;
import stroom.core.client.presenter.CoreScreens;
import stroom.core.client.presenter.FullScreenPresenter;
import stroom.core.client.presenter.FullScreenPresenter.FullScreenView;
import stroom.core.client.view.CoreViewImpl;
import stroom.core.client.view.FullScreenViewImpl;
import stroom.data.client.presenter.DataDisplaySupport;
import stroom.data.client.presenter.DataPreviewTabPresenter;
import stroom.data.client.presenter.DataPreviewTabPresenter.DataPreviewTabView;
import stroom.data.client.presenter.SourceTabPresenter;
import stroom.data.client.presenter.SourceTabPresenter.SourceTabView;
import stroom.data.client.view.DataPreviewTabViewImpl;
import stroom.data.client.view.SourceTabViewImpl;
import stroom.editor.client.presenter.DelegatingAceCompleter;
import stroom.event.client.StaticEventBus;
import stroom.iframe.client.presenter.IFrameContentPresenter;
import stroom.iframe.client.presenter.IFrameContentPresenter.IFrameContentView;
import stroom.iframe.client.view.IFrameContentViewImpl;
import stroom.main.client.presenter.GlobalKeyHandlerImpl;
import stroom.widget.datagrid.client.WrapperView;
import stroom.widget.datagrid.client.WrapperViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;

public class DashboardAppModule extends AbstractPresenterModule {

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

        // Presenters
        // Hear the app-wide events that open its screens, for the life of the app
        bind(CoreScreens.class).asEagerSingleton();
        bindPresenter(CorePresenter.class, CoreView.class, CoreViewImpl.class);

        bindPresenter(
                FullScreenPresenter.class,
                FullScreenView.class,
                FullScreenViewImpl.class);



        bindPresenterWidget(
                IFrameContentPresenter.class,
                IFrameContentView.class,
                IFrameContentViewImpl.class);
        bindPresenterWidget(
                DataPreviewTabPresenter.class,
                DataPreviewTabView.class,
                DataPreviewTabViewImpl.class);
        bindPresenterWidget(
                SourceTabPresenter.class,
                SourceTabView.class,
                SourceTabViewImpl.class);
        bindSharedView(WrapperView.class, WrapperViewImpl.class);
    }
}
