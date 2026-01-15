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

package stroom.data.client.view;

import stroom.data.client.presenter.DataUploadPresenter.DataUploadView;
import stroom.item.client.SelectionBox;
import stroom.preferences.client.UserPreferencesManager;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.form.client.CustomFileUpload;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class DataUploadViewImpl extends ViewImpl implements DataUploadView {

    private final Widget widget;

    @UiField
    SelectionBox<String> type;
    @UiField
    CustomFileUpload fileUpload;
    @UiField
    MyDateBox effective;
    @UiField
    TextArea metaData;

    @Inject
    public DataUploadViewImpl(final Binder binder,
                              final UserPreferencesManager userPreferencesManager) {
        widget = binder.createAndBindUi(this);
        effective.setUtc(userPreferencesManager.isUtc());
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
    public CustomFileUpload getFileUpload() {
        return fileUpload;
    }

    @Override
    public Long getEffectiveDate() {
        return effective.getMilliseconds();
    }

    @Override
    public SelectionBox<String> getType() {
        return type;
    }

    @Override
    public void setType(final String type) {
        this.type.setValue(type);
    }

    @Override
    public String getMetaData() {
        return metaData.getText();
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, DataUploadViewImpl> {

    }
}
