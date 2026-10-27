/*
 * Copyright 2025 Crown Copyright
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

import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.client.component.AnnotationTagListController;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.datagrid.client.WrapperView;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;

public class AnnotationTagPresenter extends ContentTabPresenter<WrapperView> {

    public static final String TAB_TYPE = "AnnotationTags";

    private final AnnotationTagListController annotationTagList;
    private final Provider<AnnotationTagCreatePresenter> createProvider;
    private final Provider<AnnotationTagEditPresenter> editProvider;
    private final AnnotationResourceClient annotationResourceClient;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;

    private String tabLabel;
    private AnnotationTagType annotationTagType;

    @Inject
    public AnnotationTagPresenter(
            final EventBus eventBus,
            final WrapperView view,
            final AnnotationTagListController annotationTagList,
            final Provider<AnnotationTagCreatePresenter> createProvider,
            final Provider<AnnotationTagEditPresenter> editProvider,
            final AnnotationResourceClient annotationResourceClient) {

        super(eventBus, view);
        own(annotationTagList);
        this.annotationTagList = annotationTagList;
        this.createProvider = createProvider;
        this.editProvider = editProvider;
        this.annotationResourceClient = annotationResourceClient;

        annotationTagList.getToolbar().setName("Tag actions");
        newButton = annotationTagList.getToolbar().button(SvgImage.ADD, "New Annotation Tag",
                this::add);
        openButton = annotationTagList.getToolbar().button(SvgImage.EDIT,
                "Edit Selected Annotation Tag", this::edit);
        openButton.setEnabled(false);
        deleteButton = annotationTagList.getToolbar().button(SvgImage.DELETE,
                "Delete Selected Annotation Tag",
                this::delete);
        deleteButton.setEnabled(false);

        view.setView(annotationTagList);

        own(annotationTagList.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                edit();
            }
        }));
    }

    private void add() {
        makeDialog(createProvider).open(annotationTagType, annotationTag -> refresh());
    }

    private void edit() {
        final AnnotationTag annotationTag = annotationTagList.getSelectionModel().getSelected();
        if (annotationTag != null) {
            edit(annotationTag);
        }
    }

    private void edit(final AnnotationTag annotationTag) {
        final AnnotationTagEditPresenter editor = makeDialog(editProvider);
        final String name = annotationTag.getName();
        final String caption = "Edit " + annotationTagType.getDisplayValue() + " - " + annotationTag.getName();

        editor.open(annotationTag, caption, newAnnotationTag -> {
            if (!Objects.equals(name, newAnnotationTag.getName())) {
                AnnotationTagNameChangeEvent.fire(this);
            }
            refresh();
        });
    }

    private void delete() {
        final List<AnnotationTag> list = annotationTagList.getSelectionModel().getSelectedItems();
        if (list != null && !list.isEmpty()) {
            final String message = "Are you sure you want to delete the selected " +
                                   annotationTagType.getDisplayValue().toLowerCase() +
                                   "?";
            ConfirmEvent.fire(AnnotationTagPresenter.this, message,
                    result -> {
                        if (result) {
                            annotationTagList.getSelectionModel().clear();
                            for (final AnnotationTag group : list) {
                                annotationResourceClient.deleteAnnotationGroup(
                                        group,
                                        ignored -> {
                                            AnnotationTagNameChangeEvent.fire(this);
                                            refresh();
                                        },
                                        this);
                            }
                        }
                    });
        }
    }

    private void enableButtons() {
        final boolean enabled = annotationTagList.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    public void refresh() {
        annotationTagList.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.TAGS;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return tabLabel;
    }

    @Override
    public String getType() {
        return TAB_TYPE + annotationTagType.getDisplayValue();
    }

    public void setTabLabel(final String tabLabel) {
        this.tabLabel = tabLabel;
    }

    public void setAnnotationTagType(final AnnotationTagType annotationTagType) {
        this.annotationTagType = annotationTagType;
        annotationTagList.setAnnotationTagType(annotationTagType);
        // Name the buttons after the kind of tag this screen lists, e.g. "New Annotation Status"
        final String tagName = "Annotation " + annotationTagType.getDisplayValue();
        newButton.setTitle("New " + tagName);
        openButton.setTitle("Edit Selected " + tagName);
        deleteButton.setTitle("Delete Selected " + tagName);
    }
}
