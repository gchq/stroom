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
 *
 */

package stroom.annotation.client;

import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.shared.AnnotationGroup;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.grid.client.WrapperView;
import stroom.svg.client.IconColour;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

public class AnnotationGroupPresenter extends ContentTabPresenter<WrapperView> {

    public static final String TAB_TYPE = "AnnotationGroups";

    private final AnnotationGroupListPresenter annotationGroupListPresenter;
    private final Provider<AnnotationGroupEditPresenter> editProvider;
    private final AnnotationResourceClient annotationResourceClient;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;

    @Inject
    public AnnotationGroupPresenter(
            final EventBus eventBus,
            final WrapperView view,
            final AnnotationGroupListPresenter annotationGroupListPresenter,
            final Provider<AnnotationGroupEditPresenter> editProvider,
            final AnnotationResourceClient annotationResourceClient) {

        super(eventBus, view);
        this.annotationGroupListPresenter = annotationGroupListPresenter;
        this.editProvider = editProvider;
        this.annotationResourceClient = annotationResourceClient;

        newButton = annotationGroupListPresenter.getView().addButton(SvgPresets.NEW_ITEM);
        openButton = annotationGroupListPresenter.getView().addButton(SvgPresets.EDIT);
        deleteButton = annotationGroupListPresenter.getView().addButton(SvgPresets.DELETE);

        view.setView(annotationGroupListPresenter.getView());
    }

    @Override
    protected void onBind() {
        registerHandler(annotationGroupListPresenter.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                edit();
            }
        }));
        registerHandler(newButton.addClickHandler(event -> add()));
        registerHandler(openButton.addClickHandler(event -> edit()));
        registerHandler(deleteButton.addClickHandler(event -> delete()));
    }

    private void add() {
        edit(AnnotationGroup.builder().build());
    }

    private void edit() {
        final AnnotationGroup annotationGroup = annotationGroupListPresenter.getSelectionModel().getSelected();
        if (annotationGroup != null) {
            edit(annotationGroup);
        }
    }

    private void edit(final AnnotationGroup annotationGroup) {
        final AnnotationGroupEditPresenter editor = editProvider.get();
        final String caption = annotationGroup.getUuid() == null
                ? "Create Annotation Group"
                : "Edit Annotation Group - " + annotationGroup.getName();
        editor.open(annotationGroup, caption, result -> refresh());
    }

    private void delete() {
        final List<AnnotationGroup> list = annotationGroupListPresenter.getSelectionModel().getSelectedItems();
        if (list != null && !list.isEmpty()) {
            String message = "Are you sure you want to delete the selected annotation group?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected annotation groups?";
            }
            ConfirmEvent.fire(AnnotationGroupPresenter.this, message,
                    result -> {
                        if (result) {
                            annotationGroupListPresenter.getSelectionModel().clear();
                            for (final AnnotationGroup group : list) {
                                annotationResourceClient.deleteAnnotationGroup(group, response -> refresh(), this);
                            }
                        }
                    });
        }
    }

    private void enableButtons() {
        final boolean enabled = annotationGroupListPresenter.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    public void refresh() {
        annotationGroupListPresenter.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.DOCUMENT_ANNOTATIONS_INDEX;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Annotation Groups";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
