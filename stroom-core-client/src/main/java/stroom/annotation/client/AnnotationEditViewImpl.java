/*
 * Copyright 2018 Crown Copyright
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

package stroom.annotation.client;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.annotation.client.AnnotationEditPresenter.AnnotationEditView;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public class AnnotationEditViewImpl extends ViewWithUiHandlers implements AnnotationEditView {
    public interface Binder extends UiBinder<Widget, AnnotationEditViewImpl> {
    }

    @UiField
    Label title;
    @UiField
    Label createdBy;
    @UiField
    Label createdOn;
    @UiField
    Label status;
    @UiField
    Label assignedTo;
    @UiField
    ResizeSimplePanel history;
    @UiField
    ResizeSimplePanel comment;

    private final Widget widget;

    @Inject
    public AnnotationEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public String getTitle() {
        return title.getText();
    }

    @Override
    public void setTitle(final String title) {
        this.title.setText(title);
    }

    @Override
    public String getCreatedBy() {
        return createdBy.getText();
    }

    @Override
    public void setCreatedBy(final String createdBy) {
        this.createdBy.setText(createdBy);
    }

    @Override
    public String getCreatedOn() {
        return createdOn.getText();
    }

    @Override
    public void setCreatedOn(final String createdOn) {
        this.createdOn.setText(createdOn);
    }

    @Override
    public String getStatus() {
        return status.getText();
    }

    @Override
    public void setStatus(final String status) {
        this.status.setText(status);
    }

    @Override
    public String getAssignedTo() {
        return assignedTo.getText();
    }

    @Override
    public void setAssignedTo(final String assignedTo) {
        this.assignedTo.setText(assignedTo);
    }

    @Override
    public void setHistoryView(final View view) {

    }

    @Override
    public void setCommentView(final View view) {

    }

    @Override
    public Widget asWidget() {
        return widget;
    }

}
