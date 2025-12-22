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

package stroom.welcome.client;

import stroom.content.client.ContentPlugin;
import stroom.core.client.ContentManager;
import stroom.main.client.event.ShowMainEvent;
import stroom.welcome.client.presenter.WelcomePresenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class WelcomePlugin extends ContentPlugin<WelcomePresenter> implements ShowMainEvent.Handler {

    @Inject
    public WelcomePlugin(final EventBus eventBus, final ContentManager eventManager,
                         final Provider<WelcomePresenter> presenterProvider) {
        super(eventBus, eventManager, presenterProvider);

        registerHandler(getEventBus().addHandler(ShowMainEvent.getType(), this));
    }

    @Override
    public void onShowMain(final ShowMainEvent event) {
        // Don't show welcome if we have been asked to show another doc immediately.
        if (event.getInitialDocRef() == null) {
            open();
        }
    }
}
