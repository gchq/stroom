/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.streamstore.client.presenter;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.client.ExpressionUiHandlers;
import stroom.query.shared.ExpressionItem;
import stroom.query.shared.ExpressionOperator;
import stroom.streamstore.shared.FetchFieldsAction;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcon;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.button.client.ImageButtonView;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.presenter.ImageIcon;

import java.util.ArrayList;
import java.util.List;

public class EditExpressionPresenter extends MyPresenterWidget<EditExpressionPresenter.EditExpressionView> implements HasDirtyHandlers {
    private final ExpressionTreePresenter expressionPresenter;
    private final Resources resources;
    private final MenuListPresenter menuListPresenter;

    private final ImageButtonView addOperatorButton;
    private final GlyphButtonView addTermButton;
    private final GlyphButtonView disableItemButton;
    private final GlyphButtonView deleteItemButton;

    @Inject
    public EditExpressionPresenter(final EventBus eventBus, final EditExpressionView view,
                                   final ExpressionTreePresenter expressionPresenter, final Resources resources,
                                   final MenuListPresenter menuListPresenter, final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.expressionPresenter = expressionPresenter;
        this.menuListPresenter = menuListPresenter;
        this.resources = resources;

        view.setExpressionView(expressionPresenter.getView());

        expressionPresenter.setUiHandlers(new ExpressionUiHandlers() {
            @Override
            public void fireDirty() {
                setDirty(true);
            }

            @Override
            public void search() {

            }
        });

        addTermButton = view.addButton(GlyphIcons.ADD);
        addTermButton.setTitle("Add Term");
        addOperatorButton = view.addButton("Add Operator", resources.addOperator(), resources.addOperator(), true);
        disableItemButton = view.addButton(GlyphIcons.DISABLE);
        deleteItemButton = view.addButton(GlyphIcons.DELETE);

        dispatcher.exec(new FetchFieldsAction()).onSuccess(result -> expressionPresenter.setFields(result.getIndexFields()));
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(expressionPresenter.addDataSelectionHandler(event -> setButtonsEnabled()));
        registerHandler(expressionPresenter.addContextMenuHandler(event -> {
            final List<Item> menuItems = addExpressionActionsToMenu();
            if (menuItems != null && menuItems.size() > 0) {
                final PopupPosition popupPosition = new PopupPosition(event.getX(), event.getY());
                showMenu(popupPosition, menuItems);
            }
        }));
        registerHandler(addOperatorButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                addOperator();
            }
        }));
        registerHandler(addTermButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                addTerm();
            }
        }));
        registerHandler(disableItemButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                disable();
            }
        }));
        registerHandler(deleteItemButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                delete();
            }
        }));
    }

    private void setButtonsEnabled() {
        final ExpressionItem selectedItem = getSelectedItem();

        if (selectedItem == null) {
            disableItemButton.setEnabled(false);
            disableItemButton.setTitle("");
        } else {
            disableItemButton.setEnabled(true);
            disableItemButton.setTitle(getEnableDisableText());
        }

        if (selectedItem == null) {
            deleteItemButton.setEnabled(false);
            deleteItemButton.setTitle("");
        } else {
            deleteItemButton.setEnabled(true);
            deleteItemButton.setTitle("Delete");
        }
    }

    public void read(final ExpressionOperator expressionOperator) {
        expressionPresenter.read(expressionOperator);
    }

    public ExpressionOperator write() {
        final ExpressionOperator root = new ExpressionOperator();
        expressionPresenter.write(root);
        return root;
    }

    private void addOperator() {
        expressionPresenter.addOperator();
    }

    private void addTerm() {
        expressionPresenter.addTerm();
    }

    private void disable() {
        expressionPresenter.disable();
        setButtonsEnabled();
    }

    private void delete() {
        expressionPresenter.delete();
    }

    private List<Item> addExpressionActionsToMenu() {
        final ExpressionItem selectedItem = getSelectedItem();
        final boolean hasSelection = selectedItem != null;

        final List<Item> menuItems = new ArrayList<Item>();
        menuItems.add(new IconMenuItem(1, GlyphIcons.ADD, GlyphIcons.ADD, "Add Term", null, true, () -> addTerm()));
        menuItems.add(new IconMenuItem(2, ImageIcon.create(resources.addOperator()), ImageIcon.create(resources.addOperator()), "Add Operator", null,
                true, () -> addOperator()));
        menuItems.add(new IconMenuItem(3, GlyphIcons.DISABLE, GlyphIcons.DISABLE, getEnableDisableText(),
                null, hasSelection, () -> disable()));
        menuItems.add(new IconMenuItem(4, GlyphIcons.DELETE, GlyphIcons.DELETE, "Delete", null,
                hasSelection, () -> delete()));

        return menuItems;
    }

    private String getEnableDisableText() {
        final ExpressionItem selectedItem = getSelectedItem();
        if (selectedItem != null && !selectedItem.isEnabled()) {
            return "Enable";
        }
        return "Disable";
    }

    private ExpressionItem getSelectedItem() {
        if (expressionPresenter.getSelectionModel() != null) {
            return expressionPresenter.getSelectionModel().getSelectedObject();
        }
        return null;
    }

    private void showMenu(final PopupPosition popupPosition, final List<Item> menuItems) {
        menuListPresenter.setData(menuItems);

        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                HidePopupEvent.fire(EditExpressionPresenter.this, menuListPresenter);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        };
        ShowPopupEvent.fire(this, menuListPresenter, PopupType.POPUP, popupPosition, popupUiHandlers);
    }

    public void setDirty(final boolean dirty) {
        if (dirty) {
            DirtyEvent.fire(this, dirty);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface EditExpressionView extends View {
        ImageButtonView addButton(String title, ImageResource enabledImage, ImageResource disabledImage,
                                  boolean enabled);

        GlyphButtonView addButton(GlyphIcon preset);

        void setExpressionView(View view);
    }

    public interface Resources extends ClientBundle {
        ImageResource addOperator();
    }
}
