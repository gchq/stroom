/*
 * Copyright 2024 Crown Copyright
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
import stroom.dashboard.shared.IndexConstants;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dispatch.client.QuietTaskListener;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.v2.Column;
import stroom.query.client.presenter.TableRow;
import stroom.security.shared.UserNameResource;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.string.CIKey;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class AnnotationManager {

    private final ChangeStatusPresenter changeStatusPresenter;
    private final ChangeAssignedToPresenter changeAssignedToPresenter;
    private final RestFactory restFactory;

    private TableComponentSettings tableComponentSettings;
    private List<TableRow> selectedItems;

    @Inject
    public AnnotationManager(final ChangeStatusPresenter changeStatusPresenter,
                             final ChangeAssignedToPresenter changeAssignedToPresenter,
                             final RestFactory restFactory) {
        this.changeStatusPresenter = changeStatusPresenter;
        this.changeAssignedToPresenter = changeAssignedToPresenter;
        this.restFactory = restFactory;
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

        final List<EventId> eventIdList = getEventIdList(tableComponentSettings, selectedItems);
        final List<Long> annotationIdList = getAnnotationIdList(tableComponentSettings, selectedItems);

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

    public List<EventId> getEventIdList(final TableComponentSettings tableComponentSettings,
                                        final List<TableRow> selectedItems) {
        final List<EventId> idList = new ArrayList<>();

        final List<String> streamIds = getValues(
                tableComponentSettings,
                selectedItems,
                IndexConstants.generateObfuscatedColumnName(IndexConstants.STREAM_ID));
        final List<String> eventIds = getValues(
                tableComponentSettings,
                selectedItems,
                IndexConstants.generateObfuscatedColumnName(IndexConstants.EVENT_ID));
        final List<String> eventIdLists = getValues(tableComponentSettings, selectedItems, "EventIdList");

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

    private String getFieldId(final TableComponentSettings tableComponentSettings, final String fieldName) {
        final CIKey fieldNameKey = CIKey.of(fieldName);
        return tableComponentSettings.getColumns()
                .stream()
                .filter(column ->
                        Objects.equals(column.getNameAsCIKey(), fieldNameKey))
                .findAny()
                .map(Column::getId)
                .orElse(null);
    }

    public List<Long> getAnnotationIdList(final TableComponentSettings tableComponentSettings,
                                          final List<TableRow> selectedItems) {
        final List<String> values = getValues(tableComponentSettings, selectedItems, "annotation:Id");
        return values
                .stream()
                .map(this::toLong)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<String> getValues(final TableComponentSettings tableComponentSettings,
                                  final List<TableRow> selectedItems,
                                  final String fieldName) {
        final List<String> values = new ArrayList<>();
        if (!GwtNullSafe.isEmptyCollection(selectedItems)) {
            final String fieldId = getFieldId(tableComponentSettings, fieldName);
            if (fieldId != null) {
                for (final TableRow row : selectedItems) {
                    final String value = row.getText(fieldId);
                    values.add(value);
                }
            }
        }
        return values;
    }

    public String getValue(final TableComponentSettings tableComponentSettings,
                           final List<TableRow> selectedItems,
                           final String fieldName) {
        if (!GwtNullSafe.isEmptyCollection(selectedItems)) {
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

        // assignedTo is a display name so have to convert it back to a unique username
        final UserNameResource userNameResource = GWT.create(UserNameResource.class);
        restFactory
                .create(userNameResource)
                .method(res -> res.getByDisplayName(assignedTo))
                .onSuccess(optUserName -> {
                    final Annotation annotation = new Annotation();
                    annotation.setTitle(title);
                    annotation.setSubject(subject);
                    annotation.setStatus(status);
                    annotation.setAssignedTo(optUserName);
                    annotation.setComment(comment);

                    ShowAnnotationEvent.fire(changeStatusPresenter, annotation, eventIdList);
                })
                .taskListener(new QuietTaskListener())
                .exec();
    }

    private void changeStatus(final List<Long> annotationIdList) {
        changeStatusPresenter.show(annotationIdList);
    }

    private void changeAssignedTo(final List<Long> annotationIdList) {
        changeAssignedToPresenter.show(annotationIdList);
    }
}
