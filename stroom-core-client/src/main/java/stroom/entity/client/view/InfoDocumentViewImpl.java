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

package stroom.entity.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.entity.client.presenter.InfoDocumentPresenter;

public class InfoDocumentViewImpl extends ViewImpl implements InfoDocumentPresenter.InfoDocumentView {
    private final Widget widget;

    @UiField
    Label docType;
    @UiField
    TextBox docUuid;
    @UiField
    Label docName;
    @UiField
    Label createdUser;
    @UiField
    Label createdTime;
    @UiField
    Label updatedUser;
    @UiField
    Label updatedTime;

    @Inject
    public InfoDocumentViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public HasText getDocType() {
        return docType;
    }

    @Override
    public HasText getDocUuid() {
        docUuid.setReadOnly(true);
        return docUuid;
    }

    @Override
    public HasText getDocName() {
        return docName;
    }

    @Override
    public HasText getCreatedUser() {
        return createdUser;
    }

    @Override
    public HasText getCreatedTime() {
        return createdTime;
    }

    @Override
    public HasText getUpdatedUser() {
        return updatedUser;
    }

    @Override
    public HasText getUpdatedTime() {
        return updatedTime;
    }

    public interface Binder extends UiBinder<Widget, InfoDocumentViewImpl> {
    }
}
