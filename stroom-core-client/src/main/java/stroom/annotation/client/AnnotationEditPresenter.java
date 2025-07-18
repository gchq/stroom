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
import stroom.annotation.shared.AddTag;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationEntryType;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagFields;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.ChangeAnnotationEntryRequest;
import stroom.annotation.shared.ChangeAssignedTo;
import stroom.annotation.shared.ChangeComment;
import stroom.annotation.shared.ChangeRetentionPeriod;
import stroom.annotation.shared.ChangeSubject;
import stroom.annotation.shared.ChangeTitle;
import stroom.annotation.shared.DeleteAnnotationEntryRequest;
import stroom.annotation.shared.EntryValue;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.FetchAnnotationEntryRequest;
import stroom.annotation.shared.RemoveTag;
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
import stroom.query.api.ConditionalFormattingStyle;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.ClassNameBuilder;
import stroom.security.client.presenter.UserRefPopupPresenter;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.button.client.Button;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.menu.client.presenter.SimpleMenuItem;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.MouseDownEvent;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class AnnotationEditPresenter
        extends DocumentEditPresenter<AnnotationEditView, Annotation>
        implements AnnotationEditUiHandlers {

    public static final String LOZENGE = "lozenge";

    private static final String EMPTY_VALUE = "'  '";
    private static final String ENTRY_ID_ATTRIBUTE = "entryId";
    private static final String ENTRY_TYPE_ATTRIBUTE = "entryType";

    private static final SafeHtml ELLIPSES = SafeHtmlUtils.fromTrustedString(
            "<div class=\"setting-block-icon icon-colour__grey svgIcon\">" +
            SvgImage.ELLIPSES_VERTICAL.getSvg() +
            "</div>");
    private static final SafeHtml HISTORY_INNER_START = SafeHtmlUtils.fromTrustedString(
            "<div class=\"annotationHistoryInner\">");
    private static final SafeHtml HISTORY_INNER_END = SafeHtmlUtils.fromTrustedString(
            "</div>");
    private static final SafeHtml HISTORY_LINE = SafeHtmlUtils.fromTrustedString(
            "<div class=\"annotationHistoryLine\"></div>");
    private static final SafeHtml HISTORY_COMMENT_BORDER_START = SafeHtmlUtils.fromTrustedString(
            "<div class=\"annotationHistoryCommentBorder\"");
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
            "<div class=\"annotationHistoryItem\"");
    private static final SafeHtml HISTORY_ITEM_END = SafeHtmlUtils.fromTrustedString(
            "</div>");

    private static final long ONE_SECOND = 1000;
    private static final long ONE_MINUTE = ONE_SECOND * 60;
    private static final long ONE_HOUR = ONE_MINUTE * 60;

    private final AnnotationResourceClient annotationResourceClient;
    private final ChooserPresenter<AnnotationTag> annotationStatusPresenter;
    private final UserRefPopupPresenter assignedToPresenter;
    private final MultiChooserPresenter<AnnotationTag> annotationLabelPresenter;
    private final MultiChooserPresenter<AnnotationTag> annotationCollectionPresenter;
    private final ChooserPresenter<String> commentPresenter;
    private final ClientSecurityContext clientSecurityContext;
    private final DateTimeFormatter dateTimeFormatter;
    private final DurationPresenter retentionDurationProvider;
    private final Provider<CommentEditPresenter> commentEditPresenterProvider;

    private DocRef annotationRef;
    private AnnotationPresenter parent;

    private AnnotationTag currentStatus;
    private UserRef currentAssignedTo;
    private List<AnnotationTag> currentLabels;
    private List<AnnotationTag> currentCollections;
    private SimpleDuration currentRetentionPeriod;

    private String currentTitle;
    private String currentSubject;

    @Inject
    public AnnotationEditPresenter(final EventBus eventBus,
                                   final AnnotationEditView view,
                                   final AnnotationResourceClient annotationResourceClient,
                                   final ChooserPresenter<AnnotationTag> annotationStatusPresenter,
                                   final UserRefPopupPresenter assignedToPresenter,
                                   final MultiChooserPresenter<AnnotationTag> annotationLabelPresenter,
                                   final MultiChooserPresenter<AnnotationTag> annotationCollectionPresenter,
                                   final ChooserPresenter<String> commentPresenter,
                                   final ClientSecurityContext clientSecurityContext,
                                   final DateTimeFormatter dateTimeFormatter,
                                   final DurationPresenter retentionDurationProvider,
                                   final Provider<CommentEditPresenter> commentEditPresenterProvider) {
        super(eventBus, view);
        this.annotationResourceClient = annotationResourceClient;
        this.annotationStatusPresenter = annotationStatusPresenter;
        this.assignedToPresenter = assignedToPresenter;
        this.annotationLabelPresenter = annotationLabelPresenter;
        this.annotationCollectionPresenter = annotationCollectionPresenter;
        this.commentPresenter = commentPresenter;
        this.clientSecurityContext = clientSecurityContext;
        this.dateTimeFormatter = dateTimeFormatter;
        this.retentionDurationProvider = retentionDurationProvider;
        this.commentEditPresenterProvider = commentEditPresenterProvider;

        getView().setUiHandlers(this);
        assignedToPresenter.showActiveUsersOnly(true);

        this.annotationStatusPresenter.setDataSupplier((filter, consumer) -> {
            final ExpressionCriteria criteria = createCriteria(AnnotationTagType.STATUS, filter);
            annotationResourceClient.findAnnotationTags(criteria, values ->
                            consumer.accept(values.getValues()),
                    new DefaultErrorHandler(this, null), this);
        });
        annotationStatusPresenter.setDisplayValueFunction(at -> SafeHtmlUtils.fromString(at.getName()));

        this.annotationLabelPresenter.setDataSupplier((filter, consumer) -> {
            final ExpressionCriteria criteria = createCriteria(AnnotationTagType.LABEL, filter);
            annotationResourceClient.findAnnotationTags(criteria, values ->
                            consumer.accept(values.getValues()),
                    new DefaultErrorHandler(this, null), this);
        });
        annotationLabelPresenter.setDisplayValueFunction(at -> createSwatch(at.getStyle(), at.getName()));

        this.annotationCollectionPresenter.setDataSupplier((filter, consumer) -> {
            final ExpressionCriteria criteria = createCriteria(AnnotationTagType.COLLECTION, filter);
            annotationResourceClient.findAnnotationTags(criteria, values ->
                            consumer.accept(values.getValues()),
                    new DefaultErrorHandler(this, null), this);
        });
        annotationCollectionPresenter.setDisplayValueFunction(at -> createSwatch(at.getStyle(), at.getName()));

        this.commentPresenter.setDataSupplier((filter, consumer) ->
                annotationResourceClient.getStandardComments(filter, consumer, this));

        // See if we are able to get standard comments.
        annotationResourceClient.getStandardComments(null, values ->
                getView().setHasCommentValues(values != null && !values.isEmpty()), this);
        assignedToPresenter.showActiveUsersOnly(true);
    }

    public static SafeHtml createSwatch(final ConditionalFormattingStyle formattingStyle,
                                        final String name) {
        final ClassNameBuilder classNameBuilder = new ClassNameBuilder();
        classNameBuilder.addClassName(LOZENGE);
        if (formattingStyle != null) {
            classNameBuilder.addClassName(formattingStyle.getCssClassName());
        }

        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        sb.appendHtmlConstant("<div");
        sb.appendHtmlConstant(classNameBuilder.buildClassAttribute());
        sb.appendHtmlConstant(">");
        sb.appendEscaped(name);
        sb.appendHtmlConstant("</div>");

        return sb.toSafeHtml();
    }

    private ExpressionCriteria createCriteria(final AnnotationTagType annotationTagType,
                                              final String filter) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        builder.addTerm(ExpressionTerm.builder()
                .field(AnnotationTagFields.TYPE_ID)
                .condition(Condition.EQUALS)
                .value(annotationTagType.getDisplayValue())
                .build());
        if (!NullSafe.isBlankString(filter)) {
            builder.addTerm(ExpressionTerm.builder()
                    .field(AnnotationTagFields.NAME)
                    .condition(Condition.CONTAINS)
                    .value(filter)
                    .build());
        }
        return new ExpressionCriteria(builder.build());
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(annotationStatusPresenter.addDataSelectionHandler(e -> {
            final AnnotationTag selected = annotationStatusPresenter.getSelected();
            if (!Objects.equals(currentStatus, selected)) {
                changeStatus(selected);
                HidePopupRequestEvent.builder(annotationStatusPresenter).fire();
            }
        }));
        registerHandler(annotationLabelPresenter.addSelectionHandler(e -> {
            final List<AnnotationTag> selected = annotationLabelPresenter.getSelectedItems();
            changeAnnotationLabels(selected);
        }));
        registerHandler(annotationCollectionPresenter.addSelectionHandler(e -> {
            final List<AnnotationTag> selected = annotationCollectionPresenter.getSelectedItems();
            changeAnnotationCollections(selected);
        }));
        registerHandler(commentPresenter.addDataSelectionHandler(e -> {
            final String selected = commentPresenter.getSelected();
            changeComment(selected);
        }));
    }

    private void changeTitle(final String selected) {
        if (hasChanged(currentTitle, selected)) {
            currentTitle = selected;
            final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                    annotationRef,
                    new ChangeTitle(selected));
            change(request);

            getEntity().setName(selected);
            RefreshContentTabEvent.fire(this, parent);
        }
    }

    private void setTitle(final String title) {
        currentTitle = title;
        getView().setTitle(title);
    }

    private void changeSubject(final String selected) {
        if (hasChanged(currentSubject, selected)) {
            currentSubject = selected;
            final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                    annotationRef,
                    new ChangeSubject(selected));
            change(request);
        }
    }

    private void setSubject(final String subject) {
        currentSubject = subject;
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
        if (!Objects.equals(currentStatus, selected)) {
            setStatus(selected);

            final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                    annotationRef,
                    new SetTag(selected));
            change(request);
        }
    }

    private void setStatus(final AnnotationTag status) {
        currentStatus = status;
        getView().setStatus(status);
        annotationStatusPresenter.clearFilter();
        annotationStatusPresenter.setSelected(status);
    }

    private void changeAssignedTo(final UserRef selected) {
        if (!Objects.equals(currentAssignedTo, selected)) {
            setAssignedTo(selected);

            final SingleAnnotationChangeRequest request =
                    new SingleAnnotationChangeRequest(annotationRef,
                            new ChangeAssignedTo(selected));
            change(request);
        }
    }

    private void setAssignedTo(final UserRef assignedTo) {
        currentAssignedTo = assignedTo;
        assignedToPresenter.resolve(assignedTo, userRef -> {
            currentAssignedTo = userRef;
            getView().setAssignedTo(userRef);
            getView().setAssignYourselfVisible(!Objects.equals(userRef, clientSecurityContext.getUserRef()));
            assignedToPresenter.setSelected(currentAssignedTo);
        });
    }

    private void changeAnnotationLabels(final List<AnnotationTag> selected) {
        if (!Objects.equals(currentLabels, selected)) {
            if (currentLabels != null) {
                for (final AnnotationTag annotationTag : currentLabels) {
                    if (!selected.contains(annotationTag)) {
                        final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                                annotationRef,
                                new RemoveTag(annotationTag));
                        change(request);
                    }
                }
            }

            for (final AnnotationTag annotationTag : selected) {
                if (currentLabels == null || !currentLabels.contains(annotationTag)) {
                    final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                            annotationRef,
                            new AddTag(annotationTag));
                    change(request);
                }
            }

            setLabels(selected);
        }
    }

    private void setLabels(final List<AnnotationTag> labels) {
        currentLabels = labels;
        getView().setLabels(currentLabels);
    }

    private void changeAnnotationCollections(final List<AnnotationTag> selected) {
        if (!Objects.equals(currentCollections, selected)) {
            if (currentCollections != null) {
                for (final AnnotationTag annotationTag : currentCollections) {
                    if (!selected.contains(annotationTag)) {
                        final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                                annotationRef,
                                new RemoveTag(annotationTag));
                        change(request);
                    }
                }
            }

            for (final AnnotationTag annotationTag : selected) {
                if (currentCollections == null || !currentCollections.contains(annotationTag)) {
                    final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                            annotationRef,
                            new AddTag(annotationTag));
                    change(request);
                }
            }

            setCollections(selected);
        }
    }

    private void setCollections(final List<AnnotationTag> collections) {
        currentCollections = collections;
        getView().setCollections(currentCollections);
    }

    @Override
    public void showRetentionPeriodChooser(final Element element) {
        retentionDurationProvider.show(currentRetentionPeriod, this::changeRetentionPeriod);
    }

    private void changeRetentionPeriod(final SimpleDuration retentionPeriod) {
        if (!Objects.equals(currentRetentionPeriod, retentionPeriod)) {
            setRetentionPeriod(retentionPeriod);

            final SingleAnnotationChangeRequest request =
                    new SingleAnnotationChangeRequest(annotationRef,
                            new ChangeRetentionPeriod(retentionPeriod));
            change(request);
        }
    }

    private void setRetentionPeriod(final SimpleDuration retentionPeriod) {
        currentRetentionPeriod = retentionPeriod;
        getView().setRetentionPeriod(retentionPeriod == null
                ? "Forever"
                : retentionPeriod.toLongString());
    }

    private void changeComment(final String selected) {
        if (selected != null && hasChanged(getView().getComment(), selected)) {
            getView().setComment(getView().getComment() + selected);
            HidePopupRequestEvent.builder(commentPresenter).fire();
        }
    }

    private void change(final SingleAnnotationChangeRequest request) {
        change(request, success -> {
            if (success) {
                updateHistory();
            }
        });
    }

    private void change(final SingleAnnotationChangeRequest request, final Consumer<Boolean> consumer) {
        annotationResourceClient.change(request, consumer, this);
    }

    public void updateHistory() {
        annotationResourceClient.getAnnotationEntries(annotationRef, this::updateHistory, this);
    }

    private void updateHistory(final List<AnnotationEntry> entries) {
        if (entries != null) {
            final Date now = new Date();

            final SafeHtmlBuilder html = new SafeHtmlBuilder();
            final StringBuilder text = new StringBuilder();
            html.append(HISTORY_INNER_START);
            SafeHtml line = SafeHtmlUtils.EMPTY_SAFE_HTML;
            boolean first = true;
            for (final AnnotationEntry entry : entries) {
                addEntryText(text, entry);
                final boolean added = addEntryHtml(html, entry, now, line);

                if (added && first) {
                    // If we actually added some content then make sure we add a line marker before any subsequent
                    // content.
                    first = false;
                    line = HISTORY_LINE;
                }
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
                } else {
                    Element parent = target;
                    String id = null;
                    String type = null;
                    while (parent != null && NullSafe.isBlankString(id)) {
                        id = parent.getAttribute(ENTRY_ID_ATTRIBUTE);
                        type = parent.getAttribute(ENTRY_TYPE_ATTRIBUTE);
                        parent = parent.getParentElement();
                    }
                    if (NullSafe.isNonBlankString(id) && NullSafe.isNonBlankString(type)) {
                        final AnnotationEntryType entryType =
                                AnnotationEntryType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(Byte.parseByte(type));
                        showEntryEditMenu(e, Long.parseLong(id), entryType);
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
                final boolean success = copy(textCopy.getElement());
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

    private void showEntryEditMenu(final MouseDownEvent event,
                                   final long id,
                                   final AnnotationEntryType entryType) {
        final List<Item> menuItems = new ArrayList<>();
        if (AnnotationEntryType.COMMENT.equals(entryType)) {
            final SimpleMenuItem editItem = new SimpleMenuItem.Builder()
                    .text("Edit Entry")
                    .command(() -> editComment(id))
                    .build();
            menuItems.add(editItem);
        }

        final SimpleMenuItem deleteItem = new SimpleMenuItem.Builder()
                .text("Delete Entry")
                .command(() -> deleteEntry(id))
                .build();
        menuItems.add(deleteItem);

        final PopupPosition popupPosition = new PopupPosition(event.getClientX(), event.getClientY());
        ShowMenuEvent
                .builder()
                .items(menuItems)
                .popupPosition(popupPosition)
                .fire(this);
    }

    private void editComment(final long id) {
        final FetchAnnotationEntryRequest fetchAnnotationEntryRequest =
                new FetchAnnotationEntryRequest(annotationRef, id);
        annotationResourceClient.fetchAnnotationEntry(fetchAnnotationEntryRequest, result -> {
            final CommentEditPresenter commentEditPresenter = commentEditPresenterProvider.get();
            commentEditPresenter.setText(result.getEntryValue().asPersistedValue());
            final PopupSize popupSize = PopupSize.resizable(600, 600);
            ShowPopupEvent.builder(commentEditPresenter)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption("Edit Comment")
                    .onShow(e -> commentEditPresenter.focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            final ChangeAnnotationEntryRequest changeAnnotationEntryRequest =
                                    new ChangeAnnotationEntryRequest(annotationRef,
                                            id,
                                            commentEditPresenter.getText());
                            annotationResourceClient.changeAnnotationEntry(changeAnnotationEntryRequest, res -> {
                                        try {
                                            updateHistory();
                                        } finally {
                                            e.hide();
                                        }
                                    }, error -> new DefaultErrorHandler(this, e::reset),
                                    this);

                        } else {
                            e.hide();
                        }
                    })
                    .fire();
        }, this);
    }

    private void deleteEntry(final long id) {
        ConfirmEvent.fire(this, "Are you sure you want to delete this entry?", ok -> {
            if (ok) {
                final DeleteAnnotationEntryRequest request = new DeleteAnnotationEntryRequest(annotationRef, id);
                annotationResourceClient.deleteAnnotationEntry(request, result -> updateHistory(), this);
            }
        });
    }

    private void addEntryText(final StringBuilder text,
                              final AnnotationEntry entry) {
        final String entryUiValue = NullSafe.get(entry.getEntryValue(), EntryValue::asUiValue);

        if (AnnotationEntryType.COMMENT.equals(entry.getEntryType())) {
            text.append(dateTimeFormatter.format(entry.getEntryTime()));
            text.append(", ");
            text.append(getUserName(entry.getEntryUser()));
            text.append(", commented ");
            quote(text, entryUiValue);
            text.append("\n");

        } else if (AnnotationEntryType.NON_REPLACING.contains(entry.getEntryType())) {
            text.append(dateTimeFormatter.format(entry.getEntryTime()));
            text.append(", ");
            text.append(getUserName(entry.getEntryUser()));
            text.append(", ");
            text.append(entry.getEntryType().getActionText());
            quote(text, entryUiValue);
            text.append("\n");

        } else if (AnnotationEntryType.ASSIGNED.equals(entry.getEntryType())) {
            if (entry.getPreviousValue() != null) {
                text.append(dateTimeFormatter.format(entry.getEntryTime()));
                text.append(", ");
                text.append(getUserName(entry.getEntryUser()));
                text.append(",");
                if (areSameUser(entry.getEntryUser(), entry.getPreviousValue())) {
                    text.append(" removed their assignment");
                } else {
                    text.append(" unassigned ");
                    GWT.log("currentValue: " + entry.getPreviousValue());
                    quote(text, entry.getPreviousValue().asUiValue());
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

            if (entry.getPreviousValue() != null) {
                text.append(" changed the ");
            } else {
                text.append(" set the ");
            }
            text.append(entry.getEntryType().getActionText());

            if (entry.getPreviousValue() != null) {
                text.append(" from ");
                quote(text, entry.getPreviousValue().asUiValue());
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

    private void addEntryId(final SafeHtmlBuilder html,
                            final AnnotationEntry entry) {
        html.append(SafeHtmlUtils.fromTrustedString(" " +
                                                    ENTRY_ID_ATTRIBUTE +
                                                    "=\"" +
                                                    entry.getId() +
                                                    "\"" +
                                                    " " +
                                                    ENTRY_TYPE_ATTRIBUTE +
                                                    "=\"" + entry.getEntryType().getPrimitiveValue() +
                                                    "\"" +
                                                    ">"));
    }

    private boolean addEntryHtml(final SafeHtmlBuilder html,
                                 final AnnotationEntry entry,
                                 final Date now,
                                 final SafeHtml line) {
        boolean added = false;
        final String entryUiValue = NullSafe.get(entry.getEntryValue(), EntryValue::asUiValue);

        if (AnnotationEntryType.COMMENT.equals(entry.getEntryType())) {
            html.append(line);
            html.append(HISTORY_COMMENT_BORDER_START);
            addEntryId(html, entry);
            html.append(HISTORY_COMMENT_HEADER_START);
            bold(html, getUserName(entry.getEntryUser()));
            html.appendHtmlConstant("&nbsp;");
            html.appendEscaped("commented");
            html.appendHtmlConstant("&nbsp;");
            html.append(getDurationLabel(entry.getEntryTime(), now));

            if (!Objects.equals(entry.getEntryUser(), entry.getUpdateUser()) ||
                !Objects.equals(entry.getEntryTime(), entry.getUpdateTime())) {
                html.appendHtmlConstant("&nbsp;");
                html.appendEscaped(" - ");
                html.appendHtmlConstant("&nbsp;");
                bold(html, getUserName(entry.getUpdateUser()));
                html.appendHtmlConstant("&nbsp;");
                html.appendEscaped("edited");
                html.appendHtmlConstant("&nbsp;");
                html.append(getDurationLabel(entry.getUpdateTime(), now));
            }

            html.append(ELLIPSES);
            html.append(HISTORY_COMMENT_HEADER_END);
            html.append(HISTORY_COMMENT_BODY_START);
            html.appendEscaped(entryUiValue);
            html.append(HISTORY_COMMENT_BODY_END);
            html.append(HISTORY_COMMENT_BORDER_END);
            added = true;

        } else if (AnnotationEntryType.NON_REPLACING.contains(entry.getEntryType())) {
            html.append(line);
            html.append(HISTORY_ITEM_START);
            addEntryId(html, entry);
            addIcon(html, entry.getEntryType());
            bold(html, getUserName(entry.getEntryUser()));
            html.appendHtmlConstant("&nbsp;");
            html.appendEscaped(entry.getEntryType().getActionText());
            html.appendHtmlConstant("&nbsp;");
            link(html, entryUiValue);
            html.appendHtmlConstant("&nbsp;");
            html.append(getDurationLabel(entry.getEntryTime(), now));
            html.append(ELLIPSES);
            html.append(HISTORY_ITEM_END);
            added = true;

        } else {
            // Remember initial values but don't show them unless they change.
            if (AnnotationEntryType.ASSIGNED.equals(entry.getEntryType())) {
                if (entry.getPreviousValue() != null) {
                    html.append(line);
                    html.append(HISTORY_ITEM_START);
                    addEntryId(html, entry);
                    addIcon(html, entry.getEntryType());
                    bold(html, getUserName(entry.getEntryUser()));
                    if (areSameUser(entry.getEntryUser(), entry.getPreviousValue())) {
                        html.appendHtmlConstant("&nbsp;");
                        html.appendEscaped("removed their assignment");
                    } else {
                        html.appendHtmlConstant("&nbsp;");
                        html.appendEscaped("unassigned");
                        html.appendHtmlConstant("&nbsp;");
                        bold(html, getValueString(entry.getPreviousValue().asUiValue()));
                    }
                    html.appendEscaped(" ");
                    html.append(getDurationLabel(entry.getEntryTime(), now));
                    html.append(ELLIPSES);
                    html.append(HISTORY_ITEM_END);
                    added = true;
                }

                if (entryUiValue != null && !entryUiValue.trim().isEmpty()) {
                    html.append(line);
                    html.append(HISTORY_ITEM_START);
                    addEntryId(html, entry);
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
                    html.append(ELLIPSES);
                    html.append(HISTORY_ITEM_END);
                    added = true;
                }

            } else {
                html.append(line);
                html.append(HISTORY_ITEM_START);
                addEntryId(html, entry);
                addIcon(html, entry.getEntryType());
                bold(html, getUserName(entry.getEntryUser()));
                if (entry.getPreviousValue() != null) {
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

                if (entry.getPreviousValue() != null) {
                    del(html, getValueString(entry.getPreviousValue().asUiValue()));
                    html.appendHtmlConstant("&nbsp;");
                    html.appendEscaped("to");
                    html.appendHtmlConstant("&nbsp;");
                    ins(html, getValueString(entryUiValue));

                } else {
                    html.appendEscaped(getValueString(entryUiValue));
                }

                html.appendHtmlConstant("&nbsp;");
                html.append(getDurationLabel(entry.getEntryTime(), now));
                html.append(ELLIPSES);
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
        this.annotationRef = annotation.asDocRef();
        this.currentStatus = annotation.getStatus();
        this.currentAssignedTo = annotation.getAssignedTo();

        getView().setId(annotation.getId());
        getView().setButtonText("Comment");

        setTitle(annotation.getName());
        setSubject(annotation.getSubject());
        setStatus(annotation.getStatus());
        setAssignedTo(annotation.getAssignedTo());
        setLabels(annotation.getLabels());
        setCollections(annotation.getCollections());
        setRetentionPeriod(annotation.getRetentionPeriod());

        updateHistory();
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

        final long diff = now.getTime() - time;
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
        ShowPopupEvent.builder(annotationStatusPresenter)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .onShow(e -> annotationStatusPresenter.focus())
                .fire();
    }

    @Override
    public void showAssignedToChooser(final Element element) {
        assignedToPresenter.setSelected(currentAssignedTo);
        assignedToPresenter.show(this::changeAssignedTo);
    }

    @Override
    public void showLabelChooser(final Element element) {
        annotationLabelPresenter.clearFilter();
        annotationLabelPresenter.setSelectedItems(currentLabels);
        annotationLabelPresenter.refresh();

        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.builder(annotationLabelPresenter)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .onShow(e -> annotationLabelPresenter.focus())
//                .onHide(e -> changeAnnotationLabels(annotationLabelPresenter.getSelectedItems()))
                .fire();
    }

    @Override
    public void showCollectionChooser(final Element element) {
        annotationCollectionPresenter.clearFilter();
        annotationCollectionPresenter.setSelectedItems(currentCollections);
        annotationCollectionPresenter.refresh();

        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.builder(annotationCollectionPresenter)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .onShow(e -> annotationCollectionPresenter.focus())
//                .onHide(e -> changeAnnotationCollections(annotationCollectionPresenter.getSelectedItems()))
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

        void setAssignYourselfVisible(boolean visible);

        void setLabels(List<AnnotationTag> labels);

        void setCollections(List<AnnotationTag> collections);

        String getComment();

        void setComment(String comment);

        void setHasCommentValues(final boolean hasCommentValues);

        void setHistoryView(Widget view);

        void setButtonText(String text);

        void setRetentionPeriod(String retentionPeriod);
    }
}
