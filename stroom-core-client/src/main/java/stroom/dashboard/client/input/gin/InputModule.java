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

package stroom.dashboard.client.input.gin;

import stroom.dashboard.client.input.BasicKeyValueInputSettingsPresenter;
import stroom.dashboard.client.input.BasicKeyValueInputSettingsPresenter.BasicKeyValueInputSettingsView;
import stroom.dashboard.client.input.BasicKeyValueInputSettingsViewImpl;
import stroom.dashboard.client.input.BasicListInputSettingsPresenter;
import stroom.dashboard.client.input.BasicListInputSettingsPresenter.BasicListInputSettingsView;
import stroom.dashboard.client.input.BasicListInputSettingsViewImpl;
import stroom.dashboard.client.input.BasicTableFilterSettingsPresenter;
import stroom.dashboard.client.input.BasicTableFilterSettingsPresenter.BasicTableFilterSettingsView;
import stroom.dashboard.client.input.BasicTableFilterSettingsViewImpl;
import stroom.dashboard.client.input.BasicTextInputSettingsPresenter;
import stroom.dashboard.client.input.BasicTextInputSettingsPresenter.BasicTextInputSettingsView;
import stroom.dashboard.client.input.BasicTextInputSettingsViewImpl;
import stroom.dashboard.client.input.KeyValueInputPlugin;
import stroom.dashboard.client.input.KeyValueInputPresenter;
import stroom.dashboard.client.input.KeyValueInputPresenter.KeyValueInputView;
import stroom.dashboard.client.input.KeyValueInputViewImpl;
import stroom.dashboard.client.input.ListInputPlugin;
import stroom.dashboard.client.input.ListInputPresenter;
import stroom.dashboard.client.input.ListInputPresenter.ListInputView;
import stroom.dashboard.client.input.ListInputViewImpl;
import stroom.dashboard.client.input.MultiRulesPresenter;
import stroom.dashboard.client.input.MultiRulesPresenter.MultiRulesView;
import stroom.dashboard.client.input.MultiRulesViewImpl;
import stroom.dashboard.client.input.TableFilterPlugin;
import stroom.dashboard.client.input.TableFilterPresenter;
import stroom.dashboard.client.input.TableFilterPresenter.TableFilterView;
import stroom.dashboard.client.input.TableFilterViewImpl;
import stroom.dashboard.client.input.TextInputPlugin;
import stroom.dashboard.client.input.TextInputPresenter;
import stroom.dashboard.client.input.TextInputPresenter.TextInputView;
import stroom.dashboard.client.input.TextInputViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

public class InputModule extends AbstractPresenterModule {

    @Override
    protected void configure() {
        bind(KeyValueInputPlugin.class).asEagerSingleton();
        bindPresenterWidget(KeyValueInputPresenter.class, KeyValueInputView.class, KeyValueInputViewImpl.class);
        bindPresenterWidget(BasicKeyValueInputSettingsPresenter.class,
                BasicKeyValueInputSettingsView.class,
                BasicKeyValueInputSettingsViewImpl.class);

        bind(ListInputPlugin.class).asEagerSingleton();
        bindPresenterWidget(ListInputPresenter.class, ListInputView.class, ListInputViewImpl.class);
        bindPresenterWidget(BasicListInputSettingsPresenter.class,
                BasicListInputSettingsView.class,
                BasicListInputSettingsViewImpl.class);

        bind(TextInputPlugin.class).asEagerSingleton();
        bindPresenterWidget(TextInputPresenter.class, TextInputView.class, TextInputViewImpl.class);
        bindPresenterWidget(BasicTextInputSettingsPresenter.class,
                BasicTextInputSettingsView.class,
                BasicTextInputSettingsViewImpl.class);


        bind(TableFilterPlugin.class).asEagerSingleton();
        bindPresenterWidget(TableFilterPresenter.class, TableFilterView.class, TableFilterViewImpl.class);
        bindPresenterWidget(BasicTableFilterSettingsPresenter.class,
                BasicTableFilterSettingsView.class,
                BasicTableFilterSettingsViewImpl.class);
        bindPresenterWidget(MultiRulesPresenter.class,
                MultiRulesView.class,
                MultiRulesViewImpl.class);
    }
}
