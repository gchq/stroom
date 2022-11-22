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

package stroom.dashboard.client.input.gin;

import stroom.dashboard.client.input.KeyValueInputPlugin;
import stroom.dashboard.client.input.KeyValueInputPresenter;
import stroom.dashboard.client.input.KeyValueInputPresenter.KeyValueInputView;
import stroom.dashboard.client.input.KeyValueInputViewImpl;
import stroom.dashboard.client.input.ListInputPlugin;
import stroom.dashboard.client.input.ListInputPresenter;
import stroom.dashboard.client.input.ListInputPresenter.ListInputView;
import stroom.dashboard.client.input.ListInputViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

public class InputModule extends AbstractPresenterModule {

    @Override
    protected void configure() {
        bind(KeyValueInputPlugin.class).asEagerSingleton();
        bindPresenterWidget(KeyValueInputPresenter.class, KeyValueInputView.class, KeyValueInputViewImpl.class);

        bind(ListInputPlugin.class).asEagerSingleton();
        bindPresenterWidget(ListInputPresenter.class, ListInputView.class, ListInputViewImpl.class);
    }
}
