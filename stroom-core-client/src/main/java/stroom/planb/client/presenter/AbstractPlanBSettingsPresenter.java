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

package stroom.planb.client.presenter;

import stroom.document.client.event.ChangeEvent;
import stroom.document.client.event.ChangeEvent.ChangeHandler;
import stroom.document.client.event.ChangeUiHandlers;
import stroom.document.client.event.HasChangeHandlers;
import stroom.planb.shared.AbstractPlanBSettings;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public abstract class AbstractPlanBSettingsPresenter<V extends View>
        extends MyPresenterWidget<V>
        implements ChangeUiHandlers, HasChangeHandlers {

    private boolean readOnly = true;

    public AbstractPlanBSettingsPresenter(
            final EventBus eventBus,
            final V view) {
        super(eventBus, view);
    }

    public abstract void read(AbstractPlanBSettings settings, boolean readOnly);

    public abstract AbstractPlanBSettings write();

    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public void onChange() {
        if (!readOnly) {
            ChangeEvent.fire(this);
        }
    }

    @Override
    public HandlerRegistration addChangeHandler(final ChangeHandler handler) {
        return addHandlerToSource(ChangeEvent.getType(), handler);
    }
}
