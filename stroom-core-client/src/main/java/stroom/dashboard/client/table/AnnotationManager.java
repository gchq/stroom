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
import stroom.annotation.client.CreateAnnotationEvent;
import stroom.annotation.client.EditAnnotationEvent;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.annotation.shared.AnnotationFields;
import stroom.annotation.shared.EventId;
import stroom.docref.DocRef;
import stroom.index.shared.IndexConstants;
import stroom.query.api.Column;
import stroom.query.api.SpecialColumns;
import stroom.query.client.presenter.TableRow;
import stroom.svg.shared.SvgImage;
import stroom.util.client.Console;
import stroom.util.shared.UserRef;
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
import javax.inject.Provider;

public class AnnotationManager {

    private final Provider<ChangeStatusPresenter> changeStatusPresenterProvider;
    private final Provider<ChangeAssignedToPresenter> changeAssignedToPresenterProvider;

    private ChangeStatusPresenter changeStatusPresenter;
    private ChangeAssignedToPresenter changeAssignedToPresenter;

    private List<TableRow> selectedItems;

    private Supplier<DocRef> dataSourceSupplier;
    private Supplier<List<Column>> columnSupplier;

    @Inject
    public AnnotationManager(final Provider<ChangeStatusPresenter> changeStatusPresenterProvider,
                             final Provider<ChangeAssignedToPresenter> changeAssignedToPresenterProvider) {
        this.changeStatusPresenterProvider = changeStatusPresenterProvider;
        this.changeAssignedToPresenterProvider = changeAssignedToPresenterProvider;
    }

    private ChangeStatusPresenter getChangeStatusPresenter() {
        if (changeStatusPresenter == null) {
            changeStatusPresenter = changeStatusPresenterProvider.get();
        }
        return changeStatusPresenter;
    }

    private ChangeAssignedToPresenter getChangeAssignedToPresenter() {
        if (changeAssignedToPresenter == null) {
            changeAssignedToPresenter = changeAssignedToPresenterProvider.get();
        }
        return changeAssignedToPresenter;
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
                .fire(getChangeStatusPresenter());
    }

    private List<Item> getMenuItems(final List<TableRow> selectedItems) {
        final List<Item> menuItems = new ArrayList<>();

        final List<EventId> eventIdList = new ArrayList<>();
        final List<Long> annotationIdList = new ArrayList<>();
        addRowData(selectedItems, eventIdList, annotationIdList);

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

    public void addRowData(final List<TableRow> selectedItems,
                           final List<EventId> eventIdList,
                           final List<Long> annotationIdList) {
        if (selectedItems != null && !selectedItems.isEmpty()) {
            String streamIdFieldId = getFieldId(IndexConstants.STREAM_ID);
            if (streamIdFieldId == null) {
                streamIdFieldId = getFieldId(SpecialColumns.RESERVED_STREAM_ID);
            }
            String eventIdFieldId = getFieldId(IndexConstants.EVENT_ID);
            if (eventIdFieldId == null) {
                eventIdFieldId = getFieldId(SpecialColumns.RESERVED_EVENT_ID);
            }
            final String eventIdListFieldId = getFieldId("EventIdList");
            String annotationIdFieldId = getFieldId("annotation:Id");
            if (annotationIdFieldId == null) {
                annotationIdFieldId = getFieldId("Id");
                if (annotationIdFieldId == null) {
                    annotationIdFieldId = getFieldId(SpecialColumns.RESERVED_ID);
                }
            }

            if (streamIdFieldId != null ||
                eventIdFieldId != null ||
                eventIdListFieldId != null ||
                annotationIdFieldId != null) {
                for (final TableRow row : selectedItems) {
                    // Add stream and event id fields.
                    if (streamIdFieldId != null && eventIdFieldId != null) {
                        try {
                            final Long streamId = toLong(row.getText(streamIdFieldId));
                            final Long eventId = toLong(row.getText(eventIdFieldId));
                            if (streamId != null && eventId != null) {
                                eventIdList.add(new EventId(streamId, eventId));
                            }
                        } catch (final RuntimeException e) {
                            Console.log(e::getMessage, e);
                        }
                    }

                    // Add event lists.
                    if (eventIdListFieldId != null) {
                        final String eventIdListString = row.getText(eventIdListFieldId);
                        EventId.parseList(eventIdListString, eventIdList);
                    }

                    // Add annotation ids.
                    if (annotationIdFieldId != null) {
                        final Long annotationId = toLong(row.getText(annotationIdFieldId));
                        if (annotationId != null) {
                            annotationIdList.add(annotationId);
                        }
                    }
                }
            }
        }
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
                getChangeStatusPresenter(),
                title,
                subject,
                status,
                initialAssignTo,
                comment,
                eventIdList);
    }

    public void editAnnotation(final long annotationId) {
        EditAnnotationEvent.fire(getChangeStatusPresenter(), annotationId);
    }

    private void changeStatus(final List<Long> annotationIdList) {
        getChangeStatusPresenter().show(annotationIdList);
    }

    private void changeAssignedTo(final List<Long> annotationIdList) {
        getChangeAssignedToPresenter().show(annotationIdList);
    }
}
