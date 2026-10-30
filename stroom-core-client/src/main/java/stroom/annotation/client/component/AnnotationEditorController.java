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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.client.DurationLabel;
import stroom.annotation.client.Lozenge;
import stroom.annotation.client.component.AnnotationEditor.AnnotationEditAction;
import stroom.annotation.client.component.ChooserController.DataSupplier;
import stroom.annotation.client.event.AnnotationChangeEvent;
import stroom.annotation.client.event.EditAnnotationEvent;
import stroom.annotation.shared.AddTag;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationEntryType;
import stroom.annotation.shared.AnnotationIdentity;
import stroom.annotation.shared.AnnotationTable;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.ChangeAnnotationEntryRequest;
import stroom.annotation.shared.ChangeAssignedTo;
import stroom.annotation.shared.ChangeComment;
import stroom.annotation.shared.ChangeRetentionPeriod;
import stroom.annotation.shared.ChangeSubject;
import stroom.annotation.shared.ChangeTitle;
import stroom.annotation.shared.DeleteAnnotationEntryRequest;
import stroom.annotation.shared.EntryValue;
import stroom.annotation.shared.FetchAnnotationEntryRequest;
import stroom.annotation.shared.FindAnnotationTagCriteria;
import stroom.annotation.shared.RemoveTag;
import stroom.annotation.shared.SetTag;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.annotation.shared.StringEntryValue;
import stroom.annotation.shared.UserRefEntryValue;
import stroom.data.client.event.ShowDataEvent;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.docref.DocRef;
import stroom.pipeline.shared.SourceLocation;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.component.UserRefPopupController;
import stroom.security.shared.FindUserContext;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.util.client.Clipboard;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// An annotation's Annotation tab ([AnnotationEditor]): shows the annotation, makes each change to it
/// straight away on the server (its title, subject, status, assignment, labels, collections,
/// retention period and comments), and shows its history, whose comments and references can be
/// followed, edited and deleted. Read only as its document is: everything can be read, nothing
/// changed.
public class AnnotationEditorController extends Controller implements Focus {

    private final AnnotationEditor view;
    private final AnnotationResourceClient annotationResourceClient;
    private final Provider<ChooserController<AnnotationTag>> chooserProvider;
    private final UserRefPopupController assignedToPicker;
    private final Provider<MultiChooserController<AnnotationTag>> multiChooserProvider;
    private final OpenChooser openChooser = new OpenChooser();
    private final ClientSecurityContext clientSecurityContext;
    private final DateTimeFormatter dateTimeFormatter;
    private final Provider<RetentionPeriodController> retentionPeriodProvider;
    private final Provider<CommentEditorController> commentEditorProvider;

    private Annotation annotation;
    private DocRef annotationRef;
    private AnnotationIdentity annotationIdentity;
    private Runnable onTitleChange = () -> {
    };
    private Runnable onDelete = () -> {
    };

    private AnnotationTag currentStatus;
    private UserRef currentAssignedTo;
    private List<AnnotationTag> currentLabels;
    private List<AnnotationTag> currentCollections;
    private SimpleDuration currentRetentionPeriod;

    private String currentTitle;
    private String currentSubject;

    // When re-reading the entity purely to update local state (e.g. after a title change), suppress
    // onRead()'s history refresh - the change's own success callback already refreshes history with the
    // persisted data, so refreshing again here would be a redundant fetch of momentarily-stale data.
    private boolean suppressHistoryUpdate;

