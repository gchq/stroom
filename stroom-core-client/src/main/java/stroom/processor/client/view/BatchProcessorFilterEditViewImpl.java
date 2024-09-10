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

package stroom.processor.client.view;

import stroom.item.client.SelectionBox;
import stroom.processor.client.presenter.BatchProcessorFilterEditPresenter.BatchProcessorFilterEditView;
import stroom.processor.client.presenter.BatchProcessorFilterEditUiHandlers;
import stroom.processor.shared.ProcessorFilterChange;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public final class BatchProcessorFilterEditViewImpl
        extends ViewWithUiHandlers<BatchProcessorFilterEditUiHandlers>
        implements BatchProcessorFilterEditView {

    private final Widget widget;

    @UiField
    SelectionBox<ProcessorFilterChange> documentPermissionChange;
    @UiField
    FormGroup userRefLabel;
    @UiField
    SimplePanel userRef;
    @UiField
    Button apply;

    @Inject
    public BatchProcessorFilterEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        documentPermissionChange.addItems(ProcessorFilterChange.LIST);
        documentPermissionChange.setValue(ProcessorFilterChange.ENABLE);
        update();
        apply.setEnabled(false);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        documentPermissionChange.focus();
    }

    @Override
    public ProcessorFilterChange getChange() {
        return documentPermissionChange.getValue();
    }

    @Override
    public void setUserRefSelection(final View view) {
        userRef.setWidget(view.asWidget());
    }

    private void update() {
        userRefLabel.setVisible(false);
        userRef.setVisible(false);

        switch (documentPermissionChange.getValue()) {
            case SET_RUN_AS_USER: {
                userRefLabel.setVisible(true);
                userRef.setVisible(true);
                break;
            }
            default:
        }
    }

    @Override
    public void setApplyEnabled(final boolean enabled) {
        apply.setEnabled(enabled);
    }

    @UiHandler("documentPermissionChange")
    public void onDocumentPermissionChange(final ValueChangeEvent<ProcessorFilterChange> e) {
        update();
        getUiHandlers().validate();
    }

    @UiHandler("apply")
    public void onApply(final ClickEvent e) {
        getUiHandlers().apply(apply);
    }

    public interface Binder extends UiBinder<Widget, BatchProcessorFilterEditViewImpl> {

    }
}
