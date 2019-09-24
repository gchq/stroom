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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.annotation.client.AnnotationEditPresenter.AnnotationEditView;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.SvgButton;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public class AnnotationEditViewImpl extends ViewWithUiHandlers<AnnotationEditUiHandlers> implements AnnotationEditView {
    public interface Binder extends UiBinder<Widget, AnnotationEditViewImpl> {
    }

    @UiField
    Label statusLabel;
    @UiField(provided = true)
    SvgButton statusIcon;
    @UiField
    SimplePanel currentStatusContainer;
    @UiField
    Label assignedToLabel;
    @UiField(provided = true)
    SvgButton assignedToIcon;
    @UiField
    FlowPanel assignYourselfContainer;
    @UiField
    FlowPanel currentAssignedToContainer;
    @UiField
    Label assignedTo;
    @UiField
    Label assignYourself;
    @UiField
    Button create;
    @UiField
    TextArea comment;
    @UiField
    ResizeSimplePanel history;


//    @UiField
//    FlowPanel statusSelection;
//    @UiField
//    FlowPanel assignedToSelection;

//    @UiField
//    Label title;
//    @UiField
//    Label createdBy;
//    @UiField
//    Label createdOn;
//    @UiField
//    Label status;
//    @UiField
//    Label assignedTo;
//    @UiField
//    ResizeSimplePanel history;
//    @UiField
//    ResizeSimplePanel comment;

    private final Widget widget;

    @Inject
    public AnnotationEditViewImpl(final Binder binder) {
        statusIcon = SvgButton.create(SvgPresets.SETTINGS);
        assignedToIcon = SvgButton.create(SvgPresets.SETTINGS);
        widget = binder.createAndBindUi(this);

//        statusSelection.addDomHandler(e -> {
//            if (getUiHandlers() != null) {
//                getUiHandlers().showStatusChooser(e);
//            }
//        }, MouseDownEvent.getType());
    }

//    @Override
//    public String getTitle() {
//        return null;
//    }
//
//    @Override
//    public void setTitle(final String title) {
//
//    }
//
//    @Override
//    public String getCreatedBy() {
//        return null;
//    }
//
//    @Override
//    public void setCreateUser(final String createdBy) {
//
//    }
//
//    @Override
//    public String getCreatedOn() {
//        return null;
//    }
//
//    @Override
//    public void setCreateTime(final String createdOn) {
//
//    }
//
//    @Override
//    public String getStatus() {
//        return null;
//    }

    @Override
    public void setStatus(final String status) {
        final Label label = new Label(status, false);
        this.currentStatusContainer.setWidget(label);
    }

//    @Override
//    public String getAssignedTo() {
//        return null;
//    }

    @Override
    public void setAssignedTo(final String assignedTo) {
        if (assignedTo == null) {
            currentAssignedToContainer.setVisible(false);
            assignYourselfContainer.setVisible(true);
        } else {
            currentAssignedToContainer.setVisible(true);
            assignYourselfContainer.setVisible(false);
            this.assignedTo.setText(assignedTo);
        }
    }

    @Override
    public void setHistoryView(final Widget view) {
        history.setWidget(view);
    }

//    @Override
//    public void setCommentView(final View view) {
//
//    }

    @Override
    public String getComment() {
        return this.comment.getText();
    }



    //    @Override
//    public String getTitle() {
//        return title.getText();
//    }
//
//    @Override
//    public void setTitle(final String title) {
//        this.title.setText(title);
//    }
//
//    @Override
//    public String getCreatedBy() {
//        return createdBy.getText();
//    }
//
//    @Override
//    public void setCreatedBy(final String createdBy) {
//        this.createdBy.setText(createdBy);
//    }
//
//    @Override
//    public String getCreatedOn() {
//        return createdOn.getText();
//    }
//
//    @Override
//    public void setCreatedOn(final String createdOn) {
//        this.createdOn.setText(createdOn);
//    }
//
//    @Override
//    public String getStatus() {
//        return status.getText();
//    }
//
//    @Override
//    public void setStatus(final String status) {
//        this.status.setText(status);
//    }
//
//    @Override
//    public String getAssignedTo() {
//        return assignedTo.getText();
//    }
//
//    @Override
//    public void setAssignedTo(final String assignedTo) {
//        this.assignedTo.setText(assignedTo);
//    }
//
//    @Override
//    public void setHistoryView(final View view) {
//
//    }
//
//    @Override
//    public void setCommentView(final View view) {
//
//    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @UiHandler("statusIcon")
    public void onStatusIcon(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showStatusChooser(statusLabel.getElement());
        }
    }

    @UiHandler("statusLabel")
    public void onStatusLabel(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showStatusChooser(statusLabel.getElement());
        }
    }

    @UiHandler("assignedToIcon")
    public void onAssignedToIcon(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showAssignedToChooser(assignedToLabel.getElement());
        }
    }

    @UiHandler("assignedToLabel")
    public void onAssignedToLabel(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showAssignedToChooser(assignedToLabel.getElement());
        }
    }

    @UiHandler("assignYourself")
    public void onAssignedYourself(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().assignYourself();
        }
    }

    @UiHandler("create")
    public void onCreate(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().create();
        }
    }
}
