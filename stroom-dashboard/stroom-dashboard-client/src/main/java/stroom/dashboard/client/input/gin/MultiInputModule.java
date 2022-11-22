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

import stroom.dashboard.client.input.MultiInputPlugin;
import stroom.dashboard.client.input.MultiInputPresenter;
import stroom.dashboard.client.input.MultiInputPresenter.MultiInputView;
import stroom.dashboard.client.input.MultiInputViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

public class MultiInputModule extends AbstractPresenterModule {

    @Override
    protected void configure() {
        bind(MultiInputPlugin.class).asEagerSingleton();
        bindPresenterWidget(MultiInputPresenter.class, MultiInputView.class, MultiInputViewImpl.class);
    }
}
