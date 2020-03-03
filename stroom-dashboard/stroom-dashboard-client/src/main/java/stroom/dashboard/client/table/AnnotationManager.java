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

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import stroom.annotation.client.ChangeAssignedToPresenter;
import stroom.annotation.client.ChangeStatusPresenter;
import stroom.annotation.client.ShowAnnotationEvent;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.EventId;
import stroom.dashboard.shared.Field;
import stroom.dashboard.client.main.IndexConstants;
import stroom.dashboard.shared.Row;
import stroom.hyperlink.client.Hyperlink;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.VerticalLocation;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class AnnotationManager {
    private final MenuListPresenter menuListPresenter;
    private final ChangeStatusPresenter changeStatusPresenter;
    private final ChangeAssignedToPresenter changeAssignedToPresenter;

    @Inject
    public AnnotationManager(final MenuListPresenter menuListPresenter,
                             final ChangeStatusPresenter changeStatusPresenter,
                             final ChangeAssignedToPresenter changeAssignedToPresenter) {
        this.menuListPresenter = menuListPresenter;
        this.changeStatusPresenter = changeStatusPresenter;
        this.changeAssignedToPresenter = changeAssignedToPresenter;
    }

    public void showAnnotationMenu(final NativeEvent event, final List<Field> currentFields, final List<Row> selectedItems) {
        final Element target = event.getEventTarget().cast();
        final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft(),
                target.getAbsoluteRight(), target.getAbsoluteTop(), target.getAbsoluteBottom(), null,
                VerticalLocation.BELOW);
        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                HidePopupEvent.fire(menuListPresenter, menuListPresenter);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        };

        updateMenuItems(currentFields, selectedItems);

        ShowPopupEvent.fire(menuListPresenter, menuListPresenter, PopupType.POPUP, popupPosition,
                popupUiHandlers, target);
    }

    private void updateMenuItems(final List<Field> currentFields, final List<Row> selectedItems) {
        final List<Item> menuItems = new ArrayList<>();

        final List<EventId> eventIdList = getEventIdList(currentFields, selectedItems);
        final List<Long> annotationIdList = getAnnotationIdList(currentFields, selectedItems);

        // Create menu item.
        menuItems.add(createCreateMenu(eventIdList));

        if (annotationIdList.size() > 0) {
            // Status menu item.
            menuItems.add(createStatusMenu(annotationIdList));
            // Assigned to menu item.
            menuItems.add(createAssignMenu(annotationIdList));
        }

        menuListPresenter.setData(menuItems);
    }

    public List<EventId> getEventIdList(final List<Field> currentFields, final List<Row> selectedItems) {
        final List<EventId> idList = new ArrayList<>();
        if (selectedItems != null && selectedItems.size() > 0) {
            int streamIdIndex = -1;
            int eventIdIndex = -1;
            int i = 0;
            for (final Field field : currentFields) {
                if (streamIdIndex == -1 && field.getName().equals(IndexConstants.STREAM_ID)) {
                    streamIdIndex = i;
                } else if (eventIdIndex == -1 && field.getName().equals(IndexConstants.EVENT_ID)) {
                    eventIdIndex = i;
                }
                i++;
            }

            if (streamIdIndex != -1 && eventIdIndex != -1) {
                for (final Row row : selectedItems) {
                    final Long streamId = getLong(row.getValues(), streamIdIndex);
                    final Long eventId = getLong(row.getValues(), eventIdIndex);
                    if (streamId != null && eventId != null) {
                        idList.add(new EventId(streamId, eventId));
                    }
                }
            }
        }
        return idList;
    }

    public List<Long> getAnnotationIdList(final List<Field> currentFields, final List<Row> selectedItems) {
        final List<Long> idList = new ArrayList<>();
        if (selectedItems != null && selectedItems.size() > 0) {
            int annotationIdIndex = -1;
            int i = 0;
            for (final Field field : currentFields) {
                if (annotationIdIndex == -1 && field.getName().equals("annotation:Id")) {
                    annotationIdIndex = i;
                }
                i++;
            }

            if (annotationIdIndex != -1) {
                for (final Row row : selectedItems) {
                    final Long annotationId = getLong(row.getValues(), annotationIdIndex);
                    if (annotationId != null) {
                        idList.add(annotationId);
                    }
                }
            }
        }
        return idList;
    }

    private Long getLong(List<String> values, int index) {
        if (values != null && values.size() > index) {
            final String value = values.get(index);
            if (value != null) {
                try {
                    if (value.startsWith("[")) {
                        final Hyperlink hyperlink = Hyperlink.create(value);
                        return Long.parseLong(hyperlink.getText());
                    }
                    return Long.parseLong(value);
                } catch (final NumberFormatException e) {
                    // Ignore.
                }
            }
        }
        return null;
    }


    private Item createCreateMenu(final List<EventId> eventIdList) {
        return new IconMenuItem(0, SvgPresets.EDIT, SvgPresets.EDIT, "Create Annotation", null, true, () -> createAnnotation(eventIdList));
    }

    private Item createStatusMenu(final List<Long> annotationIdList) {
        return new IconMenuItem(1, SvgPresets.EDIT, SvgPresets.EDIT, "Change Status", null, true, () -> changeStatus(annotationIdList));
    }

    private Item createAssignMenu(final List<Long> annotationIdList) {
        return new IconMenuItem(2, SvgPresets.EDIT, SvgPresets.EDIT, "Change Assigned To", null, true, () -> changeAssignedTo(annotationIdList));
    }

    private void createAnnotation(final List<EventId> eventIdList) {
//        if (idList.size() > 0) {
        final Annotation annotation = new Annotation();
        ShowAnnotationEvent.fire(menuListPresenter, annotation, eventIdList);

//        } else {
//            AlertEvent.fireWarn(this, "You need to select some rows to annotate", null);
//        }
    }

    private void changeStatus(final List<Long> annotationIdList) {
        changeStatusPresenter.show(annotationIdList);

//        final List<EventId> idList = getEventIdList(currentFields, selectedItems);
//        if (idList.size() > 0) {
//            final Annotation annotation = new Annotation();
//            ShowAnnotationEvent.fire(this, annotation, idList);
//
//        } else {
//            AlertEvent.fireWarn(this, "You need to select some rows to annotate", null);
//        }
    }

    private void changeAssignedTo(final List<Long> annotationIdList) {
        changeAssignedToPresenter.show(annotationIdList);
//        final List<EventId> idList = getEventIdList(currentFields, selectedItems);
//        if (idList.size() > 0) {
//            final Annotation annotation = new Annotation();
//            ShowAnnotationEvent.fire(this, annotation, idList);
//
//        } else {
//            AlertEvent.fireWarn(this, "You need to select some rows to annotate", null);
//        }
    }
}
