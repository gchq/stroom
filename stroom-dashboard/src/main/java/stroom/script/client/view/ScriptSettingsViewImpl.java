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

package stroom.script.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.script.client.presenter.ScriptSettingsPresenter.ScriptSettingsView;
import stroom.util.shared.HasReadOnly;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public class ScriptSettingsViewImpl extends ViewImpl implements ScriptSettingsView, HasReadOnly {
    public interface Binder extends UiBinder<Widget, ScriptSettingsViewImpl> {
    }

    private final Widget widget;

    @UiField
    TextArea description;
    @UiField
    ResizeSimplePanel dependencies;

    @Inject
    public ScriptSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public TextArea getDescription() {
        return description;
    }

    @Override
    public boolean isReadOnly() {
        return description.isEnabled();
    }

    @Override
    public void setReadOnly(final boolean readOnly) {
        description.setEnabled(!readOnly);
    }

    @Override
    public void setDependencyList(final View view) {
        dependencies.setWidget(view.asWidget());
    }
}
