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

package stroom.about.client.gin;

import stroom.about.client.AboutPlugin;
import stroom.about.client.presenter.AboutPresenter;
import stroom.about.client.presenter.AboutPresenter.AboutProxy;
import stroom.about.client.presenter.AboutPresenter.AboutView;
import stroom.about.client.view.AboutViewImpl;
import stroom.core.client.gin.PluginModule;

public class AboutModule extends PluginModule {

    @Override
    protected void configure() {
        bind(AboutPlugin.class).asEagerSingleton();

        bindPresenter(AboutPresenter.class, AboutView.class, AboutViewImpl.class, AboutProxy.class);
    }
}
