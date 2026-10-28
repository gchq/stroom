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

import com.google.gwt.inject.client.AbstractGinModule;


/// The bindings of Stroom's app shell that the `shell` batch's `App/Main/AppShell` stories need and
/// no other batch's module has: the main view and the explorer's navigation panel (Stroom's
/// `AppModule`, `stroom-app-gwt`), the About, User Preferences and Welcome
/// screens (`AboutModule`, `UserPreferencesModule`, `WelcomeModule`) and the
/// Feed editor (`FeedModule`, without the data upload dialog, which another module of the
/// ginjector binds).
///
/// Plugins are left out, as in the other mirrors: the story gets them from the ginjector, which
/// registers them, and they add the handlers of the app-wide events that open their screens, as in
/// Stroom.
public class ShellAppScreenModule extends AbstractGinModule {

    /// Binds the controllers and components.
    @Override
    protected void configure() {
        // AppModule (the main controller is created by the story, with the app's key handler)

        // UserPreferencesModule

        // WelcomeModule

        // FeedModule
    }
}
