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

package stroom.streamstore.client.view;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.entity.client.EntityItemListBox;
import stroom.entity.shared.DocRefUtil;
import stroom.streamstore.client.presenter.StreamTypeUiManager;
import stroom.streamstore.client.presenter.StreamUploadPresenter.DataUploadView;
import stroom.streamstore.shared.StreamType;
import stroom.widget.customdatebox.client.MyDateBox;

public class StreamUploadViewImpl extends ViewImpl implements DataUploadView {
    private final Widget widget;
    @UiField
    Grid grid;
    @UiField(provided = true)
    EntityItemListBox streamType;
    @UiField
    FileUpload fileUpload;
    @UiField
    MyDateBox effective;
    @UiField
    FormPanel form;
    @UiField
    TextArea metaData;

    @Inject
    public StreamUploadViewImpl(final Binder binder, final StreamTypeUiManager streamTypeUiManager) {
        streamType = new EntityItemListBox("");

        for (final StreamType st : streamTypeUiManager.getRawStreamTypeList()) {
            streamType.addItem(DocRefUtil.create(st));
        }
        widget = binder.createAndBindUi(this);

        grid.getRowFormatter().getElement(0).getStyle().setHeight(100, Unit.PCT);
    }

    @Override
    public Widget asWidget() {
        return widget;
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
    public EntityItemListBox getStreamType() {
        return streamType;
    }

    @Override
    public String getMetaData() {
        return metaData.getText();
    }

    public interface Binder extends UiBinder<Widget, StreamUploadViewImpl> {
    }
}
