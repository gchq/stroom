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

package stroom.pipeline.stepping.client.view;

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.client.presenter.StepLocationPresenter.StepLocationView;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class StepLocationViewImpl extends ViewImpl implements StepLocationView {

    private final Widget widget;
    @UiField
    ValueSpinner metaId;
    @UiField
    ValueSpinner partNo;
    @UiField
    ValueSpinner recordNo;

    @Inject
    public StepLocationViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        metaId.setMax(Long.MAX_VALUE);
        metaId.setMin(0);
        partNo.setMax(Long.MAX_VALUE);
        partNo.setMin(1);
        recordNo.setMax(Long.MAX_VALUE);
        recordNo.setMin(1);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        metaId.focus();
    }

    @Override
    public StepLocation getStepLocation() {
        return new StepLocation(metaId.getValue(), partNo.getValue() - 1, recordNo.getValue() - 1);
    }

    @Override
    public void setStepLocation(final StepLocation stepLocation) {
        metaId.setValue(stepLocation.getMetaId());
        partNo.setValue(stepLocation.getPartIndex() + 1);
        recordNo.setValue(stepLocation.getRecordIndex() + 1);
    }

    public interface Binder extends UiBinder<Widget, StepLocationViewImpl> {

    }
}
