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
import stroom.annotation.client.AnnotationEditPresenter.AnnotationEditView;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.hyperlink.client.HyperlinkType;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.UserResource;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.RenamePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.datepicker.client.CalendarUtil;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class AnnotationEditPresenter extends MyPresenterWidget<AnnotationEditView> implements AnnotationEditUiHandlers {
    private static final String EMPTY_VALUE = "'  '";

    private static final String HISTORY_INNER_START = "<div class=\"annotationHistoryInner\">";
    private static final String HISTORY_INNER_END = "</div>";
    private static final String HISTORY_LINE_START = "<div class=\"annotationHistoryLine\">" +
            "<div class=\"annotationHistoryLineMargin\">" +
            "<div class=\"annotationHistoryLineMarker\"></div>" +
            "</div>";
    private static final String HISTORY_LINE_END = "</div>";
    private static final String HISTORY_COMMENT_BORDER_START = "<div class=\"annotationHistoryCommentBorder\">";
    private static final String HISTORY_COMMENT_BORDER_END = "</div>";
    private static final String HISTORY_COMMENT_HEADER_START = "<div class=\"annotationHistoryCommentHeader\">";
    private static final String HISTORY_COMMENT_HEADER_END = "</div>";
    private static final String HISTORY_COMMENT_BODY_START = "<div class=\"annotationHistoryCommentBody\">";
    private static final String HISTORY_COMMENT_BODY_END = "</div>";
    private static final String HISTORY_ITEM_START = "<div class=\"annotationHistoryItem\">";
    private static final String HISTORY_ITEM_END = "</div>";

    private static final long ONE_SECOND = 1000;
    private static final long ONE_MINUTE = ONE_SECOND * 60;
    private static final long ONE_HOUR = ONE_MINUTE * 60;

    private final RestFactory restFactory;
    private final ChooserPresenter statusPresenter;
    private final ChooserPresenter assignedToPresenter;
    private final ChooserPresenter commentPresenter;
    private final LinkedEventPresenter linkedEventPresenter;
    private final ClientSecurityContext clientSecurityContext;

    private AnnotationDetail annotationDetail;
    private Long currentId;
    private List<EventId> linkedEvents;
    private String currentTitle;
    private String currentSubject;
    private String currentStatus;
    private String currentAssignedTo;
    private String initialComment;

    @Inject
    public AnnotationEditPresenter(final EventBus eventBus,
                                   final AnnotationEditView view,
                                   final RestFactory restFactory,
                                   final ChooserPresenter statusPresenter,
                                   final ChooserPresenter assignedToPresenter,
                                   final ChooserPresenter commentPresenter,
                                   final LinkedEventPresenter linkedEventPresenter,
                                   final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.statusPresenter = statusPresenter;
        this.assignedToPresenter = assignedToPresenter;
        this.commentPresenter = commentPresenter;
        this.linkedEventPresenter = linkedEventPresenter;
        this.clientSecurityContext = clientSecurityContext;
        getView().setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(statusPresenter.addDataSelectionHandler(e -> {
            final String selected = statusPresenter.getSelected();
            changeStatus(selected, true);
        }));
        registerHandler(assignedToPresenter.addDataSelectionHandler(e -> {
            final String selected = assignedToPresenter.getSelected();
            changeAssignedTo(selected, true);
        }));
        registerHandler(commentPresenter.addDataSelectionHandler(e -> {
            final String selected = commentPresenter.getSelected();
            changeComment(selected);
        }));
    }

    private void changeTitle(final String selected, final boolean addEntry) {
        if (hasChanged(currentTitle, selected)) {
            currentTitle = selected;
            getView().setTitle(selected);

            if (addEntry && annotationDetail != null) {
                final CreateEntryRequest request = new CreateEntryRequest(annotationDetail.getAnnotation(), Annotation.TITLE, selected);
                addEntry(request);
            }
        }
    }

    private void changeSubject(final String selected, final boolean addEntry) {
        if (hasChanged(currentSubject, selected)) {
            currentSubject = selected;
            getView().setSubject(selected);

            if (addEntry && annotationDetail != null) {
                final CreateEntryRequest request = new CreateEntryRequest(annotationDetail.getAnnotation(), Annotation.SUBJECT, selected);
                addEntry(request);
            }
        }
    }

    private boolean hasChanged(final String oldValue, final String newValue) {
        // Treat empty strings as null so null and "" are treated as equal
        return !Objects.equals(
                (oldValue != null && oldValue.isEmpty() ? null : oldValue),
                (newValue != null && newValue.isEmpty() ? null : newValue));
    }

    private void changeStatus(final String selected, final boolean addEntry) {
        if (hasChanged(currentStatus, selected)) {
            currentStatus = selected;
            getView().setStatus(selected);
            HidePopupEvent.fire(this, statusPresenter, true, true);

            if (addEntry && annotationDetail != null) {
                final CreateEntryRequest request = new CreateEntryRequest(annotationDetail.getAnnotation(), Annotation.STATUS, selected);
                addEntry(request);
            }
        }
    }

    private void changeAssignedTo(final String selected, final boolean addEntry) {
        if (hasChanged(currentAssignedTo, selected)) {
            currentAssignedTo = selected;
            getView().setAssignedTo(selected);
            HidePopupEvent.fire(this, assignedToPresenter, true, true);

            if (addEntry && annotationDetail != null) {
                final CreateEntryRequest request = new CreateEntryRequest(annotationDetail.getAnnotation(), Annotation.ASSIGNED_TO, selected);
                addEntry(request);
            }
        }
    }

    private void changeComment(final String selected) {
        if (selected != null && hasChanged(getView().getComment(), selected)) {
            getView().setComment(getView().getComment() + selected);
            HidePopupEvent.fire(this, commentPresenter, true, true);
        }
    }

    private void addEntry(final CreateEntryRequest request) {
        final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
        final Rest<AnnotationDetail> rest = restFactory.create();
        rest.onSuccess(this::read).call(annotationResource).createEntry(request);
    }

    public void show(final Annotation annotation, final List<EventId> linkedEvents) {
        boolean ok = true;
        if (annotation == null) {
            ok = false;
            AlertEvent.fireError(this, "No sample annotation has been provided to open the editor", null);
        } else if (annotation.getId() == null && (linkedEvents == null || linkedEvents.size() == 0)) {
            ok = false;
            AlertEvent.fireError(this, "No stream id has been provided for the annotation", null);
        }

        if (ok) {
            this.linkedEvents = linkedEvents;
            this.initialComment = annotation.getComment();
            readAnnotation(annotation);

            final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
            final Rest<AnnotationDetail> rest = restFactory.create();
            rest.onSuccess(this::edit).call(annotationResource).get(annotation.getId());
        }
    }

    private void edit(final AnnotationDetail annotationDetail) {
        read(annotationDetail);

        // Set the initial comment if one has been provided and if this is a new annotation.
        if (annotationDetail == null || annotationDetail.getAnnotation() == null || annotationDetail.getAnnotation().getId() == null) {
            if (initialComment != null) {
                getView().setComment(initialComment);
            }
        }

        final PopupUiHandlers internalPopupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                hide();
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        };

        final PopupSize popupSize = new PopupSize(800, 600, true);
        ShowPopupEvent.fire(this,
                this,
                PopupType.CLOSE_DIALOG,
                null,
                popupSize, getCaption(annotationDetail),
                internalPopupUiHandlers,
                false);

        getView().focusComment();
    }

    private String getCaption(final AnnotationDetail annotationDetail) {
        if (annotationDetail == null) {
            return "Create Annotation";
        }
        return "Edit Annotation #" + annotationDetail.getAnnotation().getId();
    }

    private void hide() {
        HidePopupEvent.fire(AnnotationEditPresenter.this, AnnotationEditPresenter.this);
    }

    private void read(final AnnotationDetail annotationDetail) {
        if (annotationDetail != null) {
            if (this.annotationDetail == null) {
                // If this is an existing annotation then change the dialog caption.
                RenamePopupEvent.fire(this, this, getCaption(annotationDetail));
            }
            this.annotationDetail = annotationDetail;

            getView().setButtonText("Comment");

            readAnnotation(annotationDetail.getAnnotation());

            updateHistory(annotationDetail);

        } else {
            getView().setButtonText("Create");
        }

        if (currentStatus == null) {
            final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
            final Rest<List<String>> rest = restFactory.create();
            rest.onSuccess(values -> {
                if (currentStatus == null && values != null && values.size() > 0) {
                    changeStatus(values.get(0), false);
                }
            }).call(annotationResource).getStatus(null);
        }

        if (currentTitle == null || currentTitle.trim().length() == 0) {
            changeTitle(null, false);
        }

        if (currentSubject == null || currentSubject.trim().length() == 0) {
            changeSubject(null, false);
        }
    }

    private void updateHistory(final AnnotationDetail annotationDetail) {
        if (annotationDetail != null) {
            final List<AnnotationEntry> entries = annotationDetail.getEntries();
            if (entries != null) {
                final Date now = new Date();
                final Map<String, Optional<String>> currentValues = new HashMap<>();

                final SafeHtmlBuilder html = new SafeHtmlBuilder();
                final StringBuilder text = new StringBuilder();
                html.appendHtmlConstant(HISTORY_INNER_START);
                entries.forEach(entry -> {
                    final Optional<String> currentValue = currentValues.get(entry.getEntryType());

                    addEntryText(text, entry, currentValue);
                    addEntryHtml(html, entry, currentValue, now);

                    // Remember the previous value.
                    currentValues.put(entry.getEntryType(), Optional.ofNullable(entry.getData()));
                });

                html.appendHtmlConstant(HISTORY_INNER_END);

                final HTML panel = new HTML(html.toSafeHtml());
                panel.setStyleName("annotationHistoryOuter");
                panel.addMouseDownHandler(e -> {
                    // If the user has clicked on a link then consume the event.
                    final Element target = e.getNativeEvent().getEventTarget().cast();
                    if (target.hasTagName("u")) {
                        final String link = target.getAttribute("link");
                        if (link != null) {
                            final Hyperlink hyperlink = Hyperlink.create(link);
                            if (hyperlink != null) {
                                HyperlinkEvent.fire(this, hyperlink);
                            }
                        }
                    }
                });

                final TextArea textCopy = new TextArea();
                textCopy.setStyleName("annotationHistoryText");
                textCopy.setText(text.toString());

                final Button copyToClipboard = new Button("Copy History");
                copyToClipboard.addStyleName("annotationHistoryCopyButton");
                copyToClipboard.addClickHandler(e -> {
                    textCopy.setFocus(true);
                    textCopy.selectAll();
                    boolean success = copy();
                    if (!success) {
                        AlertEvent.fireError(AnnotationEditPresenter.this, "Unable to copy", null);
                    }
                });

                final FlowPanel verticalPanel = new FlowPanel();
                verticalPanel.setStyleName("annotationHistoryContainer");
                verticalPanel.add(panel);
                verticalPanel.add(textCopy);
                verticalPanel.add(copyToClipboard);

                getView().setHistoryView(verticalPanel);

                // Scroll the history to the bottom after update.
                Scheduler.get().scheduleDeferred(() -> panel.getElement().setScrollTop(panel.getElement().getScrollHeight()));
            }
        }
    }

    private void addEntryText(final StringBuilder text, final AnnotationEntry entry, final Optional<String> currentValue) {
        final String value = entry.getData();

        if (Annotation.COMMENT.equals(entry.getEntryType())) {
            text.append(ClientDateUtil.toISOString(entry.getCreateTime()));
            text.append(", ");
            text.append(entry.getCreateUser());
            text.append(", commented ");
            quote(text, value);
            text.append("\n");

        } else if (Annotation.LINK.equals(entry.getEntryType()) || Annotation.UNLINK.equals(entry.getEntryType())) {
            text.append(ClientDateUtil.toISOString(entry.getCreateTime()));
            text.append(", ");
            text.append(entry.getCreateUser());
            text.append(", ");
            text.append(entry.getEntryType().toLowerCase());
            text.append("ed ");
            quote(text, value);
            text.append("\n");

        } else if (Annotation.ASSIGNED_TO.equals(entry.getEntryType())) {
            if (currentValue != null && currentValue.isPresent()) {
                text.append(ClientDateUtil.toISOString(entry.getCreateTime()));
                text.append(", ");
                text.append(entry.getCreateUser());
                text.append(",");
                if (entry.getCreateUser().equals(currentValue.get())) {
                    text.append(" removed their assignment");
                } else {
                    text.append(" unassigned ");
                    quote(text, currentValue.get());
                }
                text.append("\n");
            }

            if (value != null && value.trim().length() > 0) {
                text.append(ClientDateUtil.toISOString(entry.getCreateTime()));
                text.append(", ");
                text.append(entry.getCreateUser());
                text.append(",");

                if (entry.getCreateUser().equals(value)) {
                    text.append(" self-assigned this");
                } else {
                    text.append(" assigned ");
                    quote(text, value);
                }
                text.append("\n");
            }
        } else {
            text.append(ClientDateUtil.toISOString(entry.getCreateTime()));
            text.append(", ");
            text.append(entry.getCreateUser());
            text.append(",");

            if (currentValue != null && currentValue.isPresent()) {
                text.append(" changed the ");
            } else {
                text.append(" set the ");
            }
            text.append(entry.getEntryType().toLowerCase());

            if (currentValue != null && currentValue.isPresent()) {
                text.append(" from ");
                quote(text, currentValue.get());
                text.append(" to ");
                quote(text, value);
            } else {
                text.append(" to ");
                quote(text, value);
            }
            text.append("\n");
        }
    }

    private void addEntryHtml(final SafeHtmlBuilder html, final AnnotationEntry entry, final Optional<String> currentValue, final Date now) {
        final String value = entry.getData();

        if (Annotation.COMMENT.equals(entry.getEntryType())) {
            html.appendHtmlConstant(HISTORY_LINE_START);
            html.appendHtmlConstant(HISTORY_COMMENT_BORDER_START);
            html.appendHtmlConstant(HISTORY_COMMENT_HEADER_START);
            bold(html, entry.getCreateUser());
            html.appendEscaped(" commented ");
            html.append(getDurationLabel(entry.getCreateTime(), now));
            html.appendHtmlConstant(HISTORY_COMMENT_HEADER_END);
            html.appendHtmlConstant(HISTORY_COMMENT_BODY_START);
            html.appendEscaped(entry.getData());
            html.appendHtmlConstant(HISTORY_COMMENT_BODY_END);
            html.appendHtmlConstant(HISTORY_COMMENT_BORDER_END);
            html.appendHtmlConstant(HISTORY_LINE_END);

        } else if (Annotation.LINK.equals(entry.getEntryType()) || Annotation.UNLINK.equals(entry.getEntryType())) {
            html.appendHtmlConstant(HISTORY_LINE_START);
            html.appendHtmlConstant(HISTORY_ITEM_START);
            bold(html, entry.getCreateUser());
            html.appendEscaped(" ");
            html.appendEscaped(entry.getEntryType().toLowerCase());
            html.appendEscaped("ed ");
            link(html, entry.getData());
            html.appendEscaped(" ");
            html.append(getDurationLabel(entry.getCreateTime(), now));
            html.appendHtmlConstant(HISTORY_ITEM_END);
            html.appendHtmlConstant(HISTORY_LINE_END);

        } else {
            // Remember initial values but don't show them unless they change.
            if (currentValue != null) {
                if (Annotation.ASSIGNED_TO.equals(entry.getEntryType())) {
                    if (currentValue.isPresent()) {
                        html.appendHtmlConstant(HISTORY_LINE_START);
                        html.appendHtmlConstant(HISTORY_ITEM_START);
                        bold(html, entry.getCreateUser());
                        if (entry.getCreateUser().equals(currentValue.get())) {
                            html.appendEscaped(" removed their assignment");
                        } else {
                            html.appendEscaped(" unassigned ");
                            bold(html, getValueString(currentValue.get()));
                        }
                        html.appendEscaped(" ");
                        html.append(getDurationLabel(entry.getCreateTime(), now));
                        html.appendHtmlConstant(HISTORY_ITEM_END);
                        html.appendHtmlConstant(HISTORY_LINE_END);
                    }

                    if (value != null && value.trim().length() > 0) {
                        html.appendHtmlConstant(HISTORY_LINE_START);
                        html.appendHtmlConstant(HISTORY_ITEM_START);
                        bold(html, entry.getCreateUser());
                        if (entry.getCreateUser().equals(value)) {
                            html.appendEscaped(" self-assigned this");
                        } else {
                            html.appendEscaped(" assigned ");
                            bold(html, getValueString(value));
                        }
                        html.appendEscaped(" ");
                        html.append(getDurationLabel(entry.getCreateTime(), now));
                        html.appendHtmlConstant(HISTORY_ITEM_END);
                        html.appendHtmlConstant(HISTORY_LINE_END);
                    }

                } else {
                    html.appendHtmlConstant(HISTORY_LINE_START);
                    html.appendHtmlConstant(HISTORY_ITEM_START);
                    bold(html, entry.getCreateUser());
                    if (currentValue.isPresent()) {
                        html.appendEscaped(" changed the ");
                    } else {
                        html.appendEscaped(" set the ");
                    }
                    html.appendEscaped(entry.getEntryType().toLowerCase());
                    html.appendEscaped(" ");

                    if (currentValue.isPresent()) {
                        del(html, getValueString(currentValue.get()));
                        html.appendEscaped(" ");
                        ins(html, getValueString(value));

                    } else {
                        html.appendEscaped(getValueString(value));
                    }

                    html.appendEscaped(" ");
                    html.append(getDurationLabel(entry.getCreateTime(), now));
                    html.appendHtmlConstant(HISTORY_ITEM_END);
                    html.appendHtmlConstant(HISTORY_LINE_END);
                }
            }
        }
    }

    private static native boolean copy() /*-{
        return $doc.execCommand('copy');
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
            final Hyperlink hyperlink = new Hyperlink.Builder()
                    .text(value)
                    .href("?id=" + eventId.getStreamId() + "&partNo=1&recordNo=" + eventId.getEventId())
                    .type(HyperlinkType.DATA.name().toLowerCase())
                    .build();
            if (!hyperlink.getText().trim().isEmpty()) {
                builder.appendHtmlConstant("<b><u link=\"" + hyperlink.toString() + "\">" + value + "</u></b>");
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

    private void readAnnotation(final Annotation annotation) {
        currentId = annotation.getId();
        currentTitle = annotation.getTitle();
        currentSubject = annotation.getSubject();
        currentStatus = annotation.getStatus();
        currentAssignedTo = annotation.getAssignedTo();
        getView().setTitle(currentTitle);
        getView().setSubject(currentSubject);
        getView().setStatus(currentStatus);
        getView().setAssignedTo(currentAssignedTo);
        getView().setAssignYourselfVisible(hasChanged(currentAssignedTo, clientSecurityContext.getUserId()));
    }

    private SafeHtml getDurationLabel(final long time, final Date now) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<span class=\"annotationDurationLabel\" title=\"" + ClientDateUtil.toISOString(time) + "\">");
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
        changeTitle(getView().getTitle(), true);
    }

    @Override
    public void onSubjectChange() {
        changeSubject(getView().getSubject(), true);
    }

    @Override
    public void showStatusChooser(final Element element) {
        statusPresenter.setDataSupplier((filter, consumer) -> {
            final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
            final Rest<List<String>> rest = restFactory.create();
            rest.onSuccess(consumer).call(annotationResource).getStatus(filter);
        });
        statusPresenter.clearFilter();
        statusPresenter.setSelected(currentStatus);
        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.fire(this, statusPresenter, PopupType.POPUP, popupPosition, null, element);
    }

    @Override
    public void showAssignedToChooser(final Element element) {
        if (currentAssignedTo == null) {
            assignedToPresenter.setClearSelectionText(null);
        } else {
            assignedToPresenter.setClearSelectionText("Clear");
        }
        assignedToPresenter.setDataSupplier((filter, consumer) -> {
            final UserResource userResource = GWT.create(UserResource.class);
            final Rest<List<String>> rest = restFactory.create();
            rest.onSuccess(consumer).call(userResource).getAssociates(filter);
        });
        assignedToPresenter.clearFilter();
        assignedToPresenter.setSelected(currentAssignedTo);
        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.fire(this, assignedToPresenter, PopupType.POPUP, popupPosition, null, element);
    }

    @Override
    public void showCommentChooser(final Element element) {
        commentPresenter.setDataSupplier((filter, consumer) -> {
            final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
            final Rest<List<String>> rest = restFactory.create();
            rest.onSuccess(consumer).call(annotationResource).getComment(filter);
        });
        commentPresenter.clearFilter();
        commentPresenter.setSelected(getView().getComment());
        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                element.getAbsoluteTop() + element.getClientHeight() + 2);
        ShowPopupEvent.fire(this, commentPresenter, PopupType.POPUP, popupPosition, null, element);
    }

    @Override
    public void assignYourself() {
        changeAssignedTo(clientSecurityContext.getUserId(), true);
    }

    @Override
    public void create() {
        final String comment = getView().getComment();
        if (comment != null && comment.length() > 0) {
            final Annotation annotation = new Annotation();
            annotation.setId(currentId);
            annotation.setTitle(currentTitle);
            annotation.setSubject(currentSubject);
            annotation.setStatus(currentStatus);
            annotation.setAssignedTo(currentAssignedTo);
            annotation.setComment(comment);
            annotation.setHistory(comment);

            final CreateEntryRequest request = new CreateEntryRequest(annotation, Annotation.COMMENT, comment, linkedEvents);
            addEntry(request);
            getView().setComment("");
        } else {
            AlertEvent.fireWarn(this, "Please enter a comment", null);
        }
    }

    @Override
    public void showLinkedEvents() {
        if (annotationDetail != null && annotationDetail.getAnnotation() != null && annotationDetail.getAnnotation().getId() != null) {
            linkedEventPresenter.edit(annotationDetail.getAnnotation(), refresh -> {
                if (refresh) {
                    final AnnotationResource annotationResource = GWT.create(AnnotationResource.class);
                    final Rest<AnnotationDetail> rest = restFactory.create();
                    rest.onSuccess(this::updateHistory).call(annotationResource).get(annotationDetail.getAnnotation().getId());
                }
            });
        } else {
            AlertEvent.fireError(this, "The annotation must be created before events are linked", null);
        }
    }

    public interface AnnotationEditView extends View, HasUiHandlers<AnnotationEditUiHandlers> {
        String getTitle();

        void setTitle(String title);

        String getSubject();

        void setSubject(String subject);

        void setStatus(String status);

        void setAssignedTo(String assignedTo);

        String getComment();

        void setComment(String comment);

        void setHistoryView(Widget view);

        void setButtonText(String text);

        void setAssignYourselfVisible(boolean visible);

        void focusComment();
    }
}