    // The history as text, for Copy History
    private String historyText = "";

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The tab.
    /// @param annotationResourceClient Fetches and changes the annotation.
    /// @param chooserProvider          Makes the status and standard comment choosers.
    /// @param assignedToPicker         Picks who the annotation is assigned to.
    /// @param multiChooserProvider     Makes the label and collection choosers.
    /// @param clientSecurityContext    Says who the user is, to assign themselves.
    /// @param dateTimeFormatter        Formats the history's times.
    /// @param retentionPeriodProvider  Makes the retention period dialog.
    /// @param durationLabel            Writes when each history entry was made.
    /// @param commentEditorProvider      Makes the dialog that edits a comment.
    @Inject
    public AnnotationEditorController(final EventBus eventBus,
                                      final AnnotationEditor view,
                                      final AnnotationResourceClient annotationResourceClient,
                                      final Provider<ChooserController<AnnotationTag>> chooserProvider,
                                      final UserRefPopupController assignedToPicker,
                                      final Provider<MultiChooserController<AnnotationTag>> multiChooserProvider,
                                      final ClientSecurityContext clientSecurityContext,
                                      final DateTimeFormatter dateTimeFormatter,
                                      final Provider<RetentionPeriodController> retentionPeriodProvider,
                                      final DurationLabel durationLabel,
                                      final Provider<CommentEditorController> commentEditorProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(assignedToPicker);
        this.annotationResourceClient = annotationResourceClient;
        this.chooserProvider = chooserProvider;
        this.assignedToPicker = assignedToPicker;
        this.multiChooserProvider = multiChooserProvider;
        this.clientSecurityContext = clientSecurityContext;
        this.dateTimeFormatter = dateTimeFormatter;
        this.retentionPeriodProvider = retentionPeriodProvider;
        this.commentEditorProvider = commentEditorProvider;

        view.setOnAction(this::onAction);
        view.setOnTitleChange(this::onTitleChange);
        view.setOnSubjectChange(this::onSubjectChange);
        final AnnotationHistory history = view.getHistory();
        history.setDurationLabel(durationLabel);
        history.setOnOpenEvent(eventId -> ShowDataEvent.fire(this, SourceLocation.fromEventId(eventId)));
        history.setOnOpenAnnotation(annotationId -> EditAnnotationEvent.fire(this, annotationId));
        // A group expanded or collapsed is shown so once the history is fetched again
        history.setOnExpandedChange(this::updateHistory);
        history.setOnEntryMenu(this::showEntryEditMenu);
        assignedToPicker.setContext(FindUserContext.ANNOTATION_ASSIGNMENT);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    private void onAction(final AnnotationEditAction action, final Element element) {
        switch (action) {
            case STATUS -> showStatusChooser(element);
            case ASSIGNED_TO -> showAssignedToChooser(element);
            case ASSIGN_YOURSELF -> assignYourself();
            case LABELS -> showLabelChooser(element);
            case COLLECTIONS -> showCollectionChooser(element);
            case RETENTION_PERIOD -> showRetentionPeriodChooser(element);
            case CHOOSE_COMMENT -> showCommentChooser(element);
            case CREATE -> create();
            case COPY_HISTORY -> copyHistory();
            case DELETE -> onDelete();
        }
    }

    // Whether the annotation may not be changed, as its document says
    private boolean isReadOnly() {
        return context(Contexts.READ_ONLY);

    }

    // Gives a chooser the tags of a type that match its filter
    private DataSupplier<AnnotationTag> tagSupplier(final AnnotationTagType annotationTagType) {
        return (filter, consumer) -> {
            final FindAnnotationTagCriteria criteria = createCriteria(annotationTagType, filter);
            annotationResourceClient.findAnnotationTags(
                    this,
                    criteria,
                    guard(values -> {
                        if (values != null) {
                            consumer.accept(values.getValues());
                        }
                    }),
                    new DefaultErrorHandler(this, null), taskMonitor());
        };
    }

    private FindAnnotationTagCriteria createCriteria(final AnnotationTagType annotationTagType,
                                                    final String filter) {
        // The type says what kind of tag is being chosen, so it is a field of the criteria rather
        // than a term; the filter is the user's own text and goes to the server unparsed.
        // See FindAnnotationTagCriteria.
        return new FindAnnotationTagCriteria(annotationTagType, filter);
    }

    private void changeTitle(final String selected) {
        if (hasChanged(currentTitle, selected)) {
            currentTitle = selected;
            final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                    annotationRef,
                    new ChangeTitle(selected));
            change(request);

            // Re-read to update the entity's name (used for the tab title) and refresh the view, but
            // suppress the history refresh - change()'s success callback refreshes it once with the
            // persisted data.
            suppressHistoryUpdate = true;
            try {
                read(annotation.asDocRef(), annotation.copy().name(selected).build());
            } finally {
                suppressHistoryUpdate = false;
            }
            onTitleChange.run();
        }
    }

