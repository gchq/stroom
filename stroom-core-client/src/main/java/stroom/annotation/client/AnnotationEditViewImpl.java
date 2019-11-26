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

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.annotation.client.AnnotationEditPresenter.AnnotationEditView;
import stroom.svg.client.SvgPreset;
import stroom.widget.button.client.SvgButton;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public class AnnotationEditViewImpl extends ViewWithUiHandlers<AnnotationEditUiHandlers> implements AnnotationEditView {
    public interface Binder extends UiBinder<Widget, AnnotationEditViewImpl> {
    }

    private static final SvgPreset CHANGE_STATUS = new SvgPreset("images/tree-open.svg", "Change Status", true);
    private static final SvgPreset CHANGE_ASSIGNED_TO = new SvgPreset("images/tree-open.svg", "Change Assigned To", true);
    private static final SvgPreset CHOOSE_COMMENT = new SvgPreset("images/tree-open.svg", "Choose Comment", true);

    @UiField
    TextBox titleTextBox;
    @UiField
    TextBox subjectTextBox;
    @UiField
    Label statusLabel;
    @UiField(provided = true)
    SvgButton statusIcon;
    @UiField
    Label status;
    @UiField
    Label assignedToLabel;
    @UiField(provided = true)
    SvgButton assignedToIcon;
    @UiField
    Label assignedTo;
    @UiField
    Label assignYourself;
    @UiField
    Label commentLabel;
    @UiField(provided = true)
    SvgButton commentIcon;
    @UiField
    Button create;
    @UiField
    TextArea comment;
    @UiField
    ResizeSimplePanel history;

    private final Widget widget;

    @Inject
    public AnnotationEditViewImpl(final Binder binder) {
        statusIcon = SvgButton.create(CHANGE_STATUS);
        assignedToIcon = SvgButton.create(CHANGE_ASSIGNED_TO);
        commentIcon = SvgButton.create(CHOOSE_COMMENT);
        widget = binder.createAndBindUi(this);
        titleTextBox.getElement().setAttribute("placeholder", "Title");
        subjectTextBox.getElement().setAttribute("placeholder", "Subject");

        setTitle(null);
        setSubject(null);
        setStatus(null);
    }

    @Override
    public String getTitle() {
        return this.titleTextBox.getText();
    }

    @Override
    public void setTitle(final String title) {
        if (title == null || title.isEmpty()) {
            this.titleTextBox.setText("");
            this.titleTextBox.setTitle("");
        } else {
            this.titleTextBox.setText(title);
            this.titleTextBox.setTitle(title);
        }
    }

    @Override
    public String getSubject() {
        return this.subjectTextBox.getText();
    }

    @Override
    public void setSubject(final String subject) {
        if (subject == null || subject.isEmpty()) {
            this.subjectTextBox.setText("");
            this.subjectTextBox.setTitle("");
        } else {
            this.subjectTextBox.setText(subject);
            this.subjectTextBox.setTitle(subject);
        }
    }

    @Override
    public void setStatus(final String status) {
        if (status == null || status.trim().isEmpty()) {
            this.status.setText("None");
            this.status.getElement().getStyle().setOpacity(0.5);
        } else {
            this.status.setText(status);
            this.status.getElement().getStyle().setOpacity(1);
        }
    }

    @Override
    public void setAssignedTo(final String assignedTo) {
        if (assignedTo == null || assignedTo.trim().isEmpty()) {
            this.assignedTo.setText("Nobody");
            this.assignedTo.getElement().getStyle().setOpacity(0.5);
        } else {
            this.assignedTo.setText(assignedTo);
            this.assignedTo.getElement().getStyle().setOpacity(1);
        }
    }

    @Override
    public void setHistoryView(final Widget view) {
        history.setWidget(view);
    }

    @Override
    public String getComment() {
        return this.comment.getText();
    }

    @Override
    public void setComment(final String comment) {
        this.comment.setText(comment);
    }

    @Override
    public void setButtonText(final String text) {
        create.setText(text);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setAssignYourselfVisible(final boolean visible) {
        assignYourself.setVisible(visible);
    }

    @Override
    public void focusComment() {
        Scheduler.get().scheduleDeferred(() -> comment.setFocus(true));
    }

    private void finishTitleEdit() {
        setTitle(titleTextBox.getText());
        if (getUiHandlers() != null) {
            getUiHandlers().onTitleChange();
        }
    }

    private void finishSubjectEdit() {
        setSubject(subjectTextBox.getText());
        if (getUiHandlers() != null) {
            getUiHandlers().onSubjectChange();
        }
    }

//    @UiHandler("titleTextBox")
//    public void onTitleFocus(final FocusEvent e) {
//        startTitleEdit();
//    }

    @UiHandler("titleTextBox")
    public void onTitleBlur(final BlurEvent e) {
        finishTitleEdit();
    }

    @UiHandler("titleTextBox")
    public void onTitleReturn(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            finishTitleEdit();
            Scheduler.get().scheduleDeferred(() -> subjectTextBox.setFocus(true));
        }
    }

//    @UiHandler("subjectTextBox")
//    public void onSubjectFocus(final FocusEvent e) {
//        startSubjectEdit();
//    }

    @UiHandler("subjectTextBox")
    public void onSubjectBlur(final BlurEvent e) {
        finishSubjectEdit();
    }

    @UiHandler("subjectTextBox")
    public void onSubjectReturn(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            finishSubjectEdit();
            Scheduler.get().scheduleDeferred(() -> comment.setFocus(true));
        }
    }

    @UiHandler("statusLabel")
    public void onStatusLabel(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showStatusChooser(statusLabel.getElement());
        }
    }

    @UiHandler("status")
    public void onStatus(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showStatusChooser(statusLabel.getElement());
        }
    }

    @UiHandler("statusIcon")
    public void onStatusIcon(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showStatusChooser(statusLabel.getElement());
        }
    }

    @UiHandler("assignedToLabel")
    public void onAssignedToLabel(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showAssignedToChooser(assignedToLabel.getElement());
        }
    }

    @UiHandler("assignedTo")
    public void onAssignedTo(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showAssignedToChooser(assignedToLabel.getElement());
        }
    }

    @UiHandler("assignedToIcon")
    public void onAssignedToIcon(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showAssignedToChooser(assignedToLabel.getElement());
        }
    }

    @UiHandler("assignYourself")
    public void onAssignYourself(final ClickEvent e) {
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

    @UiHandler("commentLabel")
    public void onCommentLabel(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showCommentChooser(commentLabel.getElement());
        }
    }

    @UiHandler("commentIcon")
    public void onCommentIcon(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showCommentChooser(commentLabel.getElement());
        }
    }
}
