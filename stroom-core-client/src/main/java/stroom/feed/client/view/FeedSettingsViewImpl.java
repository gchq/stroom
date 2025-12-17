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

package stroom.feed.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.feed.client.presenter.FeedSettingsPresenter.FeedSettingsView;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.item.client.SelectionBox;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class FeedSettingsViewImpl extends ViewImpl implements FeedSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    TextBox classification;
    @UiField
    SelectionBox<String> dataEncoding;
    @UiField
    SelectionBox<String> contextEncoding;
    @UiField
    SelectionBox<FeedDoc.FeedStatus> feedStatus;
    @UiField
    SelectionBox<String> receivedType;
    @UiField
    SelectionBox<String> dataFormat;
    @UiField
    SelectionBox<String> contextFormat;
    @UiField
    TextBox schema;
    @UiField
    TextBox schemaVersion;
    @UiField
    CustomCheckBox reference;
    @UiField
    SelectionBox<String> volumeGroup;

    @Inject
    public FeedSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public TextBox getClassification() {
        return classification;
    }

    @Override
    public CustomCheckBox getReference() {
        return reference;
    }

    @Override
    public SelectionBox<String> getDataEncoding() {
        return dataEncoding;
    }

    @Override
    public SelectionBox<String> getContextEncoding() {
        return contextEncoding;
    }

    @Override
    public SelectionBox<String> getReceivedType() {
        return receivedType;
    }

    @Override
    public SelectionBox<FeedStatus> getFeedStatus() {
        return feedStatus;
    }

    @Override
    public SelectionBox<String> getDataFormat() {
        return dataFormat;
    }

    @Override
    public SelectionBox<String> getContextFormat() {
        return contextFormat;
    }

    @Override
    public TextBox getSchema() {
        return schema;
    }

    @Override
    public TextBox getSchemaVersion() {
        return schemaVersion;
    }

    @Override
    public SelectionBox<String> getVolumeGroup() {
        return volumeGroup;
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        classification.setEnabled(!readOnly);
        dataEncoding.setEnabled(!readOnly);
        contextEncoding.setEnabled(!readOnly);
        receivedType.setEnabled(!readOnly);
        feedStatus.setEnabled(!readOnly);
        reference.setEnabled(!readOnly);
        volumeGroup.setEnabled(!readOnly);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, FeedSettingsViewImpl> {

    }
}
