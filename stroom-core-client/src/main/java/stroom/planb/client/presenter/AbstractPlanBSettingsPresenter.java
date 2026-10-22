/*
 * Copyright 2025 Crown Copyright
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

import stroom.document.client.event.ChangeUiHandlers;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public abstract class AbstractPlanBSettingsPresenter<V extends View>
        extends MyPresenterWidget<V>
        implements ChangeUiHandlers {

    private boolean readOnly = true;

    private Runnable onChange = () -> {
    };

    public AbstractPlanBSettingsPresenter(
            final EventBus eventBus,
            final V view) {
        super(eventBus, view);
        // Read only as its document (or what embeds it) is, now and as that changes
        watch(Contexts.READ_ONLY, this::setReadOnly);
    }

    /// Shows settings.
    ///
    /// @param settings The settings.
    public abstract void read(AbstractPlanBSettings settings);

    public abstract AbstractPlanBSettings write();

    private void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        if (getView() instanceof final ReadOnlyChangeHandler changeHandler) {
            changeHandler.onReadOnly(readOnly);
        }
    }

    @Override
    public void onChange() {
        if (!readOnly) {
            onChange.run();
        }
    }

    /// @param onChange What to run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }
}
