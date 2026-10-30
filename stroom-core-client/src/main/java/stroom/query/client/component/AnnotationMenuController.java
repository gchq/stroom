/*
 * Copyright 2020 Crown Copyright
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

package stroom.query.client.component;

import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.client.component.ChangeAssigneeController;
import stroom.annotation.client.component.ChangeStatusController;
import stroom.annotation.client.event.AnnotationChangeEvent;
import stroom.annotation.client.event.CreateAnnotationEvent;
import stroom.annotation.client.event.EditAnnotationEvent;
import stroom.annotation.client.event.ShowFindAnnotationEvent;
import stroom.annotation.shared.AddAnnotationTable;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationTable;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.LinkAnnotations;
import stroom.annotation.shared.LinkEvents;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.index.shared.IndexConstants;
import stroom.query.api.Column;
import stroom.query.api.SpecialColumns;
import stroom.query.client.result.table.TableRow;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.util.client.Console;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// The Annotate menu of a query's results table, for the rows selected: edit, create or add to an
/// annotation, change its status or who it is assigned to, or find one. It has no widget of its
/// own; the dialogs it opens are its own until they are hidden.
public class AnnotationMenuController extends Controller {

    private final Provider<ChangeStatusController> changeStatusProvider;
    private final Provider<ChangeAssigneeController> changeAssignedToProvider;
    private final AnnotationResourceClient annotationResourceClient;
    private final boolean enabled;

    private List<TableRow> selectedItems;

    private Supplier<List<Column>> columnSupplier;

    /// Made by GIN, with what it needs.
    ///
    /// @param eventBus                  The application's event bus.
    /// @param changeStatusProvider      Makes the dialog that changes an annotation's status.
    /// @param changeAssignedToProvider  Makes the dialog that changes who an annotation is assigned to.
    /// @param annotationResourceClient  Changes annotations.
    /// @param securityContext           Says whether the user may annotate.
    @Inject
    public AnnotationMenuController(final EventBus eventBus,
                                    final Provider<ChangeStatusController> changeStatusProvider,
                                    final Provider<ChangeAssigneeController> changeAssignedToProvider,
                                    final AnnotationResourceClient annotationResourceClient,
                                    final ClientSecurityContext securityContext) {
        super(eventBus);
        this.changeStatusProvider = changeStatusProvider;
        this.changeAssignedToProvider = changeAssignedToProvider;
        this.annotationResourceClient = annotationResourceClient;
        enabled = securityContext.hasAppPermission(AppPermission.ANNOTATIONS);
    }


    /// @return Whether the user may annotate.
    public boolean isEnabled() {
        return enabled;
    }

    /// @param columnSupplier Gives the table's columns, to find the rows' event ids and annotations.
    public void setColumnSupplier(final Supplier<List<Column>> columnSupplier) {
        this.columnSupplier = columnSupplier;
    }

    /// Shows the annotation menu for the selected rows, below the element an event happened on.
    ///
    /// @param event         The event, e.g. a click.
    /// @param selectedItems The selected rows the menu's actions apply to.
    public void showAnnotationMenu(final NativeEvent event,
                                   final List<TableRow> selectedItems) {
        final Element target = event.getEventTarget().cast();
        showAnnotationMenu(target, selectedItems);
    }

    /// Shows the annotation menu for the selected rows, below an element, e.g. the button that
    /// opens it.
    ///
    /// @param target        The element to show the menu below.
    /// @param selectedItems The selected rows the menu's actions apply to.
    public void showAnnotationMenu(final Element target,
                                   final List<TableRow> selectedItems) {
        if (enabled) {
            this.selectedItems = selectedItems;

            Rect relativeRect = new Rect(target);
            relativeRect = relativeRect.grow(3);
            final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.BELOW);

            final List<Item> menuItems = getMenuItems(selectedItems);
            ShowMenuEvent
                    .builder()
                    .name("Annotate")
                    .items(menuItems)
                    .popupPosition(popupPosition)
                    .opener(target)
                    .fire(this);
        }
    }

    /// @param selectedItems The selected rows.
    /// @return The menu's items for them.
    public List<Item> getMenuItems(final List<TableRow> selectedItems) {
        final List<Item> menuItems = new ArrayList<>();

        if (enabled) {
            final List<EventId> eventIdList = new ArrayList<>();
            final List<Long> annotationIdList = new ArrayList<>();
            final AnnotationTable table = addRowData(selectedItems, eventIdList, annotationIdList);

            // Create menu item.
            menuItems.add(createCreateMenu(table, eventIdList, annotationIdList));
            menuItems.add(createAddMenu(table, eventIdList, annotationIdList));

            if (annotationIdList.size() == 1) {
                // Edit menu item.
                menuItems.add(createEditMenu(annotationIdList.get(0)));
            }

            if (!annotationIdList.isEmpty()) {
                // Status menu item.
                menuItems.add(createStatusMenu(annotationIdList));
                // Assigned to menu item.
                menuItems.add(createAssignMenu(annotationIdList));
            }
        }

        return menuItems;
    }

    /// Collects the rows' event ids and annotation ids, and the rows as a table to add to an
    /// annotation.
    ///
    /// @param selectedItems    The selected rows.
    /// @param eventIdList      Given the rows' event ids.
    /// @param annotationIdList Given the rows' annotation ids.
    /// @return The rows as a table, or null if the user may not annotate or none are selected.
    public AnnotationTable addRowData(final List<TableRow> selectedItems,
                                      final List<EventId> eventIdList,
                                      final List<Long> annotationIdList) {
        if (enabled && selectedItems != null && !selectedItems.isEmpty()) {
            String streamIdFieldId = getFieldId(IndexConstants.STREAM_ID);
            if (streamIdFieldId == null) {
                streamIdFieldId = getFieldId(SpecialColumns.RESERVED_STREAM_ID);
            }
            String eventIdFieldId = getFieldId(IndexConstants.EVENT_ID);
            if (eventIdFieldId == null) {
                eventIdFieldId = getFieldId(SpecialColumns.RESERVED_EVENT_ID);
            }
            final String eventIdListFieldId = getFieldId("EventIdList");

            final List<Column> columnList = columnSupplier.get();
            final List<String> columnNames = columnList
                    .stream()
                    .filter(Column::isVisible)
                    .filter(col -> !col.isSpecial())
                    .map(Column::getName)
                    .collect(Collectors.toList());
            final List<List<String>> values = new ArrayList<>(selectedItems.size());

            for (final TableRow row : selectedItems) {
                final List<String> rowValues = columnList
                        .stream()
                        .filter(Column::isVisible)
                        .filter(col -> !col.isSpecial())
                        .map(col -> row.getText(col.getId()))
                        .collect(Collectors.toList());
                values.add(rowValues);

                // Add stream and event id fields.
                if (streamIdFieldId != null && eventIdFieldId != null) {
                    try {
                        final Long streamId = toLong(row.getText(streamIdFieldId));
                        final Long eventId = toLong(row.getText(eventIdFieldId));
                        if (streamId != null && eventId != null) {
                            eventIdList.add(new EventId(streamId, eventId));
                        }
                    } catch (final RuntimeException e) {
                        Console.error(e::getMessage, e);
                    }
                }

                // Add event lists.
                if (eventIdListFieldId != null) {
                    final String eventIdListString = row.getText(eventIdListFieldId);
                    EventId.parseList(eventIdListString, eventIdList);
                }

                // Add annotation ids.
                final Long annotationId = row.getAnnotationId();
                if (annotationId != null && !annotationIdList.contains(annotationId)) {
                    annotationIdList.add(annotationId);
                }
            }

            return new AnnotationTable(columnNames, values);
        }

        return null;
    }

    private String getFieldId(final String fieldName) {
        for (final Column column : columnSupplier.get()) {
            if (column.getName().equalsIgnoreCase(fieldName)) {
                return column.getId();
            }
        }
        return null;
    }

    /// @param selectedItems The selected rows.
    /// @return The ids of the annotations they belong to.
    public Set<Long> getAnnotationIds(final List<TableRow> selectedItems) {
        if (!enabled) {
            return Collections.emptySet();
        }

        return selectedItems
                .stream()
                .map(TableRow::getAnnotationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String getValue(final List<TableRow> selectedItems,
                            final String fieldName) {
        if (selectedItems != null && !selectedItems.isEmpty()) {
            final String fieldId = getFieldId(fieldName);
            if (fieldId != null) {
                for (final TableRow row : selectedItems) {
                    final String value = row.getText(fieldId);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private Long toLong(final String string) {
        if (string != null) {
            try {
                return Long.parseLong(string);
            } catch (final NumberFormatException e) {
                // Ignore.
            }
        }
        return null;
    }

    private Item createCreateMenu(final AnnotationTable table,
                                  final List<EventId> eventIdList,
                                  final List<Long> annotationIdList) {
        return new IconMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.EDIT)
                .text("New Annotation")
                .command(() -> createAnnotation(table, eventIdList, annotationIdList))
                .build();
    }

    private Item createAddMenu(final AnnotationTable table,
                               final List<EventId> eventIdList,
                               final List<Long> annotationIdList) {
        return new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.EDIT)
                .text("Add to Annotation")
                .command(() -> addToAnnotation(table, eventIdList, annotationIdList))
                .build();
    }

    private Item createEditMenu(final Long annotationId) {
        return new IconMenuItem.Builder()
                .priority(2)
                .icon(SvgImage.EDIT)
                .text("Edit Annotation")
                .command(() -> editAnnotation(annotationId))
                .build();
    }

    private Item createStatusMenu(final List<Long> annotationIdList) {
        return new IconMenuItem.Builder()
                .priority(3)
                .icon(SvgImage.EDIT)
                .text("Change Status")
                .command(() -> changeStatus(annotationIdList))
                .build();
    }

    private Item createAssignMenu(final List<Long> annotationIdList) {
        return new IconMenuItem.Builder()
                .priority(5)
                .icon(SvgImage.EDIT)
                .text("Change Assignee")
                .command(() -> changeAssignedTo(annotationIdList))
                .build();
    }

    private void createAnnotation(final AnnotationTable table,
                                  final List<EventId> eventIdList,
                                  final List<Long> annotationIdList) {
        String title = getValue(selectedItems, "title");
        final String subject = getValue(selectedItems, "subject");
        final String status = getValue(selectedItems, "status");
        final String assignedTo = getValue(selectedItems, "assignedTo");
        final String comment = getValue(selectedItems, "comment");

        title = title == null
                ? "New Annotation"
                : title;

        UserRef initialAssignTo = null;
        if (assignedTo != null) {
            initialAssignTo = UserRef.builder().uuid(assignedTo).build();
        }

        CreateAnnotationEvent.fire(
                this,
                title,
                subject,
                status,
                initialAssignTo,
                comment,
                table,
                eventIdList,
                annotationIdList);
    }

    private void addToAnnotation(final AnnotationTable table,
                                 final List<EventId> eventIdList,
                                 final List<Long> annotationIdList) {
        ShowFindAnnotationEvent.fire(this, annotation -> {
            linkEvents(annotation, eventIdList, () -> {
                linkAnnotations(annotation, annotationIdList, () -> {
                    addTable(annotation, table, () -> {
                        EditAnnotationEvent.fire(this, annotation.getId());

                        // Let the UI know the annotation has changed but not until we have shown the annotation
                        // (hence deferred by timer).
                        AnnotationChangeEvent.fireDeferred(AnnotationMenuController.this, annotation.asDocRef());
                    });
                });
            });
        });
    }

    private void linkEvents(final Annotation annotation,
                            final List<EventId> eventIdList,
                            final Runnable runnable) {
        if (NullSafe.isEmptyCollection(eventIdList)) {
            runnable.run();
        } else {
            final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                    annotation.asDocRef(),
                    annotation.getId(),
                    new LinkEvents(eventIdList));
            annotationResourceClient.change(request, guard(r -> runnable.run()), taskMonitor());
        }
    }

    private void linkAnnotations(final Annotation annotation,
                                 final List<Long> annotationIdList,
                                 final Runnable runnable) {
        if (NullSafe.isEmptyCollection(annotationIdList)) {
            runnable.run();
        } else {
            final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                    annotation.asDocRef(),
                    annotation.getId(),
                    new LinkAnnotations(annotationIdList));
            annotationResourceClient.change(request, guard(r -> runnable.run()), taskMonitor());
        }
    }

    private void addTable(final Annotation annotation,
                          final AnnotationTable table,
                          final Runnable runnable) {
        if (table == null) {
            runnable.run();
        } else {
            final SingleAnnotationChangeRequest request = new SingleAnnotationChangeRequest(
                    annotation.asDocRef(),
                    annotation.getId(),
                    new AddAnnotationTable(table));
            annotationResourceClient.change(request, guard(r -> runnable.run()), taskMonitor());
        }
    }

    /// Opens an annotation, if the user may annotate.
    ///
    /// @param annotationId The annotation's id.
    public void editAnnotation(final long annotationId) {
        if (enabled) {
            EditAnnotationEvent.fire(this, annotationId);
        }
    }

    private void changeStatus(final List<Long> annotationIdList) {
        if (enabled) {
            // One per use, released once it is hidden
            changeStatusProvider.get().show(this, annotationIdList);
        }
    }

    private void changeAssignedTo(final List<Long> annotationIdList) {
        if (enabled) {
            changeAssignedToProvider.get().show(this, annotationIdList);
        }
    }
}
