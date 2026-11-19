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

package stroom.splash.client;

import stroom.core.client.UrlParameters;
import stroom.splash.client.component.SplashController;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.SplashConfig;
import stroom.widget.component.client.AppRoot;
import stroom.widget.task.client.DefaultTaskMonitorFactory;

import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.function.Consumer;

/// The splash screen shown once the user has logged in, if the site has one and Stroom isn't
/// embedded: makes its dialog ([SplashController]) only when it is shown.
public class SplashScreen {

    private final UiConfigCache uiConfigCache;
    private final UrlParameters urlParameters;
    private final Provider<SplashController> splashProvider;
    private final AppRoot appRoot;

    /// Made by GIN.
    ///
    /// @param uiConfigCache  Says whether the site has a splash screen, and what it says.
    /// @param urlParameters  Say whether Stroom is embedded.
    /// @param splashProvider Makes the splash screen's dialog.
    /// @param appRoot        The app, which owns the dialog while it is shown.
    @Inject
    public SplashScreen(final UiConfigCache uiConfigCache,
                        final UrlParameters urlParameters,
                        final Provider<SplashController> splashProvider,
                        final AppRoot appRoot) {
        this.uiConfigCache = uiConfigCache;
        this.urlParameters = urlParameters;
        this.splashProvider = splashProvider;
        this.appRoot = appRoot;
    }

    /// Shows the splash screen, if there is one.
    ///
    /// @param hasHandlers Where the config's lookup shows its progress.
    /// @param onClosed    Told whether the terms were accepted, or true if there is no splash
    ///                    screen.
    public void show(final HasHandlers hasHandlers, final Consumer<Boolean> onClosed) {
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                final SplashConfig splashConfig = uiConfig.getSplash();
                if (splashConfig.isEnabled() && !urlParameters.isEmbedded()) {
                    splashProvider.get().show(appRoot,
                            splashConfig.getTitle(),
                            splashConfig.getBody(),
                            splashConfig.getVersion(),
                            onClosed);
                } else {
                    onClosed.accept(true);
                }
            }
        }, new DefaultTaskMonitorFactory(hasHandlers));
    }
}
