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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.client.AnnotationEditPresenter.AnnotationEditView;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationEntryType;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagFields;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.ChangeAssignedTo;
import stroom.annotation.shared.ChangeComment;
import stroom.annotation.shared.ChangeRetentionPeriod;
import stroom.annotation.shared.ChangeSubject;
import stroom.annotation.shared.ChangeTitle;
import stroom.annotation.shared.EntryValue;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.SetTag;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.annotation.shared.UserRefEntryValue;
import stroom.content.client.event.CloseContentTabEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.shared.ExpressionCriteria;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.hyperlink.client.HyperlinkType;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.UserRefPopupPresenter;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.UserRef;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.button.client.Button;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.datepicker.client.CalendarUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AnnotationEditPresenter
        extends DocumentEditPresenter<AnnotationEditView, Annotation>
        implements AnnotationEditUiHandlers {

    private static final String EMPTY_VALUE = "'  '";

    private static final SafeHtml HISTORY_INNER_START = SafeHtmlUtils.fromTrustedString(
            "<div class=\"annotationHistoryInner\">");
    private static final SafeHtml HISTORY_INNER_END = SafeHtmlUtils.fromTrustedString(
            "</div>");
    private static final SafeHtml HISTORY_LINE = SafeHtmlUtils.fromTrustedString(
            "<div class=\"annotationHistoryLine\"></div>");
    private static final SafeHtml HISTORY_COMMENT_BORDER_START = SafeHtmlUtils.fromTrustedString(
            "<div class=\"annotationHistoryCommentBorder\">");
    private static final SafeHtml HISTORY_COMMENT_BORDER_END = SafeHtmlUtils.fromTrustedString(
            "</div>");
    private static final SafeHtml HISTORY_COMMENT_HEADER_START = SafeHtmlUtils.fromTrustedString(
            "<div class=\"annotationHistoryCommentHeader\">");
    private static final SafeHtml HISTORY_COMMENT_HEADER_END = SafeHtmlUtils.fromTrustedString(
            "</div>");
    private static final SafeHtml HISTORY_COMMENT_BODY_START = SafeHtmlUtils.fromTrustedString(
            "<div class=\"annotationHistoryCommentBody\">");
    private static final SafeHtml HISTORY_COMMENT_BODY_END = SafeHtmlUtils.fromTrustedString(
            "</div>");
    private static final SafeHtml HISTORY_ITEM_START = SafeHtmlUtils.fromTrustedString(
            "<div class=\"annotationHistoryItem\">");
    private static final SafeHtml HISTORY_ITEM_END = SafeHtmlUtils.fromTrustedString(
            "</div>");

    private static final long ONE_SECOND = 1000;
    private static final long ONE_MINUTE = ONE_SECOND * 60;
    private static final long ONE_HOUR = ONE_MINUTE * 60;

    private final AnnotationResourceClient annotationResourceClient;
    private final ChooserPresenter<AnnotationTag> statusPresenter;
    private final UserRefPopupPresenter assignedToPresenter;
    private final ChooserPresenter<AnnotationTag> annotationLabelPresenter;
    private final ChooserPresenter<AnnotationTag> annotationCollectionPresenter;
    private final ChooserPresenter<String> commentPresenter;
    private final ClientSecurityContext clientSecurityContext;
    private final DateTimeFormatter dateTimeFormatter;
    private final Provider<DurationPresenter> durationPresenterProvider;

    private DocRef annotationRef;
    private AnnotationDetail annotationDetail;
    private AnnotationPresenter parent;

    private AnnotationTag currentStatus;
    private UserRef currentAssignedTo;
    private AnnotationTag currentAnnotationGroup;

    @Inject
    public AnnotationEditPresenter(final EventBus eventBus,
                                   final AnnotationEditView view,
                                   final AnnotationResourceClient annotationResourceClient,
                                   final ChooserPresenter<AnnotationTag> statusPresenter,
                                   final UserRefPopupPresenter assignedToPresenter,
                                   final ChooserPresenter<AnnotationTag> annotationLabelPresenter,
                                   final ChooserPresenter<AnnotationTag> annotationCollectionPresenter,
                                   final ChooserPresenter<String> commentPresenter,
                                   final ClientSecurityContext clientSecurityContext,
                                   final DateTimeFormatter dateTimeFormatter,
                                   final Provider<DurationPresenter> durationPresenterProvider) {
        super(eventBus, view);
        this.annotationResourceClient = annotationResourceClient;
        this.statusPresenter = statusPresenter;
        this.assignedToPresenter = assignedToPresenter;
        this.annotationLabelPresenter = annotationLabelPresenter;
        this.annotationCollectionPresenter = annotationCollectionPresenter;
        this.commentPresenter = commentPresenter;
        this.clientSecurityContext = clientSecurityContext;
        this.dateTimeFormatter = dateTimeFormatter;
        this.durationPresenterProvider = durationPresenterProvider;

        getView().setUiHandlers(this);
        assignedToPresenter.showActiveUsersOnly(true);

        this.statusPresenter.setDataSupplier((filter, consumer) -> {
            final ExpressionOperator expression = ExpressionOperator
                    .builder()
                    .addTerm(ExpressionTerm.builder()
                            .field(AnnotationTagFields.TYPE_ID)
                            .condition(Condition.EQUALS)
                            .value(AnnotationTagType.STATUS.getDisplayValue())
                            .build())
                    .addTerm(ExpressionTerm.builder()
                            .field(AnnotationTagFields.NAME)
                            .condition(Condition.CONTAINS)
                            .value(filter)
                            .build())
                    .build();
            final ExpressionCriteria criteria = new ExpressionCriteria(expression);
            annotationResourceClient.findAnnotationTags(criteria, values ->
                            consumer.accept(values.getValues()),
                    new DefaultErrorHandler(this, null), this);
        });

        this.annotationCollectionPresenter.setDataSupplier((filter, consumer) -> {
            final ExpressionOperator expression = ExpressionOperator
                    .builder()
                    .addTerm(ExpressionTerm.builder()
                            .field(AnnotationTagFields.TYPE_ID)
                            .condition(Condition.EQUALS)
                            .value(AnnotationTagType.COLLECTION.getDisplayValue())
                            .build())
                    .addTerm(ExpressionTerm.builder()
                            .field(AnnotationTagFields.NAME)
                            .condition(Condition.CONTAINS)
                            .value(filter)
                            .build())
                    .build();
            final ExpressionCriteria criteria = new ExpressionCriteria(expression);
            annotationResourceClient.findAnnotationTags(criteria, values ->
                            consumer.accept(values.getValues()),
                    new DefaultErrorHandler(this, null), this);
        });
        annotationCollectionPresenter.setDisplayValueFunction(AnnotationTag::getName);

        this.commentPresenter.setDataSupplier((filter, consumer) ->
                annotationResourceClient.getStandardComments(filter, consumer, this));

        // See if we are able to get standard comments.
        annotationResourceClient.getStandardComments(null, values ->
                getView().setHasCommentValues(values != null && !values.isEmpty()), this);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(statusPresenter.addDataSelectionHandler(e -> {
            final AnnotationTag selected = statusPresenter.getSelected();
            changeStatus(selected);
        }));
        registerHandler(annotationCollectionPresenter.addDataSelectionHandler(e -> {
            final AnnotationTag selected = annotationCollectionPresenter.getSelected();
            changeAnnotationGroup(selected);
        }));
        registerHandler(commentPresenter.addDataSelectionHandler(e -> {
            final String selected = commentPresenter.getSelected();
            changeComment(selected);
        }));
    }

    private void changeTitle(final String selected) {
        if (hasChanged(getEntity().getName(), selected)) {
            if (annotationDetail != null) {
                final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                        annotationRef,
                        new ChangeTitle(selected));
                change(request);
            }

            RefreshContentTabEvent.fire(this, parent);
        }
    }

    private void setTitle(final String title) {
        getView().setTitle(title);
    }

    private void changeSubject(final String selected) {
        if (hasChanged(getEntity().getSubject(), selected)) {
            if (annotationDetail != null) {
                final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                        annotationRef,
                        new ChangeSubject(selected));
                change(request);
            }
        }
    }

    private void setSubject(final String subject) {
        getView().setSubject(subject);
    }

    private boolean hasChanged(final String oldValue, final String newValue) {
        // Treat empty strings as null so null and "" are treated as equal
        return !Objects.equals(
                (oldValue != null && oldValue.isEmpty()
                        ? null
                        : oldValue),
                (newValue != null && newValue.isEmpty()
                        ? null
                        : newValue));
    }

    private void changeStatus(final AnnotationTag selected) {
        HidePopupRequestEvent.builder(statusPresenter).fire();

        if (!Objects.equals(currentStatus, selected)) {
            currentStatus = selected;
            if (annotationDetail != null) {
                final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                        annotationRef,
                        new SetTag(selected));
                change(request);
            }
        }
    }

    private void setStatus(final AnnotationTag status) {
        getView().setStatus(status);
        statusPresenter.clearFilter();
        statusPresenter.setSelected(status);
    }

    private void changeAssignedTo(final UserRef selected) {
        if (!Objects.equals(currentAssignedTo, selected)) {
            currentAssignedTo = selected;
            setAssignedTo(selected);

            if (annotationDetail != null) {
                final SingleAnnotationChangeRequest request =
                        new SingleAnnotationChangeRequest(annotationRef,
                                new ChangeAssignedTo(selected));
                change(request);
            }
        }
    }

    private void setAssignedTo(final UserRef assignedTo) {
        assignedToPresenter.resolve(assignedTo, userRef -> {
            currentAssignedTo = userRef;
            getView().setAssignedTo(userRef);
            assignedToPresenter.setSelected(currentAssignedTo);
        });
    }

    private void changeAnnotationGroup(final AnnotationTag selected) {
        HidePopupRequestEvent.builder(annotationCollectionPresenter).fire();

        if (!Objects.equals(currentAnnotationGroup, selected)) {
            currentAnnotationGroup = selected;
            if (annotationDetail != null) {
                final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                        annotationRef,
                        new SetTag(selected));
                change(request);
            }
        }
    }

    private void setAnnotationGroup(final AnnotationTag annotationCollection) {
        getView().setCollection(annotationCollection);
        annotationCollectionPresenter.clearFilter();
        annotationCollectionPresenter.setSelected(annotationCollection);
    }

    private void changeComment(final String selected) {
        if (selected != null && hasChanged(getView().getComment(), selected)) {
            getView().setComment(getView().getComment() + selected);
            HidePopupRequestEvent.builder(commentPresenter).fire();
        }
    }

    private void change(final SingleAnnotationChangeRequest request) {
        annotationResourceClient.change(request, parent::read, this);
    }

    public void read(final AnnotationDetail annotationDetail) {
        final Annotation annotation = annotationDetail.getAnnotation();
        this.currentStatus = annotation.getStatus();
        this.currentAssignedTo = annotation.getAssignedTo();
        this.currentAnnotationGroup = annotation.getAnnotationGroup();
        this.annotationRef = annotation.asDocRef();
        this.annotationDetail = annotationDetail;

        getView().setId(annotationDetail.getAnnotation().getId());
        getView().setButtonText("Comment");

        onRead(annotationRef, annotation, false);
        updateHistory(annotationDetail);

        if (annotation.getStatus() == null) {
            // Get an initial status value.
            final ExpressionOperator expression = ExpressionOperator
                    .builder()
                    .addTerm(ExpressionTerm.builder()
                            .field(AnnotationTagFields.TYPE_ID)
                            .condition(Condition.EQUALS)
                            .value(AnnotationTagType.STATUS.getDisplayValue())
                            .build())
                    .build();
            final ExpressionCriteria criteria = new ExpressionCriteria(PageRequest.oneRow(), null, expression);
            annotationResourceClient.findAnnotationTags(criteria, values -> {
                if (annotation.getStatus() == null && values != null && !values.isEmpty()) {
                    setStatus(values.getValues().get(0));
                }
            }, new DefaultErrorHandler(this, null), this);
        }
    }

    private void updateHistory(final AnnotationDetail annotationDetail) {
        if (annotationDetail != null) {
            final List<AnnotationEntry> entries = annotationDetail.getEntries();
            if (entries != null) {
                final Date now = new Date();
                final Map<AnnotationEntryType, EntryValue> currentValues = new HashMap<>();

                final SafeHtmlBuilder html = new SafeHtmlBuilder();
                final StringBuilder text = new StringBuilder();
                html.append(HISTORY_INNER_START);
                SafeHtml line = SafeHtmlUtils.EMPTY_SAFE_HTML;
                boolean first = true;
                for (final AnnotationEntry entry : entries) {
                    final EntryValue currentValue = currentValues.get(entry.getEntryType());

                    addEntryText(text, entry, currentValue);
                    final boolean added = addEntryHtml(html, entry, currentValue, now, line);

                    if (added && first) {
                        // If we actually added some content then make sure we add a line marker before any subsequent
                        // content.
                        first = false;
                        line = HISTORY_LINE;
                    }

                    // Remember the previous value.
                    currentValues.put(entry.getEntryType(), entry.getEntryValue());
                }

                html.append(HISTORY_INNER_END);

                final HTML panel = new HTML(html.toSafeHtml());
                panel.setStyleName("dock-max annotationHistoryOuter");
                panel.addMouseDownHandler(e -> {
                    // If the user has clicked on a link then consume the event.
                    final Element target = e.getNativeEvent().getEventTarget().cast();
                    if (target.hasTagName("u")) {
                        final String link = target.getAttribute("link");
                        if (link != null) {
                            final Hyperlink hyperlink = Hyperlink.create(link);
                            if (hyperlink != null) {
                                HyperlinkEvent.fire(this, hyperlink, this);
                            }
                        }
                    }
                });

                final TextArea textCopy = new TextArea();
                textCopy.setTabIndex(-2);
                textCopy.setStyleName("annotationHistoryText");
                textCopy.setText(text.toString());

                final Button copyToClipboard = new Button();
                copyToClipboard.setIcon(SvgImage.COPY);
                copyToClipboard.setText("Copy History");
                copyToClipboard.addStyleName("dock-min allow-focus annotationHistoryCopyButton");
                copyToClipboard.addClickHandler(e -> {
                    textCopy.selectAll();
                    boolean success = copy(textCopy.getElement());
                    if (!success) {
                        AlertEvent.fireError(
                                AnnotationEditPresenter.this,
                                "Unable to copy",
                                null);
                    }
                });

                final FlowPanel verticalPanel = new FlowPanel();
                verticalPanel.setStyleName("max dock-container-vertical annotationHistoryContainer");
                verticalPanel.add(panel);
                verticalPanel.add(textCopy);
                verticalPanel.add(copyToClipboard);

                getView().setHistoryView(verticalPanel);

                // Scroll the history to the bottom after update.
                Scheduler.get().scheduleDeferred(() ->
                        panel.getElement().setScrollTop(panel.getElement().getScrollHeight()));
            }
        }
    }

    private void addEntryText(final StringBuilder text,
                              final AnnotationEntry entry,
                              final EntryValue currentValue) {
        final String entryUiValue = GwtNullSafe.get(entry.getEntryValue(), EntryValue::asUiValue);

        if (AnnotationEntryType.COMMENT.equals(entry.getEntryType())) {
            text.append(dateTimeFormatter.format(entry.getEntryTime()));
            text.append(", ");
            text.append(getUserName(entry.getEntryUser()));
            text.append(", commented ");
            quote(text, entryUiValue);
            text.append("\n");

        } else if (AnnotationEntryType.LINK.equals(entry.getEntryType())
                   || AnnotationEntryType.UNLINK.equals(entry.getEntryType())) {

            text.append(dateTimeFormatter.format(entry.getEntryTime()));
            text.append(", ");
            text.append(getUserName(entry.getEntryUser()));
            text.append(", ");
            text.append(entry.getEntryType().getDisplayValue().toLowerCase());
            text.append("ed ");
            quote(text, entryUiValue);
            text.append("\n");

        } else if (AnnotationEntryType.ASSIGNED.equals(entry.getEntryType())) {
            if (currentValue != null) {
                text.append(dateTimeFormatter.format(entry.getEntryTime()));
                text.append(", ");
                text.append(getUserName(entry.getEntryUser()));
                text.append(",");
                if (areSameUser(entry.getEntryUser(), currentValue)) {
                    text.append(" removed their assignment");
                } else {
                    text.append(" unassigned ");
                    GWT.log("currentValue: " + currentValue);
                    quote(text, currentValue.asUiValue());
                }
                text.append("\n");
            }

            if (entryUiValue != null && !entryUiValue.trim().isEmpty()) {
                text.append(dateTimeFormatter.format(entry.getEntryTime()));
                text.append(", ");
                text.append(getUserName(entry.getEntryUser()));
                text.append(",");

                if (areSameUser(entry.getEntryUser(), entry.getEntryValue())) {
                    text.append(" self-assigned this");
                } else {
                    text.append(" assigned ");
                    quote(text, entryUiValue);
                }
                text.append("\n");
            }
        } else {
            text.append(dateTimeFormatter.format(entry.getEntryTime()));
            text.append(", ");
            text.append(getUserName(entry.getEntryUser()));
            text.append(",");

            if (currentValue != null) {
                text.append(" changed the ");
            } else {
                text.append(" set the ");
            }
            text.append(entry.getEntryType().getDisplayValue().toLowerCase());

            if (currentValue != null) {
                text.append(" from ");
                quote(text, currentValue.asUiValue());
                text.append(" to ");
                quote(text, entryUiValue);
            } else {
                text.append(" to ");
                quote(text, entryUiValue);
            }
            text.append("\n");
        }
    }

    private String getUserName(final UserRef userRef) {
        if (userRef == null) {
            return "Unknown";
        }
        return userRef.toDisplayString();
    }

    private boolean addEntryHtml(final SafeHtmlBuilder html,
                                 final AnnotationEntry entry,
                                 final EntryValue currentValue,
                                 final Date now,
                                 final SafeHtml line) {
        boolean added = false;
        final String entryUiValue = GwtNullSafe.get(entry.getEntryValue(), EntryValue::asUiValue);

        if (AnnotationEntryType.COMMENT.equals(entry.getEntryType())) {
            html.append(line);
            html.append(HISTORY_COMMENT_BORDER_START);
            html.append(HISTORY_COMMENT_HEADER_START);
            bold(html, getUserName(entry.getEntryUser()));
            html.appendHtmlConstant("&nbsp;");
            html.appendEscaped("commented");
            html.appendHtmlConstant("&nbsp;");
            html.append(getDurationLabel(entry.getEntryTime(), now));
            html.append(HISTORY_COMMENT_HEADER_END);
            html.append(HISTORY_COMMENT_BODY_START);
            html.appendEscaped(entryUiValue);
            html.append(HISTORY_COMMENT_BODY_END);
            html.append(HISTORY_COMMENT_BORDER_END);
            added = true;

        } else if (AnnotationEntryType.LINK.equals(entry.getEntryType())
                   || AnnotationEntryType.UNLINK.equals(entry.getEntryType())) {
            html.append(line);
            html.append(HISTORY_ITEM_START);
            addIcon(html, entry.getEntryType());
            bold(html, getUserName(entry.getEntryUser()));
            html.appendHtmlConstant("&nbsp;");
            html.appendEscaped(entry.getEntryType().getDisplayValue().toLowerCase());
            html.appendEscaped("ed");
            html.appendHtmlConstant("&nbsp;");
            link(html, entryUiValue);
            html.appendHtmlConstant("&nbsp;");
            html.append(getDurationLabel(entry.getEntryTime(), now));
            html.append(HISTORY_ITEM_END);
            added = true;

        } else {
            // Remember initial values but don't show them unless they change.
            if (AnnotationEntryType.ASSIGNED.equals(entry.getEntryType())) {
                if (currentValue != null) {
                    html.append(line);
                    html.append(HISTORY_ITEM_START);
                    addIcon(html, entry.getEntryType());
                    bold(html, getUserName(entry.getEntryUser()));
                    if (areSameUser(entry.getEntryUser(), currentValue)) {
                        html.appendHtmlConstant("&nbsp;");
                        html.appendEscaped("removed their assignment");
                    } else {
                        html.appendHtmlConstant("&nbsp;");
                        html.appendEscaped("unassigned");
                        html.appendHtmlConstant("&nbsp;");
                        bold(html, getValueString(currentValue.asUiValue()));
                    }
                    html.appendEscaped(" ");
                    html.append(getDurationLabel(entry.getEntryTime(), now));
                    html.append(HISTORY_ITEM_END);
                    added = true;
                }

                if (entryUiValue != null && !entryUiValue.trim().isEmpty()) {
                    html.append(line);
                    html.append(HISTORY_ITEM_START);
                    addIcon(html, entry.getEntryType());
                    bold(html, getUserName(entry.getEntryUser()));
                    if (areSameUser(entry.getEntryUser(), entry.getEntryValue())) {
                        html.appendHtmlConstant("&nbsp;");
                        html.appendEscaped("self-assigned this");
                    } else {
                        html.appendHtmlConstant("&nbsp;");
                        html.appendEscaped("assigned");
                        html.appendHtmlConstant("&nbsp;");
                        bold(html, getValueString(entryUiValue));
                    }
                    html.appendHtmlConstant("&nbsp;");
                    html.append(getDurationLabel(entry.getEntryTime(), now));
                    html.append(HISTORY_ITEM_END);
                    added = true;
                }

            } else {
                html.append(line);
                html.append(HISTORY_ITEM_START);
                addIcon(html, entry.getEntryType());
                bold(html, getUserName(entry.getEntryUser()));
                if (currentValue != null) {
                    html.appendHtmlConstant("&nbsp;");
                    html.appendEscaped("changed the");
                    html.appendHtmlConstant("&nbsp;");
                } else {
                    html.appendHtmlConstant("&nbsp;");
                    html.appendEscaped("set the");
                    html.appendHtmlConstant("&nbsp;");
                }
                html.appendEscaped(entry.getEntryType().getDisplayValue().toLowerCase());
                html.appendHtmlConstant("&nbsp;");

                if (currentValue != null) {
                    del(html, getValueString(currentValue.asUiValue()));
                    html.appendHtmlConstant("&nbsp;");
                    html.appendEscaped("to");
                    html.appendHtmlConstant("&nbsp;");
                    ins(html, getValueString(entryUiValue));

                } else {
                    html.appendEscaped(getValueString(entryUiValue));
                }

                html.appendHtmlConstant("&nbsp;");
                html.append(getDurationLabel(entry.getEntryTime(), now));
                html.append(HISTORY_ITEM_END);
                added = true;
            }
        }
        return added;
    }

    private void addIcon(final SafeHtmlBuilder html,
                         final AnnotationEntryType annotationEntryType) {
        final SvgImage image = switch (annotationEntryType) {
            case TITLE -> SvgImage.EDIT;
            case SUBJECT -> SvgImage.EDIT;
            case STATUS -> SvgImage.EXCLAMATION;
            case ASSIGNED -> SvgImage.USER;
            case COMMENT -> SvgImage.EDIT;
            case LINK -> SvgImage.LINK;
            case UNLINK -> SvgImage.UNLINK;
            case RETENTION_PERIOD -> SvgImage.CALENDAR;
            case DESCRIPTION -> SvgImage.EDIT;
            case ADD_TO_COLLECTION -> SvgImage.TABLE_NESTED;
            case REMOVE_FROM_COLLECTION -> SvgImage.TABLE_NESTED;
            case ADD_LABEL -> SvgImage.TAGS;
            case REMOVE_LABEL -> SvgImage.TAGS;
            case DELETE -> SvgImage.CLEAR;
        };
        html.appendHtmlConstant(image.getSvg());
    }

    private boolean areSameUser(final UserRef entryUser, final EntryValue entryValue) {
        if (entryValue instanceof UserRefEntryValue) {
            @SuppressWarnings("PatternVariableCanBeUsed") // GWT ¯\_(ツ)_/¯
            final UserRefEntryValue userRefEntryValue = (UserRefEntryValue) entryValue;
            return Objects.equals(entryUser, userRefEntryValue.getUserRef());
        } else {
            return entryUser == null && entryValue == null;
        }
    }


    private static native boolean copy(final Element element) /*-{
        var activeElement = $doc.activeElement;
        element.focus();
        var success = $doc.execCommand('copy');
        if (activeElement) {
            activeElement.focus();
        }
        return success;
    }-*/;

    private void bold(final SafeHtmlBuilder builder, final String value) {
        builder.appendHtmlConstant("<b>");
        builder.appendEscaped(value);
        builder.appendHtmlConstant("</b>");
    }

    private void del(final SafeHtmlBuilder builder, final String value) {
        builder.appendHtmlConstant("<del>");
        builder.appendEscaped(value);
        builder.appendHtmlConstant("</del>");
    }

    private void ins(final SafeHtmlBuilder builder, final String value) {
        builder.appendHtmlConstant("<ins>");
        builder.appendEscaped(value);
        builder.appendHtmlConstant("</ins>");
    }

    private void link(final SafeHtmlBuilder builder, final String value) {
        final EventId eventId = EventId.parse(value);
        if (eventId != null) {
            // Create a data link.
            final Hyperlink hyperlink = Hyperlink.builder()
                    .text(value)
                    .href("?id=" + eventId.getStreamId() + "&partNo=1&recordNo=" + eventId.getEventId())
                    .type(HyperlinkType.DATA.name().toLowerCase())
                    .build();
            if (!hyperlink.getText().trim().isEmpty()) {
                builder.appendHtmlConstant("<b><u link=\"" + hyperlink + "\">" + value + "</u></b>");
            }
        } else {
            bold(builder, value);
        }
    }

    private String getValueString(final String string) {
        if (string != null && !string.trim().isEmpty()) {
            return string;
        }
        return EMPTY_VALUE;
    }

    private void quote(final StringBuilder text, final String value) {
        text.append("'");
        if (value != null) {
            text.append(value);
        }
        text.append("'");
    }

    @Override
    protected void onRead(final DocRef docRef, final Annotation annotation, final boolean readOnly) {
        setTitle(annotation.getName());
        setSubject(annotation.getSubject());
        setStatus(annotation.getStatus());
        setAssignedTo(annotation.getAssignedTo());
        setAnnotationGroup(annotation.getAnnotationGroup());
        getView().setAssignYourselfVisible(!Objects.equals(currentAssignedTo, clientSecurityContext.getUserRef()));
        getView().setRetentionPeriod(annotation.getRetentionPeriod() == null
                ? "Forever"
                : annotation.getRetentionPeriod().toLongString());
    }

    @Override
    protected Annotation onWrite(final Annotation document) {
        return null;
    }

    private SafeHtml getDurationLabel(final long time, final Date now) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant(
                "<span class=\"annotationDurationLabel\" title=\"" + dateTimeFormatter.format(time) + "\">");
        builder.appendEscaped(getDuration(time, now));
        builder.appendHtmlConstant("</span>");
        return builder.toSafeHtml();
    }

    private String getDuration(final long time, final Date now) {
        final Date start = new Date(time);
        final int days = CalendarUtil.getDaysBetween(start, now);
        if (days == 1) {
            return "yesterday";
        } else if (days > 365) {
            final int years = days / 365;
            if (years == 1) {
                return "a year ago";
            } else {
                return years + "years ago";
            }
        } else if (days > 1) {
            return days + " days ago";
        }

        long diff = now.getTime() - time;
        if (diff > ONE_HOUR) {
            final int hours = (int) (diff / ONE_HOUR);
            if (hours == 1) {
                return "an hour ago";
            } else if (hours > 1) {
                return hours + " hours ago";
            }
        }

        if (diff > ONE_MINUTE) {
            final int minutes = (int) (diff / ONE_MINUTE);
            if (minutes == 1) {
                return "a minute ago";
            } else if (minutes > 1) {
                return minutes + " minutes ago";
            }
        }

        if (diff > ONE_SECOND) {
            final int seconds = (int) (diff / ONE_SECOND);
            if (seconds == 1) {
                return "a second ago";
            } else if (seconds > 1) {
                return seconds + " seconds ago";
            }
        }

        return "just now";
    }

    @Override
    public void onTitleChange() {
        changeTitle(getView().getTitle());
    }

    @Override
    public void onSubjectChange() {
        changeSubject(getView().getSubject());
    }

    @Override
    public void showStatusChooser(final Element element) {
        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.builder(statusPresenter)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .onShow(e -> statusPresenter.focus())
                .fire();
    }

    @Override
    public void showAssignedToChooser(final Element element) {
        assignedToPresenter.setSelected(getEntity().getAssignedTo());
        assignedToPresenter.show(this::changeAssignedTo);
    }

    @Override
    public void showLabelChooser(final Element element) {
        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.builder(annotationLabelPresenter)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .onShow(e -> annotationLabelPresenter.focus())
                .fire();
    }

    @Override
    public void showCollectionChooser(final Element element) {
        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.builder(annotationCollectionPresenter)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .onShow(e -> annotationCollectionPresenter.focus())
                .fire();
    }

    @Override
    public void showCommentChooser(final Element element) {
        commentPresenter.clearFilter();
        commentPresenter.setSelected(getView().getComment());
        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.builder(commentPresenter)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .onShow(e -> commentPresenter.focus())
                .fire();
    }

    @Override
    public void assignYourself() {
        changeAssignedTo(clientSecurityContext.getUserRef());
    }

    @Override
    public void create() {
        final String comment = getView().getComment();
        if (comment != null && !comment.isEmpty()) {
            final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                    annotationRef,
                    new ChangeComment(comment));
            change(request);
            getView().setComment("");
        } else {
            AlertEvent.fireWarn(this, "Please enter a comment", null);
        }
    }

    @Override
    public void showRetentionPeriodChooser(final Element element) {
        final SimpleDuration initial = GwtNullSafe
                .get(annotationDetail,
                        AnnotationDetail::getAnnotation,
                        Annotation::getRetentionPeriod);

        final DurationPresenter durationPresenter = durationPresenterProvider.get();
        durationPresenter.setDuration(initial);
//            final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent.builder(durationPresenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
//                    .popupSize(popupSize)
                .caption("Set Retention Period")
                .modal()
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    e.hide();
                    if (e.isOk()) {
                        final SimpleDuration duration = durationPresenter.getDuration();
                        if (!Objects.equals(getEntity().getRetentionPeriod(), duration)) {
                            final SingleAnnotationChangeRequest request =
                                    new SingleAnnotationChangeRequest(annotationRef,
                                            new ChangeRetentionPeriod(duration));
                            change(request);
                        }
                    }
                })
                .fire();
    }

    @Override
    public void onDelete() {
        ConfirmEvent.fire(this, "Are you sure you want to delete this annotation?", ok -> {
            if (ok) {
                annotationResourceClient.delete(annotationRef, result -> {
                    if (result) {
                        CloseContentTabEvent.fire(this, parent);
                    }
                }, this);
            }
        });
    }

    public void setInitialComment(final String initialComment) {
        getView().setComment(initialComment);
    }

    public void setParent(final AnnotationPresenter parent) {
        this.parent = parent;
    }

    public interface AnnotationEditView extends View, Focus, HasUiHandlers<AnnotationEditUiHandlers> {

        void setId(long id);

        String getTitle();

        void setTitle(String title);

        String getSubject();

        void setSubject(String subject);

        void setStatus(AnnotationTag status);

        void setAssignedTo(UserRef assignedTo);

        void setCollection(AnnotationTag collection);

        String getComment();

        void setComment(String comment);

        void setHasCommentValues(final boolean hasCommentValues);

        void setHistoryView(Widget view);

        void setButtonText(String text);

        void setAssignYourselfVisible(boolean visible);

        void setRetentionPeriod(String retentionPeriod);
    }
}
