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

package stroom.dashboard.client.main;

import stroom.dashboard.shared.ComponentConfig;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

public class BasicSettingsTabPresenter<V extends BasicSettingsView>
        extends AbstractSettingsTabPresenter<V> {

    @Inject
    public BasicSettingsTabPresenter(final EventBus eventBus, final V view) {
        super(eventBus, view);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        getView().setId(componentConfig.getId());
        getView().setName(componentConfig.getName());
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        return componentConfig
                .copy()
                .name(getView().getName())
                .build();
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        final ComponentConfig newComponentConfig = write(componentConfig);

        final boolean equal = Objects.equals(componentConfig.getName(), newComponentConfig.getName());

        return !equal;
    }
}
