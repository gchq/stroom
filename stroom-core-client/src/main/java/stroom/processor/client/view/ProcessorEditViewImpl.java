/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.processor.client.view;

import stroom.preferences.client.UserPreferencesManager;
import stroom.processor.client.presenter.ProcessorEditPresenter.ProcessorEditView;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class ProcessorEditViewImpl extends ViewImpl implements ProcessorEditView {

    private final Widget widget;

    @UiField
    SimplePanel expression;
    @UiField
    MyDateBox minMetaCreateTimeMs;
    @UiField
    MyDateBox maxMetaCreateTimeMs;
    @UiField
    CustomCheckBox export;
    @UiField
    SimplePanel runAsUser;

    @Inject
    public ProcessorEditViewImpl(final ProcessorEditViewImpl.Binder binder,
                                 final UserPreferencesManager userPreferencesManager) {
        widget = binder.createAndBindUi(this);
        minMetaCreateTimeMs.setUtc(userPreferencesManager.isUtc());
        maxMetaCreateTimeMs.setUtc(userPreferencesManager.isUtc());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setExpressionView(final View view) {
        expression.setWidget(view.asWidget());
    }

    @Override
    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs.getMilliseconds();
    }

    @Override
    public void setMinMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
        this.minMetaCreateTimeMs.setMilliseconds(minMetaCreateTimeMs);
    }

    @Override
    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs.getMilliseconds();
    }

    @Override
    public void setMaxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
        this.maxMetaCreateTimeMs.setMilliseconds(maxMetaCreateTimeMs);
    }

    @Override
    public boolean isExport() {
        return this.export.getValue();
    }

    @Override
    public void setExport(final boolean export) {
        this.export.setValue(export);
    }

    @Override
    public void setRunAsUserView(final View view) {
        this.runAsUser.setWidget(view.asWidget());
    }

    public interface Binder extends UiBinder<Widget, ProcessorEditViewImpl> {

    }
}
