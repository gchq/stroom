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

package stroom.visualisation.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.visualisation.client.presenter.VisualisationSettingsPresenter.VisualisationSettingsView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class VisualisationSettingsViewImpl extends ViewImpl
        implements VisualisationSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    TextBox functionName;
    @UiField
    SimplePanel script;
    @UiField
    SimplePanel settings;

    @Inject
    public VisualisationSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public TextBox getFunctionName() {
        return functionName;
    }

    @Override
    public void setScriptView(final View view) {
        script.setWidget(view.asWidget());
    }

    @Override
    public void setSettingsView(final View view) {
        settings.setWidget(view.asWidget());
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        functionName.setEnabled(!readOnly);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, VisualisationSettingsViewImpl> {

    }
}
