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
import stroom.annotation.shared.EventId;
import stroom.dashboard.shared.TableComponentSettings;
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
import java.util.List;
import javax.inject.Inject;

public class AnnotationManager {

    private final ChangeStatusPresenter changeStatusPresenter;
    private final ChangeAssignedToPresenter changeAssignedToPresenter;

    private TableComponentSettings tableComponentSettings;
    private List<TableRow> selectedItems;

    @Inject
    public AnnotationManager(final ChangeStatusPresenter changeStatusPresenter,
                             final ChangeAssignedToPresenter changeAssignedToPresenter) {
        this.changeStatusPresenter = changeStatusPresenter;
        this.changeAssignedToPresenter = changeAssignedToPresenter;
    }

    public void showAnnotationMenu(final NativeEvent event,
                                   final TableComponentSettings tableComponentSettings,
                                   final List<TableRow> selectedItems) {
        this.tableComponentSettings = tableComponentSettings;
        this.selectedItems = selectedItems;

        final Element target = event.getEventTarget().cast();
        Rect relativeRect = new Rect(target);
        relativeRect = relativeRect.grow(3);
        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.BELOW);

        final List<Item> menuItems = getMenuItems(tableComponentSettings, selectedItems);
        ShowMenuEvent
                .builder()
                .items(menuItems)
                .popupPosition(popupPosition)
                .fire(changeStatusPresenter);
    }

    private List<Item> getMenuItems(final TableComponentSettings tableComponentSettings,
                                    final List<TableRow> selectedItems) {
        final List<Item> menuItems = new ArrayList<>();

        final List<EventId> eventIdList = new ArrayList<>();
        final List<Long> annotationIdList = new ArrayList<>();
        addRowData(tableComponentSettings, selectedItems, eventIdList, annotationIdList);

        // Create menu item.
        menuItems.add(createCreateMenu(eventIdList));

        if (annotationIdList.size() > 0) {
            // Status menu item.
            menuItems.add(createStatusMenu(annotationIdList));
            // Assigned to menu item.
            menuItems.add(createAssignMenu(annotationIdList));
        }

        return menuItems;
    }

    public void addRowData(final TableComponentSettings tableComponentSettings,
                            final List<TableRow> selectedItems,
                            final List<EventId> eventIdList,
                            final List<Long> annotationIdList) {
        if (selectedItems != null && selectedItems.size() > 0) {
            final String streamIdFieldId = getFieldId(tableComponentSettings,
                    SpecialColumns.RESERVED_STREAM_ID);
            final String eventIdFieldId = getFieldId(tableComponentSettings,
                    SpecialColumns.RESERVED_EVENT_ID);
            final String eventIdListFieldId = getFieldId(tableComponentSettings,
                    "EventIdList");
            final String annotationIdFieldId = getFieldId(tableComponentSettings,
                    "annotation:Id");

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

    private String getFieldId(final TableComponentSettings tableComponentSettings, final String fieldName) {
        for (final Column column : tableComponentSettings.getColumns()) {
            if (column.getName().equalsIgnoreCase(fieldName)) {
                return column.getId();
            }
        }
        return null;
    }

    private String getValue(final TableComponentSettings tableComponentSettings,
                            final List<TableRow> selectedItems,
                            final String fieldName) {
        if (selectedItems != null && selectedItems.size() > 0) {
            final String fieldId = getFieldId(tableComponentSettings, fieldName);
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
        final String title = getValue(tableComponentSettings, selectedItems, "title");
        final String subject = getValue(tableComponentSettings, selectedItems, "subject");
        final String status = getValue(tableComponentSettings, selectedItems, "status");
        final String assignedTo = getValue(tableComponentSettings, selectedItems, "assignedTo");
        final String comment = getValue(tableComponentSettings, selectedItems, "comment");

        final Annotation annotation = new Annotation();
        annotation.setTitle(title);
        annotation.setSubject(subject);
        annotation.setStatus(status);
        if (assignedTo != null) {
            annotation.setAssignedTo(UserRef.builder().uuid(assignedTo).build());
        }
        annotation.setComment(comment);

        ShowAnnotationEvent.fire(changeStatusPresenter, annotation, eventIdList);
    }

    private void changeStatus(final List<Long> annotationIdList) {
        changeStatusPresenter.show(annotationIdList);
    }

    private void changeAssignedTo(final List<Long> annotationIdList) {
        changeAssignedToPresenter.show(annotationIdList);
    }
}
