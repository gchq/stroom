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
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public abstract class AbstractSettingsTabPresenter<V extends View> extends MyPresenterWidget<V>
        implements ComponentDataModifier {
    private Components components;

    @Inject
    public AbstractSettingsTabPresenter(final EventBus eventBus, final V view) {
        super(eventBus, view);
    }

    @Override
    public Components getComponents() {
        return components;
    }

    @Override
    public void setComponents(final Components components) {
        this.components = components;
    }
}
