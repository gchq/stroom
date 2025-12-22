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

package stroom.preferences.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.preferences.client.EditorPreferencesPresenter;
import stroom.preferences.client.EditorPreferencesPresenter.EditorPreferencesView;
import stroom.preferences.client.EditorPreferencesViewImpl;
import stroom.preferences.client.ThemePreferencesPresenter;
import stroom.preferences.client.ThemePreferencesPresenter.ThemePreferencesView;
import stroom.preferences.client.ThemePreferencesViewImpl;
import stroom.preferences.client.TimePreferencesPresenter;
import stroom.preferences.client.TimePreferencesPresenter.TimePreferencesView;
import stroom.preferences.client.TimePreferencesViewImpl;
import stroom.preferences.client.UserPreferencesPlugin;
import stroom.preferences.client.UserPreferencesPresenter;
import stroom.preferences.client.UserPreferencesPresenter.UserPreferencesView;
import stroom.preferences.client.UserPreferencesViewImpl;

public class UserPreferencesModule extends PluginModule {

    @Override
    protected void configure() {
        bindPresenterWidget(
                UserPreferencesPresenter.class,
                UserPreferencesView.class,
                UserPreferencesViewImpl.class);
        bindPresenterWidget(
                ThemePreferencesPresenter.class,
                ThemePreferencesView.class,
                ThemePreferencesViewImpl.class);
        bindPresenterWidget(
                EditorPreferencesPresenter.class,
                EditorPreferencesView.class,
                EditorPreferencesViewImpl.class);
        bindPresenterWidget(
                TimePreferencesPresenter.class,
                TimePreferencesView.class,
                TimePreferencesViewImpl.class);

        bindPlugin(UserPreferencesPlugin.class);
    }
}
