/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.client.table;

import stroom.annotation.client.ChangeAssignedToPresenter;
import stroom.annotation.client.ChangeStatusPresenter;
import stroom.annotation.client.ShowAnnotationEvent;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.annotation.shared.AnnotationFields;
import stroom.annotation.shared.EventId;
import stroom.dashboard.shared.IndexConstants;
import stroom.docref.DocRef;
import stroom.query.api.v2.Column;
import stroom.query.client.presenter.TableRow;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class AnnotationManager {

    private final ChangeStatusPresenter changeStatusPresenter;
    private final ChangeAssignedToPresenter changeAssignedToPresenter;

    private List<TableRow> selectedItems;

    private Supplier<DocRef> dataSourceSupplier;
    private Supplier<List<Column>> columnSupplier;

    @Inject
    public AnnotationManager(final ChangeStatusPresenter changeStatusPresenter,
                             final ChangeAssignedToPresenter changeAssignedToPresenter) {
        this.changeStatusPresenter = changeStatusPresenter;
        this.changeAssignedToPresenter = changeAssignedToPresenter;
    }

    public void setDataSourceSupplier(final Supplier<DocRef> dataSourceSupplier) {
        this.dataSourceSupplier = dataSourceSupplier;
    }

    public void setColumnSupplier(final Supplier<List<Column>> columnSupplier) {
        this.columnSupplier = columnSupplier;
    }

    public void showAnnotationMenu(final NativeEvent event,
                                   final List<TableRow> selectedItems) {
        this.selectedItems = selectedItems;

        final Element target = event.getEventTarget().cast();
        Rect relativeRect = new Rect(target);
        relativeRect = relativeRect.grow(3);
        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.BELOW);

        final List<Item> menuItems = getMenuItems(selectedItems);
        ShowMenuEvent
                .builder()
                .items(menuItems)
                .popupPosition(popupPosition)
                .fire(changeStatusPresenter);
    }

    private List<Item> getMenuItems(final List<TableRow> selectedItems) {
        final List<Item> menuItems = new ArrayList<>();

        final List<EventId> eventIdList = getEventIdList(selectedItems);
        final List<Long> annotationIdList = getAnnotationIdList(selectedItems);

        // Create menu item.
        menuItems.add(createCreateMenu(eventIdList));

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

        return menuItems;
    }

    public List<EventId> getEventIdList(final List<TableRow> selectedItems) {
        final List<EventId> idList = new ArrayList<>();

        final List<String> streamIds = getValues(
                selectedItems,
                IndexConstants.RESERVED_STREAM_ID_FIELD_NAME);
        final List<String> eventIds = getValues(
                selectedItems,
                IndexConstants.RESERVED_EVENT_ID_FIELD_NAME);
        final List<String> eventIdLists = getValues(selectedItems, "EventIdList");

        for (int i = 0; i < streamIds.size() && i < eventIds.size(); i++) {
            final Long streamId = toLong(streamIds.get(i));
            final Long eventId = toLong(eventIds.get(i));
            if (streamId != null && eventId != null) {
                idList.add(new EventId(streamId, eventId));
            }
        }

        for (final String eventIdList : eventIdLists) {
            if (eventIdList != null) {
                final String[] events = eventIdList.split(" ");
                for (final String event : events) {
                    try {
                        final String[] parts = event.split(":");
                        if (parts.length == 2) {
                            final long streamId = Long.parseLong(parts[0]);
                            final long eventId = Long.parseLong(parts[1]);
                            idList.add(new EventId(streamId, eventId));
                        }
                    } catch (final NumberFormatException e) {
                        // Ignore.
                    }
                }
            }
        }

        return idList;
    }

    private String getFieldId(final String fieldName) {
        for (final Column column : columnSupplier.get()) {
            if (column.getName().equalsIgnoreCase(fieldName)) {
                return column.getId();
            }
        }
        return null;
    }

    public List<Long> getAnnotationIdList(final List<TableRow> selectedItems) {
        Set<String> values = new HashSet<>();

        // Get annotation ids from annotation id column.
        final DocRef dataSource = dataSourceSupplier.get();
        if (dataSource != null &&
            Annotation.TYPE.equals(dataSource.getType())) {
            final List<String> list = getValues(selectedItems, AnnotationFields.ID);
            if (list != null) {
                values.addAll(list);
            }
        }

        // Get annotation ids from decoration column.
        final List<String> list = getValues(selectedItems,
                AnnotationDecorationFields.ANNOTATION_ID);
        if (list != null) {
            values.addAll(list);
        }

        return values
                .stream()
                .map(this::toLong)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<String> getValues(final List<TableRow> selectedItems,
                                  final String fieldName) {
        final List<String> values = new ArrayList<>();
        if (selectedItems != null && !selectedItems.isEmpty()) {
            final String fieldId = getFieldId(fieldName);
            if (fieldId != null) {
                for (final TableRow row : selectedItems) {
                    final String value = row.getText(fieldId);
                    values.add(value);
                }
            }
        }
        return values;
    }

    public String getValue(final List<TableRow> selectedItems,
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

    private Item createCreateMenu(final List<EventId> eventIdList) {
        return new IconMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.EDIT)
                .text("Create Annotation")
                .command(() -> createAnnotation(eventIdList))
                .build();
    }

    private Item createEditMenu(final Long annotationId) {
        return new IconMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.EDIT)
                .text("Edit Annotation")
                .command(() -> editAnnotation(annotationId))
                .build();
    }

    private Item createStatusMenu(final List<Long> annotationIdList) {
        return new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.EDIT)
                .text("Change Status")
                .command(() -> changeStatus(annotationIdList))
                .build();
    }

    private Item createAssignMenu(final List<Long> annotationIdList) {
        return new IconMenuItem.Builder()
                .priority(2)
                .icon(SvgImage.EDIT)
                .text("Change Assigned To")
                .command(() -> changeAssignedTo(annotationIdList))
                .build();
    }

    private void createAnnotation(final List<EventId> eventIdList) {
        final String title = getValue(selectedItems, "title");
        final String subject = getValue(selectedItems, "subject");
        final String status = getValue(selectedItems, "status");
        final String comment = getValue(selectedItems, "comment");

        final Annotation annotation = new Annotation();
        annotation.setName(title == null
                ? "New Annotation"
                : title);
        annotation.setSubject(subject);
        annotation.setStatus(status);
        annotation.setComment(comment);

        ShowAnnotationEvent.fire(changeStatusPresenter, annotation, eventIdList);
    }

    public void editAnnotation(final long annotationId) {
        // assignedTo is a display name so have to convert it back to a unique username
        final Annotation annotation = new Annotation();
        annotation.setId(annotationId);

        ShowAnnotationEvent.fire(changeStatusPresenter, annotation, null);
    }

    private void changeStatus(final List<Long> annotationIdList) {
        changeStatusPresenter.show(annotationIdList);
    }

    private void changeAssignedTo(final List<Long> annotationIdList) {
        changeAssignedToPresenter.show(annotationIdList);
    }
}
