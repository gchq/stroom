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

import stroom.annotation.client.AnnotationEditPresenter.AnnotationEditView;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AnnotationEditViewImpl extends ViewWithUiHandlers<AnnotationEditUiHandlers> implements AnnotationEditView {

    private final Widget widget;
    @UiField
    TextBox titleTextBox;
    @UiField
    TextBox subjectTextBox;
    @UiField
    Label statusLabel;
    @UiField
    InlineSvgButton statusIcon;
    @UiField
    Label status;
    @UiField
    Label assignedToLabel;
    @UiField
    InlineSvgButton assignedToIcon;
    @UiField
    Label assignedTo;
    @UiField
    Label assignYourself;
    @UiField
    FlowPanel commentFlowPanel;
    @UiField
    Label commentLabel;
    @UiField
    InlineSvgButton commentIcon;
    @UiField
    Button create;
    @UiField
    TextArea comment;
    @UiField
    SimplePanel history;
    @UiField
    Label showLinkedEvents;

    @Inject
    public AnnotationEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        titleTextBox.getElement().setAttribute("placeholder", "Title");
        subjectTextBox.getElement().setAttribute("placeholder", "Subject");
        create.setIcon(SvgImage.ADD);

        statusIcon.setSvg(SvgImage.ARROW_DOWN);
        assignedToIcon.setSvg(SvgImage.ARROW_DOWN);
        commentIcon.setSvg(SvgImage.ARROW_DOWN);

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
    public void setAssignedTo(final UserRef assignedTo) {
        if (assignedTo == null) {
            this.assignedTo.setText("Nobody");
            this.assignedTo.getElement().getStyle().setOpacity(0.5);
        } else {
            this.assignedTo.setText(assignedTo.toDisplayString());
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
    public void focus() {
        if (titleTextBox.getText() == null || titleTextBox.getText().isEmpty()) {
            titleTextBox.setFocus(true);
        } else {
            comment.setFocus(true);
        }
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

    @UiHandler("titleTextBox")
    public void onTitleBlur(final BlurEvent e) {
        finishTitleEdit();
    }

//    @UiHandler("titleTextBox")
//    public void onTitleFocus(final FocusEvent e) {
//        startTitleEdit();
//    }

    @UiHandler("titleTextBox")
    public void onTitleReturn(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            finishTitleEdit();
            Scheduler.get().scheduleDeferred(() -> subjectTextBox.setFocus(true));
        }
    }

    @UiHandler("subjectTextBox")
    public void onSubjectBlur(final BlurEvent e) {
        finishSubjectEdit();
    }

//    @UiHandler("subjectTextBox")
//    public void onSubjectFocus(final FocusEvent e) {
//        startSubjectEdit();
//    }

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
        if (getUiHandlers() != null && commentIcon.isEnabled()) {
            getUiHandlers().showCommentChooser(commentLabel.getElement());
        }
    }

    @UiHandler("commentIcon")
    public void onCommentIcon(final ClickEvent e) {
        if (getUiHandlers() != null && commentIcon.isEnabled()) {
            getUiHandlers().showCommentChooser(commentLabel.getElement());
        }
    }

    @UiHandler("showLinkedEvents")
    public void onShowLinkedEvents(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showLinkedEvents();
        }
    }

    @Override
    public void setHasCommentValues(final boolean hasCommentValues) {
        // There may not be any preconfigured standard comments so enable/disable the QF dropdown
        // accordingly
        commentIcon.setEnabled(hasCommentValues);
        if (hasCommentValues) {
            commentLabel.addStyleName("clickable");
            commentFlowPanel.addStyleName("clickable");
        } else {
            commentLabel.removeStyleName("clickable");
            commentFlowPanel.removeStyleName("clickable");
        }
    }

    public interface Binder extends UiBinder<Widget, AnnotationEditViewImpl> {

    }
}
