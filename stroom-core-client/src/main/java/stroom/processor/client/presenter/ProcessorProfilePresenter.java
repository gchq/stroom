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

package stroom.processor.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.grid.client.WrapperView;
import stroom.data.table.client.Refreshable;
import stroom.processor.shared.ProcessorProfile;
import stroom.svg.client.IconColour;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

public class ProcessorProfilePresenter extends ContentTabPresenter<WrapperView> implements Refreshable {

    public static final String TAB_TYPE = "ProcessorProfiles";

    private final ProcessorProfileListPresenter processorProfileListPresenter;
    private final Provider<ProcessorProfileEditPresenter> editProvider;
    private final ProcessorProfileClient nodeGroupClient;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;

    @Inject
    public ProcessorProfilePresenter(
            final EventBus eventBus,
            final WrapperView view,
            final ProcessorProfileListPresenter processorProfileListPresenter,
            final Provider<ProcessorProfileEditPresenter> editProvider,
            final ProcessorProfileClient nodeGroupClient) {

        super(eventBus, view);
        this.processorProfileListPresenter = processorProfileListPresenter;
        this.editProvider = editProvider;
        this.nodeGroupClient = nodeGroupClient;

        newButton = processorProfileListPresenter.getView().addButton(SvgPresets.NEW_ITEM);
        openButton = processorProfileListPresenter.getView().addButton(SvgPresets.EDIT);
        deleteButton = processorProfileListPresenter.getView().addButton(SvgPresets.DELETE);

        view.setView(processorProfileListPresenter.getView());
    }

    @Override
    protected void onBind() {
        registerHandler(processorProfileListPresenter.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                edit();
            }
        }));
        registerHandler(newButton.addClickHandler(event -> add()));
        registerHandler(openButton.addClickHandler(event -> edit()));
        registerHandler(deleteButton.addClickHandler(event -> delete()));
    }

//    public void show() {
//        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
//            @Override
//            public void onHideRequest(final boolean autoClose, final boolean ok) {
//                hide();
//            }
//        };
//        final PopupSize popupSize = PopupSize.resizable(1000, 600);
//        ShowPopupEvent.fire(
//                this,
//                this,
//                PopupType.CLOSE_DIALOG,
//                null,
//                popupSize,
//                "Node Groups",
//                popupUiHandlers,
//                null);
//    }
//
//    public void hide() {
//        HidePopupEvent.fire(this, this, false, true);
//    }

    private void add() {
//        final NewProcessorProfilePresenter presenter = newProcessorProfilePresenterProvider.get();
//        presenter.show("", nodeGroup -> {
//            edit(nodeGroup);
//            refresh();
//        });
    }

    private void edit() {
        final ProcessorProfile nodeGroup = processorProfileListPresenter.getSelectionModel().getSelected();
        if (nodeGroup != null) {
            nodeGroupClient.fetch(nodeGroup.getId(), this::edit, this);
        }
    }

    private void edit(final ProcessorProfile nodeGroup) {
        final ProcessorProfileEditPresenter editor = editProvider.get();
        editor.show(nodeGroup, "Edit Node Group - " + nodeGroup.getName(), result -> refresh());
    }

    private void delete() {
        final List<ProcessorProfile> list = processorProfileListPresenter.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected node group?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected node groups?";
            }
            ConfirmEvent.fire(ProcessorProfilePresenter.this, message,
                    result -> {
                        if (result) {
                            processorProfileListPresenter.getSelectionModel().clear();
                            for (final ProcessorProfile nodeGroup : list) {
                                nodeGroupClient.delete(nodeGroup.getId(), response -> refresh(), this);
                            }
                        }
                    });
        }
    }

    private void enableButtons() {
        final boolean enabled = processorProfileListPresenter.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    @Override
    public void refresh() {
        processorProfileListPresenter.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.NODES;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Node Groups";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
