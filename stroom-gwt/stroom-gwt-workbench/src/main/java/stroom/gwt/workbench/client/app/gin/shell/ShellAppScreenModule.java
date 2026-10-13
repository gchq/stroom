/*
 * Copyright 2026 Crown Copyright
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


package stroom.gwt.workbench.client.app.gin.shell;

import stroom.about.client.presenter.AboutPresenter;
import stroom.about.client.presenter.AboutPresenter.AboutProxy;
import stroom.about.client.presenter.AboutPresenter.AboutView;
import stroom.about.client.view.AboutViewImpl;
import stroom.explorer.client.presenter.NavigationPresenter;
import stroom.explorer.client.presenter.NavigationPresenter.NavigationProxy;
import stroom.explorer.client.presenter.NavigationPresenter.NavigationView;
import stroom.explorer.client.presenter.RecentItemsPresenter;
import stroom.explorer.client.presenter.RecentItemsPresenter.RecentItemsProxy;
import stroom.explorer.client.view.NavigationViewImpl;
import stroom.feed.client.presenter.FeedPresenter;
import stroom.feed.client.presenter.FeedSettingsPresenter;
import stroom.feed.client.presenter.FeedSettingsPresenter.FeedSettingsView;
import stroom.feed.client.view.FeedSettingsViewImpl;
import stroom.main.client.presenter.MainPresenter.MainView;
import stroom.main.client.view.MainViewImpl;
import stroom.preferences.client.EditorPreferencesPresenter;
import stroom.preferences.client.EditorPreferencesPresenter.EditorPreferencesView;
import stroom.preferences.client.EditorPreferencesViewImpl;
import stroom.preferences.client.ThemePreferencesPresenter;
import stroom.preferences.client.ThemePreferencesPresenter.ThemePreferencesView;
import stroom.preferences.client.ThemePreferencesViewImpl;
import stroom.preferences.client.TimePreferencesPresenter;
import stroom.preferences.client.TimePreferencesPresenter.TimePreferencesView;
import stroom.preferences.client.TimePreferencesViewImpl;
import stroom.preferences.client.UserPreferencesPresenter;
import stroom.preferences.client.UserPreferencesPresenter.UserPreferencesView;
import stroom.preferences.client.UserPreferencesViewImpl;
import stroom.security.identity.client.presenter.CurrentPasswordPresenter;
import stroom.security.identity.client.presenter.CurrentPasswordPresenter.CurrentPasswordView;
import stroom.security.identity.client.view.CurrentPasswordViewImpl;
import stroom.welcome.client.presenter.WelcomePresenter;
import stroom.welcome.client.presenter.WelcomePresenter.WelcomeView;
import stroom.welcome.client.view.WelcomeViewImpl;

import com.google.inject.Provides;
import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The bindings of Stroom's app shell that the `shell` batch's `App/Main/AppShell` stories need and
/// no other batch's module has: the main view and the explorer's navigation panel (Stroom's
/// `AppModule`, `stroom-app-gwt`), the About, User Preferences, Welcome and current password
/// screens (`AboutModule`, `UserPreferencesModule`, `WelcomeModule`, `ChangePasswordModule`) and the
/// Feed editor (`FeedModule`, without the data upload dialog, which another module of the
/// ginjector binds).
///
/// Plugins are left out, as in the other mirrors: the story gets them from the ginjector, which
/// registers them. Presenters that Stroom binds with a GWTP proxy are bound with a null proxy: the
/// story registers a presenter as its event's handler, as the proxy would.
public class ShellAppScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        // AppModule (the main presenter is created by the story, with the app's key handler)
        bind(MainView.class).to(MainViewImpl.class);
        bindPresenterWidget(NavigationPresenter.class,
                NavigationView.class,
                NavigationViewImpl.class);
        bind(RecentItemsPresenter.class);

        // AboutModule
        bindPresenterWidget(AboutPresenter.class,
                AboutView.class,
                AboutViewImpl.class);

        // UserPreferencesModule
        bindPresenterWidget(UserPreferencesPresenter.class,
                UserPreferencesView.class,
                UserPreferencesViewImpl.class);
        bindPresenterWidget(ThemePreferencesPresenter.class,
                ThemePreferencesView.class,
                ThemePreferencesViewImpl.class);
        bindPresenterWidget(EditorPreferencesPresenter.class,
                EditorPreferencesView.class,
                EditorPreferencesViewImpl.class);
        bindPresenterWidget(TimePreferencesPresenter.class,
                TimePreferencesView.class,
                TimePreferencesViewImpl.class);

        // WelcomeModule
        bindPresenterWidget(WelcomePresenter.class,
                WelcomeView.class,
                WelcomeViewImpl.class);

        // ChangePasswordModule (the rest is in the security batch's IdentityScreenModule)
        bindPresenterWidget(CurrentPasswordPresenter.class,
                CurrentPasswordView.class,
                CurrentPasswordViewImpl.class);

        // FeedModule
        bind(FeedPresenter.class);
        bindSharedView(FeedSettingsView.class,
                FeedSettingsViewImpl.class);
        bind(FeedSettingsPresenter.class);
    }

    /// @return No proxy, see the class description.
    @Provides
    NavigationProxy provideNavigationProxy() {
        return null;
    }

    /// @return No proxy, see the class description.
    @Provides
    RecentItemsProxy provideRecentItemsProxy() {
        return null;
    }

    /// @return No proxy, see the class description.
    @Provides
    AboutProxy provideAboutProxy() {
        return null;
    }
}
