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

package stroom.pathways.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.pathways.client.presenter.PathwaysSettingsPresenter.PathwaysSettingsView;
import stroom.pathways.client.presenter.PathwaysSettingsUiHandlers;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.customdatebox.client.DurationPicker;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class PathwaysSettingsViewImpl
        extends ViewWithUiHandlers<PathwaysSettingsUiHandlers>
        implements PathwaysSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    DurationPicker temporalOrderingTolerance;

    @Inject
    public PathwaysSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public SimpleDuration getTemporalOrderingTolerance() {
        return temporalOrderingTolerance.getValue();
    }

    @Override
    public void setTemporalOrderingTolerance(final SimpleDuration temporalOrderingTolerance) {
        this.temporalOrderingTolerance.setValue(temporalOrderingTolerance);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        temporalOrderingTolerance.setEnabled(!readOnly);
    }

    @UiHandler("temporalOrderingTolerance")
    public void onTemporalOrderingTolerance(final ValueChangeEvent<SimpleDuration> e) {
        fireChange();
    }

    private void fireChange() {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    public interface Binder extends UiBinder<Widget, PathwaysSettingsViewImpl> {

    }
}