    private void setTitle(final String title) {
        currentTitle = title;
        view.setAnnotationTitle(title);
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
        view.setSubject(subject);
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
        view.setStatus(status);
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
        assignedToPicker.resolve(assignedTo, userRef -> {
            currentAssignedTo = userRef;
            view.setAssignedTo(userRef);
            view.setAssignYourselfEnabled(!Objects.equals(userRef, clientSecurityContext.getUserRef()));
            assignedToPicker.setSelected(currentAssignedTo);
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
        view.setLabels(currentLabels);
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
        view.setCollections(currentCollections);
    }

    private void showRetentionPeriodChooser(final Element element) {
        if (isReadOnly()) {
            return;
        }
        retentionPeriodProvider.get().edit(this, currentRetentionPeriod, this::changeRetentionPeriod);
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
        view.setRetentionPeriod(retentionPeriod == null
                ? "Forever"
                : retentionPeriod.toLongString());
    }

    // Adds a standard comment to the comment being written, returning whether it did
    private boolean changeComment(final AnnotationTag selected) {
        if (selected != null && hasChanged(view.getComment(), selected.getTagText())) {
            view.setComment(view.getComment() + selected.getTagText());
            return true;
        }
        return false;
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
        annotationResourceClient.change(request, guard(consumer), taskMonitor());
    }

    /// Fetches the history again.
    public void updateHistory() {
        annotationResourceClient.getAnnotationEntries(annotationRef, guard(this::updateHistory), taskMonitor());
    }

    private void updateHistory(final List<AnnotationEntry> entries) {
        if (entries != null) {
            final StringBuilder text = new StringBuilder();
            for (final AnnotationEntry entry : entries) {
                addEntryText(text, entry);
            }
            historyText = text.toString();
            view.setHistory(entries, System.currentTimeMillis(), isReadOnly());
        }
    }

    private void copyHistory() {
        Clipboard.writeText(historyText, () -> {
        }, () -> AlertEvent.fireError(
                this,
                "Unable to copy",
                null));
    }

    private void showEntryEditMenu(final long id,
                                   final AnnotationEntryType entryType,
                                   final String name,
                                   final Element opener,
                                   final PopupPosition popupPosition) {
        // A read-only annotation's entries can't be edited or deleted
        if (isReadOnly()) {
            return;
        }
        final List<Item> menuItems = new ArrayList<>();
        if (AnnotationEntryType.COMMENT.equals(entryType)) {
            final IconMenuItem editItem = new IconMenuItem.Builder()
                    .text("Edit Comment")
                    .icon(SvgImage.EDIT)
                    .command(() -> editComment(id))
                    .build();
            menuItems.add(editItem);
        }

        final IconMenuItem deleteItem = new IconMenuItem.Builder()
                .text("Delete Entry")
                .icon(SvgImage.DELETE)
                .command(() -> deleteEntry(id, entryType))
                .build();
        menuItems.add(deleteItem);

        ShowMenuEvent
                .builder()
                .name(name)
                .items(menuItems)
                .popupPosition(popupPosition)
                .opener(opener)
                .fire(this);
    }

    private void editComment(final long id) {
        final FetchAnnotationEntryRequest request = new FetchAnnotationEntryRequest(annotationRef, id);
        annotationResourceClient.fetchAnnotationEntry(request, guard(annotationEntry -> {
            if (annotationEntry.getEntryValue() instanceof final StringEntryValue value) {
                commentEditorProvider.get().edit(this, value.getValue(), (text, e) ->
                        changeComment(id, text, annotationEntry.getEntryType(), e));
            }
        }), taskMonitor());
    }

    private void changeComment(final long id,
                               final String text,
                               final AnnotationEntryType annotationEntryType,
                               final HidePopupRequestEvent e) {
        final ChangeAnnotationEntryRequest request = new ChangeAnnotationEntryRequest(
                annotationIdentity,
                id,
                annotationEntryType,
                text);
        annotationResourceClient.changeAnnotationEntry(
                request,
                guard(ignored -> afterChangeComment(e)),
                // If the change fails, say why and let the user try again or cancel
                RestErrorHandler.forPopup(this, e),
                taskMonitor());
    }

    private void afterChangeComment(final HidePopupRequestEvent e) {
        try {
            AnnotationChangeEvent.fire(this, annotationRef);
            updateHistory();
        } finally {
            e.hide();
        }
    }

    private void deleteEntry(final long id, final AnnotationEntryType annotationEntryType) {
        ConfirmEvent.fire(this, "Are you sure you want to delete this entry?", ok -> {
            if (ok) {
                final DeleteAnnotationEntryRequest request = new DeleteAnnotationEntryRequest(
                        annotationIdentity, annotationEntryType, id);
                annotationResourceClient.deleteAnnotationEntry(
                        request, guard(result -> {
                            AnnotationChangeEvent.fire(this, annotationRef);
                            updateHistory();
                        }), taskMonitor());
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

    private boolean areSameUser(final UserRef entryUser, final EntryValue entryValue) {
        if (entryValue instanceof final UserRefEntryValue userRefEntryValue) {
            return Objects.equals(entryUser, userRefEntryValue.getUserRef());
        } else {
            return entryUser == null && entryValue == null;
        }
    }

    private void quote(final StringBuilder text, final String value) {
        text.append("'");
        if (value != null) {
            text.append(value);
        }
        text.append("'");
    }

    /// Shows an annotation, and fetches its history.
    ///
    /// @param docRef     The annotation.
    /// @param annotation The annotation.
    public void read(final DocRef docRef, final Annotation annotation) {
        this.annotation = annotation;
        this.annotationRef = annotation.asDocRef();
        this.annotationIdentity = annotation.asAnnotationIdentity();
        // Read only (watched): everything can be read, nothing changed (this also refuses each
        // change, as some are made straight to the server)
        this.currentStatus = annotation.getStatus();
        this.currentAssignedTo = annotation.getAssignedTo();

        view.setId(annotation.getId());
        view.setButtonText("Comment");

        setTitle(annotation.getName());
        setSubject(annotation.getSubject());
        setStatus(annotation.getStatus());
        setAssignedTo(annotation.getAssignedTo());
        setLabels(annotation.getLabels());
        setCollections(annotation.getCollections());
        setRetentionPeriod(annotation.getRetentionPeriod());

        if (!suppressHistoryUpdate) {
            updateHistory();
        }
    }

    private void onTitleChange() {
        if (isReadOnly()) {
            return;
        }
        changeTitle(view.getAnnotationTitle());
    }

    private void onSubjectChange() {
        if (isReadOnly()) {
            return;
        }
        changeSubject(view.getSubject());
    }

    private void showStatusChooser(final Element element) {
        if (isReadOnly()) {
            return;
        }
        if (openChooser.closeIfOpenedFrom(element)) {
            return;
        }
        final ChooserController<AnnotationTag> chooser = chooserProvider.get();
        chooser.setDataSupplier(tagSupplier(AnnotationTagType.STATUS));
        chooser.setDisplayValueFunction(at -> SafeHtmlUtils.fromString(at.getName()));
        chooser.setAccessibleName("Statuses");
        chooser.setSelected(currentStatus);
        chooser.show(this, element, "Choose Status", selected -> {
            if (!Objects.equals(currentStatus, selected)) {
                changeStatus(selected);
                chooser.hide();
            }
        });
        openChooser.opened(chooser, element);
    }

    private void showAssignedToChooser(final Element element) {
        if (isReadOnly()) {
            return;
        }
        assignedToPicker.setSelected(currentAssignedTo);
        assignedToPicker.show(this::changeAssignedTo);
    }

    private void showLabelChooser(final Element element) {
        if (isReadOnly()) {
            return;
        }
        if (openChooser.closeIfOpenedFrom(element)) {
            return;
        }
        final MultiChooserController<AnnotationTag> chooser = multiChooserProvider.get();
        chooser.setDataSupplier(tagSupplier(AnnotationTagType.LABEL));
        chooser.setDisplayValueFunction(Lozenge::create);
        chooser.setAccessibleName("Labels");
        chooser.setSelectedItems(currentLabels);
        chooser.show(this, element, "Choose Labels", this::changeAnnotationLabels);
        openChooser.opened(chooser, element);
    }

    private void showCollectionChooser(final Element element) {
        if (isReadOnly()) {
            return;
        }
        if (openChooser.closeIfOpenedFrom(element)) {
            return;
        }
        final MultiChooserController<AnnotationTag> chooser = multiChooserProvider.get();
        chooser.setDataSupplier(tagSupplier(AnnotationTagType.COLLECTION));
        chooser.setDisplayValueFunction(Lozenge::create);
        chooser.setAccessibleName("Collections");
        chooser.setSelectedItems(currentCollections);
        chooser.show(this, element, "Choose Collections", this::changeAnnotationCollections);
        openChooser.opened(chooser, element);
    }

    private void showCommentChooser(final Element element) {
        if (isReadOnly()) {
            return;
        }
        if (openChooser.closeIfOpenedFrom(element)) {
            return;
        }
        final ChooserController<AnnotationTag> chooser = chooserProvider.get();
        chooser.setDataSupplier(tagSupplier(AnnotationTagType.COMMENT));
        chooser.setDisplayValueFunction(at -> SafeHtmlUtils.fromString(at.getName()));
        chooser.setAccessibleName("Standard comments");
        chooser.setTooltipFunction(AnnotationTag::getTagText);
        chooser.show(this, element, "Choose Comment", selected -> {
            if (changeComment(selected)) {
                chooser.hide();
            }
        });
        openChooser.opened(chooser, element);
    }

    private void assignYourself() {
        if (isReadOnly()) {
            return;
        }
        changeAssignedTo(clientSecurityContext.getUserRef());
    }

    private void create() {
        if (isReadOnly()) {
            return;
        }
        final String comment = view.getComment();
        if (comment != null && !comment.isEmpty()) {
            final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                    annotationRef,
                    new ChangeComment(comment));
            change(request);
            view.setComment("");
        } else {
            AlertEvent.fireWarn(this, "Please enter a comment", null);
        }
    }

    private void onDelete() {
        if (isReadOnly()) {
            return;
        }
        ConfirmEvent.fire(this, "Are you sure you want to delete this annotation?", ok -> {
            if (ok) {
                annotationResourceClient.delete(annotationRef, guard(result -> {
                    if (result) {
                        AnnotationChangeEvent.fire(this, null);
                        onDelete.run();
                    }
                }), taskMonitor());
            }
        });
    }

    @Override
    public void focus() {
        view.focus();
    }


    /// @param initialComment The comment to start with, e.g. the text of the data annotated.
    public void setInitialComment(final String initialComment) {
        view.setComment(initialComment);
    }

    /// @return The annotation's title as it now is (after any change), or null before one is read.
    public String getAnnotationTitle() {
        return annotation == null
                ? null
                : annotation.getName();
    }

    /// @param onTitleChange Run when the annotation's title changes, e.g. to retitle its tab.
    public void setOnTitleChange(final Runnable onTitleChange) {
        this.onTitleChange = onTitleChange;
    }

    /// @param onDelete Run when the annotation is deleted, e.g. to close its tab.
    public void setOnDelete(final Runnable onDelete) {
        this.onDelete = onDelete;
    }
}
