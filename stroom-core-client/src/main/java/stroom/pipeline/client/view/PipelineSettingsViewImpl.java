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

package stroom.pipeline.client.view;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.item.client.ItemListBox;
import stroom.pipeline.client.presenter.PipelineSettingsPresenter.PipelineSettingsView;
import stroom.pipeline.client.presenter.PipelineSettingsUiHandlers;
import stroom.pipeline.shared.PipelineEntity;
import stroom.util.shared.HasReadOnly;

public class PipelineSettingsViewImpl extends ViewWithUiHandlers<PipelineSettingsUiHandlers>
        implements PipelineSettingsView, HasReadOnly {
    private final Widget widget;
    @UiField
    TextArea description;

    @Inject
    public PipelineSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getDescription() {
        return description.getText();
    }

    @Override
    public void setDescription(final String description) {
        this.description.setText(description);
    }

    @UiHandler("description")
    void onDescriptionKeyDown(final KeyDownEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().setDirty(true);
        }
    }

    @Override
    public boolean isReadOnly() {
        return description.isEnabled();
    }

    @Override
    public void setReadOnly(final boolean readOnly) {
        description.setEnabled(!readOnly);
    }

    public interface Binder extends UiBinder<Widget, PipelineSettingsViewImpl> {
    }
}
