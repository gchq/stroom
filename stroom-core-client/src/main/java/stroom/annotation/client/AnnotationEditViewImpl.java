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

package stroom.annotation.client;

import stroom.annotation.client.AnnotationEditPresenter.AnnotationEditView;
import stroom.annotation.shared.AnnotationTag;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.Button;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public class AnnotationEditViewImpl extends ViewWithUiHandlers<AnnotationEditUiHandlers> implements AnnotationEditView {

    private final Widget widget;

    @UiField
    Label annotationId;
    @UiField
    TextBox titleTextBox;
    @UiField
    TextBox subjectTextBox;
    @UiField
    Label status;
    @UiField
    Label assignedTo;
    @UiField
    Label assignYourself;
    @UiField
    HTML labels;
    @UiField
    HTML collections;
    @UiField
    Label retentionPeriod;
    @UiField
    Button commentButton;
    @UiField
    Button create;
    @UiField
    TextArea comment;
    @UiField
    SimplePanel history;
    @UiField
    Button delete;

    @UiField
    SettingBlock statusBlock;
    @UiField
    SettingBlock assignedToBlock;
    @UiField
    SettingBlock annotationLabelBlock;
    @UiField
    SettingBlock annotationCollectionBlock;
    @UiField
    SettingBlock annotationRetentionPeriodBlock;

    @Inject
    public AnnotationEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        titleTextBox.getElement().setAttribute("placeholder", "Title");
        subjectTextBox.getElement().setAttribute("placeholder", "Subject");
        create.setIcon(SvgImage.ADD);
        delete.setIcon(SvgImage.DELETE);

        setTitle(null);
        setSubject(null);
        setStatus(null);
    }

    @Override
    public void setId(final long id) {
        annotationId.setText("#" + id);
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
    public void setStatus(final AnnotationTag status) {
        if (status == null) {
            this.status.setText("None");
            this.status.getElement().getStyle().setOpacity(0.5);
        } else {
            this.status.setText(status.getName());
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
    public void setLabels(final List<AnnotationTag> labels) {
        if (labels == null || labels.isEmpty()) {
            this.labels.setText("None");
            this.labels.getElement().getStyle().setOpacity(0.5);
        } else {
            final HtmlBuilder htmlBuilder = new HtmlBuilder();
            for (final AnnotationTag annotationTag : labels) {
                htmlBuilder.div(div -> Lozenge.append(div, annotationTag), Attribute.className("lozengeOuter"));
            }
            this.labels.setHTML(htmlBuilder.toSafeHtml());
            this.labels.getElement().getStyle().setOpacity(1);
        }
    }

    @Override
    public void setCollections(final List<AnnotationTag> collections) {
        if (collections == null || collections.isEmpty()) {
            this.collections.setText("None");
            this.collections.getElement().getStyle().setOpacity(0.5);
        } else {
            final HtmlBuilder htmlBuilder = new HtmlBuilder();
            for (final AnnotationTag annotationTag : collections) {
                htmlBuilder.div(div -> Lozenge.append(div, annotationTag), Attribute.className("lozengeOuter"));
            }
            this.collections.setHTML(htmlBuilder.toSafeHtml());
            this.collections.getElement().getStyle().setOpacity(1);
        }
    }

    @Override
    public void setRetentionPeriod(final String retentionPeriod) {
        if (retentionPeriod == null || retentionPeriod.trim().isEmpty()) {
            this.retentionPeriod.setText("None");
            this.retentionPeriod.getElement().getStyle().setOpacity(0.5);
        } else {
            this.retentionPeriod.setText(retentionPeriod);
            this.retentionPeriod.getElement().getStyle().setOpacity(1);
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

    @UiHandler("subjectTextBox")
    public void onSubjectReturn(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            finishSubjectEdit();
            Scheduler.get().scheduleDeferred(() -> comment.setFocus(true));
        }
    }

    @UiHandler("titleBlock")
    public void onTitleBlock(final ClickEvent e) {
        titleTextBox.setFocus(true);
    }

    @UiHandler("subjectBlock")
    public void onSubjectBlock(final ClickEvent e) {
        subjectTextBox.setFocus(true);
    }

    @UiHandler("statusBlock")
    public void onStatusBlock(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showStatusChooser(statusBlock.getElement());
        }
    }

//    @UiHandler("status")
//    public void onStatus(final ClickEvent e) {
//        if (getUiHandlers() != null) {
//            getUiHandlers().showStatusChooser(statusBlock.getElement());
//        }
//    }

    @UiHandler("assignedToBlock")
    public void onAssignedToBlock(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showAssignedToChooser(assignedToBlock.getElement());
        }
    }

//    @UiHandler("assignedTo")
//    public void onAssignedTo(final ClickEvent e) {
//        if (getUiHandlers() != null) {
//            getUiHandlers().showAssignedToChooser(assignedToBlock.getElement());
//        }
//    }

    @UiHandler("assignYourself")
    public void onAssignYourself(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().assignYourself();
        }
        e.stopPropagation();
    }

    @UiHandler("annotationLabelBlock")
    public void onAnnotationLabelBlock(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showLabelChooser(annotationLabelBlock.getElement());
        }
    }

//    @UiHandler("label")
//    public void onLabel(final ClickEvent e) {
//        if (getUiHandlers() != null) {
//            getUiHandlers().showLabelChooser(annotationLabelBlock.getElement());
//        }
//    }

    @UiHandler("annotationCollectionBlock")
    public void onAnnotationCollectionBlock(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showCollectionChooser(annotationCollectionBlock.getElement());
        }
    }

//    @UiHandler("collection")
//    public void onCollection(final ClickEvent e) {
//        if (getUiHandlers() != null) {
//            getUiHandlers().showCollectionChooser(annotationCollectionBlock.getElement());
//        }
//    }

    @UiHandler("annotationRetentionPeriodBlock")
    public void onAnnotationRetentionPeriodBlock(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showRetentionPeriodChooser(annotationRetentionPeriodBlock.getElement());
        }
    }

//    @UiHandler("retentionPeriod")
//    public void onRetentionPeriod(final ClickEvent e) {
//        if (getUiHandlers() != null) {
//            getUiHandlers().showRetentionPeriodChooser(annotationRetentionPeriodBlock.getElement());
//        }
//    }

    @UiHandler("create")
    public void onCreate(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().create();
        }
    }

    @UiHandler("commentButton")
    public void onCommentButton(final ClickEvent e) {
        if (getUiHandlers() != null && commentButton.isEnabled()) {
            getUiHandlers().showCommentChooser(commentButton.getElement());
        }
    }

    @UiHandler("delete")
    public void onDelete(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDelete();
        }
    }

    @Override
    public void setHasCommentValues(final boolean hasCommentValues) {
        commentButton.setVisible(hasCommentValues);
    }

    public interface Binder extends UiBinder<Widget, AnnotationEditViewImpl> {

    }
}
