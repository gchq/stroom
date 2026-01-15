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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.client.AnnotationEditPresenter.AnnotationEditView;
import stroom.annotation.shared.AddTag;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationEntryType;
import stroom.annotation.shared.AnnotationTable;
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
import stroom.annotation.shared.StringEntryValue;
import stroom.annotation.shared.UserRefEntryValue;
import stroom.content.client.event.CloseContentTabEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.data.client.presenter.ShowDataEvent;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.shared.ExpressionCriteria;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.pipeline.shared.SourceLocation;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.UserRefPopupPresenter;
import stroom.security.shared.FindUserContext;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.button.client.Button;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
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

    private static final String EMPTY_VALUE = "'  '";
    private static final String ENTRY_ID_ATTRIBUTE = "entryId";
    private static final String ENTRY_TYPE_ATTRIBUTE = "entryType";

    private static final SafeHtml ELLIPSES = SafeHtmlUtils.fromTrustedString(
            "<div class=\"setting-block-icon icon-colour__grey svgIcon\">" +
            SvgImage.ELLIPSES_VERTICAL.getSvg() +
            "</div>");
    private static final SafeHtml HISTORY_LINE = SafeHtmlUtils.fromTrustedString(
            "<div class=\"annotationHistoryLine\"></div>");


    private static final Attribute HISTORY_INNER = Attribute.className("annotationHistoryInner");
    private static final Attribute HISTORY_COMMENT_BORDER = Attribute.className("annotationHistoryCommentBorder");
    private static final Attribute HISTORY_COMMENT_HEADER = Attribute.className("annotationHistoryCommentHeader");
    private static final Attribute HISTORY_COMMENT_BODY = Attribute.className("annotationHistoryCommentBody");
    private static final Attribute HISTORY_ITEM = Attribute.className("annotationHistoryItem");
    private static final Attribute ANNOTATION_TABLE = Attribute.className("annotationTable");
    private static final Attribute ANNOTATION_LINK = Attribute.className("annotationLink");

    private final AnnotationResourceClient annotationResourceClient;
    private final ChooserPresenter<AnnotationTag> annotationStatusPresenter;
    private final UserRefPopupPresenter assignedToPresenter;
    private final MultiChooserPresenter<AnnotationTag> annotationLabelPresenter;
    private final MultiChooserPresenter<AnnotationTag> annotationCollectionPresenter;
    private final ChooserPresenter<String> commentPresenter;
    private final ClientSecurityContext clientSecurityContext;
    private final DateTimeFormatter dateTimeFormatter;
    private final DurationPresenter retentionDurationProvider;
    private final DurationLabel durationLabel;
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
                                   final DurationLabel durationLabel,
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
        this.durationLabel = durationLabel;
        this.commentEditPresenterProvider = commentEditPresenterProvider;

        getView().setUiHandlers(this);
        assignedToPresenter.setContext(FindUserContext.ANNOTATION_ASSIGNMENT);

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
        annotationLabelPresenter.setDisplayValueFunction(Lozenge::create);

        this.annotationCollectionPresenter.setDataSupplier((filter, consumer) -> {
            final ExpressionCriteria criteria = createCriteria(AnnotationTagType.COLLECTION, filter);
            annotationResourceClient.findAnnotationTags(criteria, values ->
                            consumer.accept(values.getValues()),
                    new DefaultErrorHandler(this, null), this);
        });
        annotationCollectionPresenter.setDisplayValueFunction(Lozenge::create);

        this.commentPresenter.setDataSupplier((filter, consumer) ->
                annotationResourceClient.getStandardComments(filter, consumer, this));

        // See if we are able to get standard comments.
        annotationResourceClient.getStandardComments(null, values ->
                getView().setHasCommentValues(values != null && !values.isEmpty()), this);
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
                AnnotationChangeEvent.fire(this, annotationRef);
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

            final HtmlBuilder html = new HtmlBuilder();
            final StringBuilder text = new StringBuilder();

            html.div(inner -> {
                SafeHtml line = SafeHtmlUtils.EMPTY_SAFE_HTML;
                boolean first = true;
                for (final AnnotationEntry entry : entries) {
                    addEntryText(text, entry);
                    final boolean added = addEntryHtml(inner, entry, now, line);

                    if (added && first) {
                        // If we actually added some content then make sure we add a line marker before any subsequent
                        // content.
                        first = false;
                        line = HISTORY_LINE;
                    }
                }
            }, HISTORY_INNER);

            final HTML panel = new HTML(html.toSafeHtml());
            panel.setStyleName("dock-max annotationHistoryOuter");
            panel.addMouseDownHandler(e -> {
                // If the user has clicked on a link then consume the event.
                final Element target = e.getNativeEvent().getEventTarget().cast();
                if (target.hasTagName("u")) {
                    final String link = target.getAttribute("link");
                    if (NullSafe.isNonBlankString(link)) {
                        final Hyperlink hyperlink = Hyperlink.create(link);
                        if (hyperlink != null) {
                            HyperlinkEvent.fire(this, hyperlink, this);
                        }
                    } else {
                        final String eventIdString = target.getAttribute("eventId");
                        if (NullSafe.isNonBlankString(eventIdString)) {
                            final EventId eventId = EventId.parse(eventIdString);
                            ShowDataEvent.fire(this, SourceLocation.fromEventId(eventId));
                        } else {
                            final String annotationIdString = target.getAttribute("annotationId");
                            if (NullSafe.isNonBlankString(annotationIdString)) {
                                EditAnnotationEvent.fire(this, Long.parseLong(annotationIdString));
                            }
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
            final IconMenuItem editItem = new IconMenuItem.Builder()
                    .text("Edit Entry")
                    .icon(SvgImage.EDIT)
                    .command(() -> editComment(id))
                    .build();
            menuItems.add(editItem);
        }

        final IconMenuItem deleteItem = new IconMenuItem.Builder()
                .text("Delete Entry")
                .icon(SvgImage.DELETE)
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
        final FetchAnnotationEntryRequest request =
                new FetchAnnotationEntryRequest(annotationRef, id);
        annotationResourceClient.fetchAnnotationEntry(request, result -> {
            if (result.getEntryValue() instanceof final StringEntryValue value) {
                final CommentEditPresenter commentEditPresenter = commentEditPresenterProvider.get();
                commentEditPresenter.setText(value.getValue());
                final PopupSize popupSize = PopupSize.resizable(600, 600);
                ShowPopupEvent.builder(commentEditPresenter)
                        .popupType(PopupType.OK_CANCEL_DIALOG)
                        .popupSize(popupSize)
                        .caption("Edit Comment")
                        .onShow(e -> commentEditPresenter.focus())
                        .onHideRequest(e -> {
                            if (e.isOk()) {
                                changeComment(id, commentEditPresenter.getText(), e);
                            } else {
                                e.hide();
                            }
                        })
                        .fire();
            }
        }, this);
    }

    private void changeComment(final long id,
                               final String text,
                               final HidePopupRequestEvent e) {
        final ChangeAnnotationEntryRequest request = new ChangeAnnotationEntryRequest(
                annotationRef,
                id,
                text);
        annotationResourceClient.changeAnnotationEntry(
                request,
                res -> afterChangeComment(e),
                error -> new DefaultErrorHandler(this, e::reset),
                this);
    }

    private void afterChangeComment(final HidePopupRequestEvent e) {
        try {
            updateHistory();
        } finally {
            e.hide();
        }
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

        switch (entry.getEntryType()) {
            case COMMENT -> {
                text.append(dateTimeFormatter.format(entry.getEntryTime()));
                text.append(", ");
                text.append(getUserName(entry.getEntryUser()));
                text.append(", commented ");
                quote(text, entryUiValue);
                text.append("\n");
            }
            case LINK_EVENT,
                 UNLINK_EVENT,
                 LINK_ANNOTATION,
                 UNLINK_ANNOTATION,
                 ADD_TO_COLLECTION,
                 REMOVE_FROM_COLLECTION,
                 ADD_LABEL,
                 REMOVE_LABEL,
                 DELETE -> {
                text.append(dateTimeFormatter.format(entry.getEntryTime()));
                text.append(", ");
                text.append(getUserName(entry.getEntryUser()));
                text.append(", ");
                text.append(entry.getEntryType().getActionText());
                text.append(" ");
                quote(text, entryUiValue);
                text.append("\n");
            }
            case ADD_TABLE_DATA -> {
                if (entry.getEntryValue() instanceof final AnnotationTable table) {
                    text.append(dateTimeFormatter.format(entry.getEntryTime()));
                    text.append(", ");
                    text.append(getUserName(entry.getEntryUser()));
                    text.append(", added ");
                    text.append(table.getValues().size());
                    text.append(table.getValues().size() == 1
                            ? " row:\n"
                            : " rows:\n");
                    table.append(text);
                    text.append("\n\n");
                }
            }
            case ASSIGNED -> {
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


            }
            default -> {
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
    }

    private String getUserName(final UserRef userRef) {
        if (userRef == null) {
            return "Unknown";
        }
        return userRef.toDisplayString();
    }

    private Attribute[] getEntryIdAttributes(final Attribute className,
                                             final AnnotationEntry entry) {
        return new Attribute[]{
                className,
                new Attribute(SafeHtmlUtil.from(ENTRY_ID_ATTRIBUTE),
                        SafeHtmlUtil.from(entry.getId())),
                new Attribute(SafeHtmlUtil.from(ENTRY_TYPE_ATTRIBUTE),
                        SafeHtmlUtil.from(entry.getEntryType().getPrimitiveValue()))
        };
    }

    private boolean addEntryHtml(final HtmlBuilder html,
                                 final AnnotationEntry entry,
                                 final Date now,
                                 final SafeHtml line) {
        boolean added = false;
        final String entryUiValue = NullSafe.get(entry.getEntryValue(), EntryValue::asUiValue);

        switch (entry.getEntryType()) {
            case COMMENT -> {
                html.append(line);
                html.div(border -> {
                    border.div(header -> {

                        header.bold(getUserName(entry.getEntryUser()));
                        header.append(HtmlBuilder.NB_SPACE);
                        header.appendTrustedString("commented");
                        header.append(HtmlBuilder.NB_SPACE);
                        durationLabel.append(header, entry.getEntryTime(), now);

                        if (!Objects.equals(entry.getEntryUser(), entry.getUpdateUser()) ||
                            !Objects.equals(entry.getEntryTime(), entry.getUpdateTime())) {
                            header.append(HtmlBuilder.NB_SPACE);
                            header.appendTrustedString(" - ");
                            header.append(HtmlBuilder.NB_SPACE);
                            header.bold(getUserName(entry.getUpdateUser()));
                            header.append(HtmlBuilder.NB_SPACE);
                            header.appendTrustedString("edited");
                            header.append(HtmlBuilder.NB_SPACE);
                            durationLabel.append(header, entry.getUpdateTime(), now);
                        }

                        header.append(ELLIPSES);

                    }, getEntryIdAttributes(HISTORY_COMMENT_HEADER, entry));
                    border.div(body -> decorateComment(body, entryUiValue), HISTORY_COMMENT_BODY);
                }, HISTORY_COMMENT_BORDER);
                added = true;
            }
            case LINK_EVENT,
                 UNLINK_EVENT,
                 LINK_ANNOTATION,
                 UNLINK_ANNOTATION,
                 ADD_TO_COLLECTION,
                 REMOVE_FROM_COLLECTION,
                 ADD_LABEL,
                 REMOVE_LABEL,
                 DELETE -> {
                html.append(line);
                html.div(item -> {
                    addIcon(item, entry.getEntryType());
                    item.bold(getUserName(entry.getEntryUser()));
                    item.nbsp();
                    item.append(entry.getEntryType().getActionText());
                    item.nbsp();
                    link(html, entry.getEntryType(), entryUiValue);
                    item.nbsp();
                    durationLabel.append(item, entry.getEntryTime(), now);
                    item.append(ELLIPSES);
                }, getEntryIdAttributes(HISTORY_ITEM, entry));

                added = true;
            }
            case ADD_TABLE_DATA -> {
                if (entry.getEntryValue() instanceof final AnnotationTable table) {
                    final List<List<String>> values = NullSafe.list(table.getValues());

                    html.append(line);
                    html.div(border -> {
                        border.div(header -> {

                            header.bold(getUserName(entry.getEntryUser()));
                            header.nbsp();
                            header.append(values.size() == 1
                                    ? "added " + values.size() + " row"
                                    : "added " + values.size() + " rows");
                            header.nbsp();
                            durationLabel.append(header, entry.getEntryTime(), now);
                            header.append(ELLIPSES);

                        }, getEntryIdAttributes(HISTORY_COMMENT_HEADER, entry));

                        border.div(body -> {
                            body.table(tb -> {
                                // Add table header
                                tb.tr(tr -> {
                                    for (final String col : NullSafe.list(table.getColumns())) {
                                        tr.th(col);
                                    }
                                });

                                // Add table body
                                for (final List<String> row : values) {
                                    tb.tr(tr -> {
                                        for (final String val : NullSafe.list(row)) {
                                            tr.td(val);
                                        }
                                    });
                                }
                            }, ANNOTATION_TABLE);
                        }, HISTORY_COMMENT_BODY);
                    }, HISTORY_COMMENT_BORDER);

                    added = true;
                }
            }
            case ASSIGNED -> {
                if (entry.getPreviousValue() != null) {
                    html.append(line);
                    html.div(item -> {
                        addIcon(item, entry.getEntryType());
                        item.bold(getUserName(entry.getEntryUser()));
                        if (areSameUser(entry.getEntryUser(), entry.getPreviousValue())) {
                            item.nbsp();
                            item.appendTrustedString("removed their assignment");
                        } else {
                            item.nbsp();
                            item.appendTrustedString("unassigned");
                            item.nbsp();
                            item.bold(getValueString(entry.getPreviousValue().asUiValue()));
                        }
                        item.nbsp();
                        durationLabel.append(item, entry.getEntryTime(), now);
                        item.append(ELLIPSES);
                    }, getEntryIdAttributes(HISTORY_ITEM, entry));
                    added = true;
                }

                if (entryUiValue != null && !entryUiValue.trim().isEmpty()) {
                    html.append(line);
                    html.div(item -> {
                        addIcon(html, entry.getEntryType());
                        item.bold(getUserName(entry.getEntryUser()));
                        if (areSameUser(entry.getEntryUser(), entry.getEntryValue())) {
                            item.nbsp();
                            item.appendTrustedString("self-assigned this");
                        } else {
                            item.nbsp();
                            item.appendTrustedString("assigned");
                            item.nbsp();
                            item.bold(getValueString(entryUiValue));
                        }
                        item.nbsp();
                        durationLabel.append(item, entry.getEntryTime(), now);
                        item.append(ELLIPSES);
                    }, getEntryIdAttributes(HISTORY_ITEM, entry));
                    added = true;
                }
            }
            default -> {
                html.append(line);
                html.div(item -> {
                    addIcon(html, entry.getEntryType());
                    item.bold(getUserName(entry.getEntryUser()));
                    if (entry.getPreviousValue() != null) {
                        item.nbsp();
                        item.appendTrustedString("changed the");
                        item.nbsp();
                    } else {
                        item.nbsp();
                        item.appendTrustedString("set the");
                        item.nbsp();
                    }
                    item.append(entry.getEntryType().getDisplayValue().toLowerCase());
                    item.nbsp();

                    if (entry.getPreviousValue() != null) {
                        item.del(getValueString(entry.getPreviousValue().asUiValue()));
                        item.nbsp();
                        item.appendTrustedString("to");
                        item.nbsp();
                        item.ins(getValueString(entryUiValue));

                    } else {
                        item.append(getValueString(entryUiValue));
                    }

                    item.nbsp();
                    durationLabel.append(item, entry.getEntryTime(), now);
                    item.append(ELLIPSES);
                }, getEntryIdAttributes(HISTORY_ITEM, entry));
                added = true;
            }
        }
        return added;
    }

    static void decorateComment(final HtmlBuilder html, final String comment) {
        final StringBuilder sb = new StringBuilder();
        final char[] chars = comment.toCharArray();

        boolean start = true;
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (start && c == '#') {
                // We may have an annotation ref.

                // Get the string.
                final String idString = getNumberString(chars, i + 1);
                if (idString != null) {
                    // Append current buffer.
                    if (sb.length() > 0) {
                        html.append(sb.toString());
                        sb.setLength(0);
                    }

                    // Append the link.
                    link(html, AnnotationEntryType.LINK_ANNOTATION, idString);

                    // Jump to next index.
                    i += idString.length();
                } else {
                    sb.append(c);
                }

            } else if (start && Character.isDigit(c)) {
                // We may have an event id.
                boolean isEventId = false;

                int pos = i;
                final String streamIdString = getNumberString(chars, pos);
                if (streamIdString != null) {
                    pos += streamIdString.length();
                    if (chars.length > pos && chars[pos] == ':') {
                        pos++;
                        final String eventIdString = getNumberString(chars, pos);
                        if (eventIdString != null) {
                            pos += eventIdString.length();

                            // Append current buffer.
                            if (sb.length() > 0) {
                                html.append(sb.toString());
                                sb.setLength(0);
                            }

                            // Append the link.
                            link(html, AnnotationEntryType.LINK_EVENT, streamIdString + ":" + eventIdString);

                            // Jump to next index.
                            i = pos - 1;
                            isEventId = true;
                        }
                    }
                }

                if (!isEventId) {
                    sb.append(c);
                }

            } else {
                start = Character.isWhitespace(c);
                sb.append(c);
            }
        }
        // Add remaining content.
        html.append(sb.toString());
    }

    private static String getNumberString(final char[] chars, final int start) {
        // Try to get a number.
        int len = 0;
        for (int i = start; i < chars.length; i++) {
            final char c = chars[i];
            if (Character.isDigit(c)) {
                len++;
            } else {
                break;
            }
        }
        if (len > 0) {
            try {
                // Get the string.
                final String idString = new String(chars, start, len);
                // Make sure it is a valid number.
                Long.parseLong(idString);
                return idString;
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }
        return null;
    }

    private void addIcon(final HtmlBuilder html,
                         final AnnotationEntryType annotationEntryType) {
        final SvgImage image = switch (annotationEntryType) {
            case TITLE -> SvgImage.EDIT;
            case SUBJECT -> SvgImage.EDIT;
            case STATUS -> SvgImage.EXCLAMATION;
            case ASSIGNED -> SvgImage.USER;
            case COMMENT -> SvgImage.EDIT;
            case LINK_EVENT -> SvgImage.LINK;
            case UNLINK_EVENT -> SvgImage.UNLINK;
            case RETENTION_PERIOD -> SvgImage.CALENDAR;
            case DESCRIPTION -> SvgImage.EDIT;
            case ADD_TO_COLLECTION -> SvgImage.TABLE_NESTED;
            case REMOVE_FROM_COLLECTION -> SvgImage.TABLE_NESTED;
            case ADD_LABEL -> SvgImage.TAGS;
            case REMOVE_LABEL -> SvgImage.TAGS;
            case ADD_TABLE_DATA -> SvgImage.TABLE;
            case LINK_ANNOTATION -> SvgImage.LINK;
            case UNLINK_ANNOTATION -> SvgImage.UNLINK;
            case DELETE -> SvgImage.CLEAR;
        };
        html.appendTrustedString(image.getSvg());
    }

    private boolean areSameUser(final UserRef entryUser, final EntryValue entryValue) {
        if (entryValue instanceof final UserRefEntryValue userRefEntryValue) {
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

    private static void link(final HtmlBuilder builder,
                             final AnnotationEntryType entryType,
                             final String value) {
        if (AnnotationEntryType.LINK_EVENT.equals(entryType) ||
            AnnotationEntryType.UNLINK_EVENT.equals(entryType)) {
            final EventId eventId = EventId.parse(value);
            if (eventId != null) {
                builder.underline(u -> u.append(eventId.toString()),
                        ANNOTATION_LINK, new Attribute("eventId", eventId.toString()));
            } else {
                builder.bold(value);
            }
        } else if (AnnotationEntryType.LINK_ANNOTATION.equals(entryType) ||
                   AnnotationEntryType.UNLINK_ANNOTATION.equals(entryType)) {
            builder.underline(u -> {
                u.append("#");
                u.append(value);
            }, ANNOTATION_LINK, new Attribute("annotationId", value));
        } else {
            builder.bold(value);
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
                        AnnotationChangeEvent.fire(this, null);
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
