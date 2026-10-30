/*
 * Copyright 2019 Crown Copyright
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

package stroom.annotation.client.component;

import stroom.annotation.client.AssignYourself;
import stroom.annotation.client.Lozenge;
import stroom.annotation.client.component.SettingBlock;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationTag;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.Html;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.BiConsumer;

/// An annotation's Annotation tab: its title and subject, its history with a box to add a comment
/// to it, and its settings (status, who it is assigned to, labels, collections and retention
/// period), each of which opens a chooser when selected. Read only, everything can be read and
/// copied but nothing changed.
public class AnnotationEditor extends Component {

    /// Shows that a field has no value ("None", "Nobody") in the muted text colour.
    private static final String EMPTY_VALUE_STYLE = "annotation-setting__value--empty";

    private static final Binder BINDER = GWT.create(Binder.class);

    private boolean assignYourselfEnabled;

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
    LinkButton assignYourself;
    @UiField
    Html labels;
    @UiField
    Html collections;
    @UiField
    Label retentionPeriod;
    @UiField
    Button commentButton;
    @UiField
    Button create;
    @UiField
    TextArea comment;
    @UiField
    FlowPanel historyContainer;
    @UiField
    AnnotationHistory history;
    @UiField
    Button copyHistory;
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
    @UiField
    SettingBlock titleBlock;
    @UiField
    SettingBlock subjectBlock;

    private BiConsumer<AnnotationEditAction, Element> onAction = (action, element) -> {
    };
    private Runnable onTitleChange = () -> {
    };
    private Runnable onSubjectChange = () -> {
    };

    /// Makes the tab, with no annotation.
    public AnnotationEditor() {
        initWidget(BINDER.createAndBindUi(this));
        titleTextBox.addBlurHandler(this::onTitleBlur);
        titleTextBox.addKeyDownHandler(this::onTitleReturn);
        subjectTextBox.addBlurHandler(this::onSubjectBlur);
        subjectTextBox.addKeyDownHandler(this::onSubjectReturn);
        titleBlock.addClickHandler(this::onTitleBlock);
        subjectBlock.addClickHandler(this::onSubjectBlock);
        statusBlock.addClickHandler(this::onStatusBlock);
        assignedToBlock.addClickHandler(this::onAssignedToBlock);
        annotationLabelBlock.addClickHandler(this::onAnnotationLabelBlock);
        annotationCollectionBlock.addClickHandler(this::onAnnotationCollectionBlock);
        annotationRetentionPeriodBlock.addClickHandler(this::onAnnotationRetentionPeriodBlock);
        create.setOnClick(this::onCreate);
        commentButton.setOnClick(this::onCommentButton);
        delete.setOnClick(this::onDelete);
        // The setting around it opens its chooser on a click: this click (or Enter or Space) is the
        // button's own
        assignYourself.addClickHandler(ClickEvent::stopPropagation);
        assignYourself.setOnClick(() -> onAction.accept(AnnotationEditAction.ASSIGN_YOURSELF,
                assignYourself.getElement()));
        copyHistory.setOnClick(() -> onAction.accept(AnnotationEditAction.COPY_HISTORY, copyHistory.getElement()));

        setAnnotationTitle(null);
        setSubject(null);
        setStatus(null);
    }

    /// @param id The annotation's id, shown as `#<id>`.
    public void setId(final long id) {
        annotationId.setText("#" + id);
    }

    /// @return The title, as edited.
    public String getAnnotationTitle() {
        return this.titleTextBox.getText();
    }

    /// @param title The title, also the box's tooltip, for when it is too long to show; `null` for
    ///              none.
    public void setAnnotationTitle(final String title) {
        if (title == null || title.isEmpty()) {
            this.titleTextBox.setText("");
            this.titleTextBox.setTitle("");
        } else {
            this.titleTextBox.setText(title);
            this.titleTextBox.setTitle(title);
        }
    }

    /// @return The subject, as edited.
    public String getSubject() {
        return this.subjectTextBox.getText();
    }

    /// @param subject The subject, also the box's tooltip; `null` for none.
    public void setSubject(final String subject) {
        if (subject == null || subject.isEmpty()) {
            this.subjectTextBox.setText("");
            this.subjectTextBox.setTitle("");
        } else {
            this.subjectTextBox.setText(subject);
            this.subjectTextBox.setTitle(subject);
        }
    }

    /// @param status The status, or `null` for none.
    public void setStatus(final AnnotationTag status) {
        if (status == null) {
            this.status.setText("None");
            this.status.addStyleName(EMPTY_VALUE_STYLE);
        } else {
            this.status.setText(status.getName());
            this.status.removeStyleName(EMPTY_VALUE_STYLE);
        }
    }

    /// @param assignedTo Who it is assigned to, or `null` for nobody.
    public void setAssignedTo(final UserRef assignedTo) {
        if (assignedTo == null) {
            this.assignedTo.setText("Nobody");
            this.assignedTo.addStyleName(EMPTY_VALUE_STYLE);
        } else {
            this.assignedTo.setText(assignedTo.toDisplayString());
            this.assignedTo.removeStyleName(EMPTY_VALUE_STYLE);
        }
    }

    /// Enables 'Assign Yourself', or disables it, saying why, when the annotation is already
    /// assigned to the current user.
    ///
    /// @param enabled Whether it is enabled.
    public void setAssignYourselfEnabled(final boolean enabled) {
        this.assignYourselfEnabled = enabled;
        updateAssignYourself();
    }

    /// @param labels The labels, as lozenges, or none.
    public void setLabels(final List<AnnotationTag> labels) {
        setTags(this.labels, labels);
    }

    /// @param collections The collections, as lozenges, or none.
    public void setCollections(final List<AnnotationTag> collections) {
        setTags(this.collections, collections);
    }

    private static void setTags(final Html html, final List<AnnotationTag> tags) {
        if (tags == null || tags.isEmpty()) {
            html.setText("None");
            html.addStyleName(EMPTY_VALUE_STYLE);
        } else {
            final HtmlBuilder htmlBuilder = new HtmlBuilder();
            for (final AnnotationTag annotationTag : tags) {
                htmlBuilder.div(div -> Lozenge.append(div, annotationTag),
                        Attribute.className("annotation-editor__tag"));
            }
            html.setHTML(htmlBuilder.toSafeHtml());
            html.removeStyleName(EMPTY_VALUE_STYLE);
        }
    }

    /// @param retentionPeriod The retention period, as text, or `null` or blank for none.
    public void setRetentionPeriod(final String retentionPeriod) {
        if (retentionPeriod == null || retentionPeriod.trim().isEmpty()) {
            this.retentionPeriod.setText("None");
            this.retentionPeriod.addStyleName(EMPTY_VALUE_STYLE);
        } else {
            this.retentionPeriod.setText(retentionPeriod);
            this.retentionPeriod.removeStyleName(EMPTY_VALUE_STYLE);
        }
    }

    /// Shows the history, scrolled to its end.
    ///
    /// @param entries  The history's entries, oldest first.
    /// @param nowMs    The time now, that the entries' times are shown relative to.
    /// @param readOnly Whether the entries can't be changed, so have no actions buttons.
    public void setHistory(final List<AnnotationEntry> entries, final long nowMs, final boolean readOnly) {
        history.setEntries(entries, nowMs, readOnly);
        historyContainer.setVisible(true);
        // Scroll the history to the bottom after update.
        Scheduler.get().scheduleDeferred(() ->
                history.getElement().setScrollTop(history.getElement().getScrollHeight()));
    }

    /// @return The history, for its controller to follow its links and show its entries' menus.
    public AnnotationHistory getHistory() {
        return history;
    }

    /// @return The comment, as entered.
    public String getComment() {
        return this.comment.getText();
    }

    /// @param comment The comment.
    public void setComment(final String comment) {
        this.comment.setText(comment);
    }

    /// @param text The text of the button that adds the comment.
    public void setButtonText(final String text) {
        create.setText(text);
    }

    /// Puts the focus on the title if there is none, else on the comment.
    public void focus() {
        if (titleTextBox.getText() == null || titleTextBox.getText().isEmpty()) {
            titleTextBox.setFocus(true);
        } else {
            comment.setFocus(true);
        }
    }

    /// @param onAction Given what was pressed and its element (e.g. to show a chooser beside).
    public void setOnAction(final BiConsumer<AnnotationEditAction, Element> onAction) {
        this.onAction = onAction;
    }

    /// @param onTitleChange Run when the user has finished editing the title.
    public void setOnTitleChange(final Runnable onTitleChange) {
        this.onTitleChange = onTitleChange;
    }

    /// @param onSubjectChange Run when the user has finished editing the subject.
    public void setOnSubjectChange(final Runnable onSubjectChange) {
        this.onSubjectChange = onSubjectChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        titleTextBox.setReadOnly(readOnly);
        subjectTextBox.setReadOnly(readOnly);
        comment.setReadOnly(readOnly);
        commentButton.setEnabled(!readOnly);
        create.setEnabled(!readOnly);
        delete.setEnabled(!readOnly);
        statusBlock.setReadOnly(readOnly);
        assignedToBlock.setReadOnly(readOnly);
        annotationLabelBlock.setReadOnly(readOnly);
        annotationCollectionBlock.setReadOnly(readOnly);
        annotationRetentionPeriodBlock.setReadOnly(readOnly);
        updateAssignYourself();
    }

    private void updateAssignYourself() {
        AssignYourself.setState(assignYourself, !assignYourselfEnabled, isReadOnly());
    }

    private void finishTitleEdit() {
        setAnnotationTitle(titleTextBox.getText());
        onTitleChange.run();
    }

    private void finishSubjectEdit() {
        setSubject(subjectTextBox.getText());
        onSubjectChange.run();
    }

    private void onTitleBlur(final BlurEvent e) {
        finishTitleEdit();
    }

    private void onTitleReturn(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            finishTitleEdit();
            Scheduler.get().scheduleDeferred(() -> subjectTextBox.setFocus(true));
        }
    }

    private void onSubjectBlur(final BlurEvent e) {
        finishSubjectEdit();
    }

    private void onSubjectReturn(final KeyDownEvent e) {
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            finishSubjectEdit();
            Scheduler.get().scheduleDeferred(() -> comment.setFocus(true));
        }
    }

    private void onTitleBlock(final ClickEvent e) {
        titleTextBox.setFocus(true);
    }

    private void onSubjectBlock(final ClickEvent e) {
        subjectTextBox.setFocus(true);
    }

    private void onStatusBlock(final ClickEvent e) {
        onAction.accept(AnnotationEditAction.STATUS, statusBlock.getElement());
    }

    private void onAssignedToBlock(final ClickEvent e) {
        onAction.accept(AnnotationEditAction.ASSIGNED_TO, assignedToBlock.getElement());
    }

    private void onAnnotationLabelBlock(final ClickEvent e) {
        onAction.accept(AnnotationEditAction.LABELS, annotationLabelBlock.getElement());
    }

    private void onAnnotationCollectionBlock(final ClickEvent e) {
        onAction.accept(AnnotationEditAction.COLLECTIONS, annotationCollectionBlock.getElement());
    }

    private void onAnnotationRetentionPeriodBlock(final ClickEvent e) {
        onAction.accept(AnnotationEditAction.RETENTION_PERIOD, annotationRetentionPeriodBlock.getElement());
    }

    private void onCreate() {
        onAction.accept(AnnotationEditAction.CREATE, create.getElement());
    }

    private void onCommentButton() {
        if (commentButton.isEnabled()) {
            onAction.accept(AnnotationEditAction.CHOOSE_COMMENT, commentButton.getElement());
        }
    }

    private void onDelete() {
        onAction.accept(AnnotationEditAction.DELETE, delete.getElement());
    }


    // --------------------------------------------------------------------------------


    /// What can be pressed on the tab.
    public enum AnnotationEditAction {
        /// The status, to choose another.
        STATUS,
        /// Who it is assigned to, to choose another.
        ASSIGNED_TO,
        /// Assign Yourself.
        ASSIGN_YOURSELF,
        /// The labels, to add or remove some.
        LABELS,
        /// The collections, to add or remove some.
        COLLECTIONS,
        /// The retention period, to change it.
        RETENTION_PERIOD,
        /// Choose Comment, to add a standard comment's text to the comment.
        CHOOSE_COMMENT,
        /// The button that adds the comment to the history.
        CREATE,
        /// Copy History.
        COPY_HISTORY,
        /// Delete Annotation.
        DELETE
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnnotationEditor> {

    }
}
