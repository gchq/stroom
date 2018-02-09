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

package stroom.dashboard.client.main;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.shared.ComponentConfig;
import stroom.query.api.v2.DocRef;
import stroom.util.shared.EqualsUtil;

public class BasicSettingsTabPresenter<V extends BasicSettingsTabPresenter.SettingsView>
        extends AbstractSettingsTabPresenter<V> {
    @Inject
    public BasicSettingsTabPresenter(final EventBus eventBus, final V view) {
        super(eventBus, view);
    }

    @Override
    public void read(final ComponentConfig componentData) {
        getView().setId(componentData.getId());
        getView().setName(componentData.getName());
    }

    @Override
    public void write(final ComponentConfig componentData) {
        componentData.setName(getView().getName());
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public boolean isDirty(final ComponentConfig componentData) {
        return !EqualsUtil.isEquals(componentData.getName(), getView().getName());
    }

    public interface SettingsView extends View {
        void setId(String id);

        String getName();

        void setName(String name);
    }
}
