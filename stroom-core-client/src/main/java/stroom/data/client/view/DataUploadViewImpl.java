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

package stroom.data.client.view;

import stroom.data.client.presenter.DataTypeUiManager;
import stroom.data.client.presenter.DataUploadPresenter.DataUploadView;
import stroom.data.shared.StreamTypeNames;
import stroom.item.client.StringListBox;
import stroom.preferences.client.UserPreferencesManager;
import stroom.widget.customdatebox.client.MyDateBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class DataUploadViewImpl extends ViewImpl implements DataUploadView {

    private final Widget widget;

    @UiField(provided = true)
    StringListBox type;
    @UiField
    FileUpload fileUpload;
    @UiField(provided = true)
    MyDateBox effective;
    @UiField
    FormPanel form;
    @UiField
    TextArea metaData;

    @Inject
    public DataUploadViewImpl(final Binder binder,
                              final DataTypeUiManager dataTypeUiManager,
                              final UserPreferencesManager userPreferencesManager) {
        effective = new MyDateBox(userPreferencesManager.isUtc());
        type = new StringListBox();

        dataTypeUiManager.getTypes(list -> {
            type.clear();
            if (list != null && !list.isEmpty()) {
                type.addItems(list);
                // Default to raw events
                type.setSelected(StreamTypeNames.RAW_EVENTS);
            }
        });
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        metaData.setFocus(true);
    }

    @Override
    public FormPanel getForm() {
        return form;
    }

    @Override
    public FileUpload getFileUpload() {
        return fileUpload;
    }

    @Override
    public Long getEffectiveDate() {
        return effective.getMilliseconds();
    }

    @Override
    public StringListBox getType() {
        return type;
    }

    @Override
    public String getMetaData() {
        return metaData.getText();
    }

    public interface Binder extends UiBinder<Widget, DataUploadViewImpl> {

    }
}
