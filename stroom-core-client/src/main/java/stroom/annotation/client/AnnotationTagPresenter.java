/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
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

public class AnnotationTagPresenter extends ContentTabPresenter<WrapperView> {

    public static final String TAB_TYPE = "AnnotationTags";

    private final AnnotationTagListPresenter annotationTagListPresenter;
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
            final AnnotationTagListPresenter annotationTagListPresenter,
            final Provider<AnnotationTagCreatePresenter> createProvider,
            final Provider<AnnotationTagEditPresenter> editProvider,
            final AnnotationResourceClient annotationResourceClient) {

        super(eventBus, view);
        this.annotationTagListPresenter = annotationTagListPresenter;
        this.createProvider = createProvider;
        this.editProvider = editProvider;
        this.annotationResourceClient = annotationResourceClient;

        newButton = annotationTagListPresenter.getView().addButton(SvgPresets.NEW_ITEM);
        openButton = annotationTagListPresenter.getView().addButton(SvgPresets.EDIT);
        deleteButton = annotationTagListPresenter.getView().addButton(SvgPresets.DELETE);

        view.setView(annotationTagListPresenter.getView());
    }

    @Override
    protected void onBind() {
        registerHandler(annotationTagListPresenter.getSelectionModel().addSelectionHandler(event -> {
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
        createProvider.get().open(annotationTagType, annotationTag -> refresh());
    }

    private void edit() {
        final AnnotationTag annotationTag = annotationTagListPresenter.getSelectionModel().getSelected();
        if (annotationTag != null) {
            edit(annotationTag);
        }
    }

    private void edit(final AnnotationTag annotationTag) {
        final AnnotationTagEditPresenter editor = editProvider.get();
        final String caption = "Edit " + annotationTagType.getDisplayValue() + " - " + annotationTag.getName();
        editor.open(annotationTag, caption, result -> refresh());
    }

    private void delete() {
        final List<AnnotationTag> list = annotationTagListPresenter.getSelectionModel().getSelectedItems();
        if (list != null && !list.isEmpty()) {
            final String message = "Are you sure you want to delete the selected " +
                                   annotationTagType.getDisplayValue().toLowerCase() +
                                   "?";
            ConfirmEvent.fire(AnnotationTagPresenter.this, message,
                    result -> {
                        if (result) {
                            annotationTagListPresenter.getSelectionModel().clear();
                            for (final AnnotationTag group : list) {
                                annotationResourceClient.deleteAnnotationGroup(group, response -> refresh(), this);
                            }
                        }
                    });
        }
    }

    private void enableButtons() {
        final boolean enabled = annotationTagListPresenter.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    public void refresh() {
        annotationTagListPresenter.refresh();
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
        annotationTagListPresenter.setAnnotationTagType(annotationTagType);
    }
}
