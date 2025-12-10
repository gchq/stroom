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

package stroom.dashboard.client.text.gin;

import stroom.dashboard.client.text.BasicTextSettingsPresenter;
import stroom.dashboard.client.text.BasicTextSettingsPresenter.BasicTextSettingsView;
import stroom.dashboard.client.text.BasicTextSettingsViewImpl;
import stroom.dashboard.client.text.TextPlugin;
import stroom.dashboard.client.text.TextPresenter;
import stroom.dashboard.client.text.TextPresenter.TextView;
import stroom.dashboard.client.text.TextViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

public class TextModule extends AbstractPresenterModule {

    @Override
    protected void configure() {
        bind(TextPlugin.class).asEagerSingleton();
        bindPresenterWidget(TextPresenter.class, TextView.class, TextViewImpl.class);
        bindPresenterWidget(BasicTextSettingsPresenter.class, BasicTextSettingsView.class,
                BasicTextSettingsViewImpl.class);
    }
}
