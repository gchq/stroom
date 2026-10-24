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

package stroom.data.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.client.ExpressionUiHandlers;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.Toolbar;
import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.contextmenu.client.TextEditTarget;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class EditExpressionPresenter extends MyPresenterWidget<EditExpressionPresenter.EditExpressionView>
        implements Focus {

    private final ExpressionTreePresenter expressionPresenter;
    private final MySingleSelectionModel<stroom.query.client.Item> selectionModel;
    private boolean readOnly;

    private final ButtonView addOperatorButton;
    private final ButtonView addTermButton;
    private final ButtonView copyButton;
    private final ButtonView disableItemButton;
    private final ButtonView deleteItemButton;

    private Runnable onChange = () -> {
    };

    @Inject
    public EditExpressionPresenter(final EventBus eventBus,
                                   final EditExpressionView view,
                                   final ExpressionTreePresenter expressionPresenter) {
        super(eventBus, view);
        own(expressionPresenter);
        this.expressionPresenter = expressionPresenter;
        this.selectionModel = expressionPresenter.getSelectionModel();

        view.setExpressionView(expressionPresenter.getView());

        expressionPresenter.setUiHandlers(new ExpressionUiHandlers() {
            @Override
            public void onChange() {
                EditExpressionPresenter.this.onChange();
            }

            @Override
            public void search() {

            }
        });

        addTermButton = view.getToolbar().button(SvgImage.ADD, "Add Term", this::addTerm);
        addOperatorButton = view.getToolbar().button(SvgImage.OPERATOR, "Add Operator", this::addOperator);
        copyButton = view.getToolbar().button(SvgImage.COPY, "Duplicate Selected Expression Item", this::copy);
        copyButton.setEnabled(false);
        disableItemButton = view.getToolbar().button(SvgImage.DISABLE,
                "Disable Selected Expression Item", this::disable);
        disableItemButton.setEnabled(false);
        deleteItemButton = view.getToolbar().button(SvgImage.DELETE, "Delete Selected Expression Item", this::delete);
        deleteItemButton.setEnabled(false);

        own(expressionPresenter.addSelectionListener(event -> setButtonsEnabled()));
        own(expressionPresenter.addContextMenuHandler(event -> {
            // On a term's field, the field's edit items come first; a read-only expression has
            // no actions of its own, but its fields can still be copied from
            final List<Item> actions = readOnly
                    ? List.of()
                    : addExpressionActionsToMenu();
            final List<Item> menuItems = ContextMenuManager.withEditItems(event.getEditTarget(), actions);
            if (NullSafe.hasItems(menuItems)) {
                showMenu(menuItems, event.getPopupPosition(), event.getEditTarget());
            }
        }));
    }

    private void copy() {
        expressionPresenter.copy();
    }

    @Override
    public void focus() {
        addTermButton.focus();
    }

    public void init(final RestFactory restFactory,
                     final DocRef dataSource,
                     final FieldSelectionListModel fieldSelectionListModel) {
        expressionPresenter.init(restFactory, dataSource, fieldSelectionListModel);
    }

    /// Stops the expression being changed, for a document the user can't change: terms can't be
    /// selected, edited or dragged, and the add, copy, disable and delete buttons and the
    /// context menu do nothing.
    ///
    /// @param readOnly Whether the expression can't be changed.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        expressionPresenter.setSelectionModel(readOnly
                ? null
                : selectionModel);
        addTermButton.setEnabled(!readOnly);
        addOperatorButton.setEnabled(!readOnly);
        setButtonsEnabled();
        // The read-only look: the terms greyed, as a read-only field's value is
        expressionPresenter.setReadOnlyLook(readOnly);
    }

    private void setButtonsEnabled() {
        final boolean canChangeItem = !readOnly && getSelectedItem() != null;
        copyButton.setEnabled(canChangeItem);
        disableItemButton.setEnabled(canChangeItem);
        // The title is the button's name, so it stays when the button is disabled; it follows the
        // selected item, enabling a disabled one
        disableItemButton.setTitle(getEnableDisableText() + " Selected Expression Item");
        deleteItemButton.setEnabled(canChangeItem);
    }

    /**
     * @see ExpressionTreePresenter#setConditionFilter(Predicate)
     */
    public void setConditionFilter(final Predicate<Condition> conditionFilter) {
        expressionPresenter.setConditionFilter(conditionFilter);
    }

    public void read(final ExpressionOperator expressionOperator) {
        expressionPresenter.read(expressionOperator);
    }

    public ExpressionOperator write() {
        return expressionPresenter.write();
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
        final stroom.query.client.Item selectedItem = getSelectedItem();
        final boolean hasSelection = selectedItem != null;

        final List<Item> menuItems = new ArrayList<>();
        menuItems.add(new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.ADD)
                .text("Add Term")
                .command(this::addTerm)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(2)
                .icon(SvgImage.OPERATOR)
                .text("Add Operator")
                .command(this::addOperator)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(3)
                .icon(SvgImage.DISABLE)
                .text(getEnableDisableText())
                .enabled(hasSelection)
                .command(this::disable)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(4)
                .icon(SvgImage.DELETE)
                .text("Delete")
                .enabled(hasSelection)
                .command(this::delete)
                .build());
        menuItems.addAll(expressionPresenter.getMoveMenuItems(5));

        return menuItems;
    }

    private String getEnableDisableText() {
        final stroom.query.client.Item selectedItem = getSelectedItem();
        if (selectedItem != null && !selectedItem.isEnabled()) {
            return "Enable";
        }
        return "Disable";
    }

    private stroom.query.client.Item getSelectedItem() {
        if (expressionPresenter.getSelectionModel() != null) {
            return expressionPresenter.getSelectionModel().getSelectedObject();
        }
        return null;
    }

    private void showMenu(final List<Item> menuItems,
                          final PopupPosition popupPosition,
                          final TextEditTarget textField) {
        final ShowMenuEvent.Builder builder = ShowMenuEvent
                .builder()
                .name(getMenuName())
                .items(menuItems)
                .popupPosition(popupPosition);
        if (textField != null) {
            // Opened on a term's field: the focus goes back to it
            builder.onHide(e -> textField.restore());
        }
        builder.fire(this);
    }

    // The context menu's accessible name, saying which term or operator it acts on
    private String getMenuName() {
        final stroom.query.client.Item selectedItem = getSelectedItem();
        final String text = selectedItem != null
                ? selectedItem.toString().trim()
                : "";
        return text.isEmpty()
                ? "Expression Actions"
                : "Actions for expression item '" + text + "'";
    }

    private void onChange() {
        onChange.run();
    }

    /// @param onChange What to run when the user changes the expression.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }


    // --------------------------------------------------------------------------------


    public interface EditExpressionView extends View {

        /// The toolbar for the view's buttons, e.g. `getToolbar().button(SvgImage.ADD, "Add", this::add)`.
        ///
        /// @return The toolbar.
        Toolbar getToolbar();

        void setExpressionView(View view);
    }
}
