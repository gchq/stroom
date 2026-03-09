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

package stroom.node.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.grid.client.WrapperView;
import stroom.node.client.NodeGroupClient;
import stroom.node.shared.NodeGroup;
import stroom.svg.client.IconColour;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

public class NodeGroupPresenter extends ContentTabPresenter<WrapperView> {

    public static final String TAB_TYPE = "NodeGroups";

    private final NodeGroupListPresenter nodeGroupListPresenter;
    private final Provider<NodeGroupEditPresenter> editProvider;
    private final NodeGroupClient nodeGroupClient;
    private final Provider<NewNodeGroupPresenter> newNodeGroupPresenterProvider;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;

    @Inject
    public NodeGroupPresenter(
            final EventBus eventBus,
            final WrapperView view,
            final NodeGroupListPresenter nodeGroupListPresenter,
            final Provider<NodeGroupEditPresenter> editProvider,
            final NodeGroupClient nodeGroupClient,
            final Provider<NewNodeGroupPresenter> newNodeGroupPresenterProvider) {

        super(eventBus, view);
        this.nodeGroupListPresenter = nodeGroupListPresenter;
        this.editProvider = editProvider;
        this.nodeGroupClient = nodeGroupClient;
        this.newNodeGroupPresenterProvider = newNodeGroupPresenterProvider;

        newButton = nodeGroupListPresenter.getView().addButton(SvgPresets.NEW_ITEM);
        openButton = nodeGroupListPresenter.getView().addButton(SvgPresets.EDIT);
        deleteButton = nodeGroupListPresenter.getView().addButton(SvgPresets.DELETE);

        view.setView(nodeGroupListPresenter.getView());
        refresh();
    }

    @Override
    protected void onBind() {
        registerHandler(nodeGroupListPresenter.getSelectionModel().addSelectionHandler(event -> {
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
        final NewNodeGroupPresenter presenter = newNodeGroupPresenterProvider.get();
        presenter.show("", nodeGroup -> {
            edit(nodeGroup);
            refresh();
        });
    }

    private void edit() {
        final NodeGroup nodeGroup = nodeGroupListPresenter.getSelectionModel().getSelected();
        if (nodeGroup != null) {
            nodeGroupClient.fetchById(nodeGroup.getId(), this::edit, this);
        }
    }

    private void edit(final NodeGroup nodeGroup) {
        final NodeGroupEditPresenter editor = editProvider.get();
        editor.show(nodeGroup, "Edit Node Group - " + nodeGroup.getName(), result -> refresh());
    }

    private void delete() {
        final List<NodeGroup> list = nodeGroupListPresenter.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected node group?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected node groups?";
            }
            ConfirmEvent.fire(NodeGroupPresenter.this, message,
                    result -> {
                        if (result) {
                            nodeGroupListPresenter.getSelectionModel().clear();
                            for (final NodeGroup nodeGroup : list) {
                                nodeGroupClient.delete(nodeGroup.getId(), response -> refresh(), this);
                            }
                        }
                    });
        }
    }

    private void enableButtons() {
        final boolean enabled = nodeGroupListPresenter.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    private void refresh() {
        nodeGroupListPresenter.refresh();
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
