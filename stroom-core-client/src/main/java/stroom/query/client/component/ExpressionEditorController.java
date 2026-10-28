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

package stroom.query.client.component;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.contextmenu.client.TextEditTarget;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/// An expression's editor ([ExpressionEditor]): its toolbar's and context menu's actions on the
/// expression's tree ([ExpressionTreeController]), which it owns. Used wherever an expression is
/// edited: a processor's filter, a data retention or receipt rule, a dashboard table's conditional
/// formatting rule and selection handler, and more.
public class ExpressionEditorController extends Controller implements Focus {

    private final ExpressionEditor view;
    private final ExpressionTreeController expressionTree;
    private final MySingleSelectionModel<stroom.query.client.Item> selectionModel;
    private boolean readOnly;

    private Runnable onChange = () -> {
    };

    /// Made by GIN, with its editor and the tree it shows.
    ///
    /// @param eventBus            The application's event bus, which the context menu is shown on.
    /// @param view                The editor.
    /// @param expressionTree The expression's tree.
    @Inject
    public ExpressionEditorController(final EventBus eventBus,
                                      final ExpressionEditor view,
                                      final ExpressionTreeController expressionTree) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.expressionTree = own(expressionTree);
        this.selectionModel = expressionTree.getSelectionModel();

        view.setExpressionTree(expressionTree);

        expressionTree.setOnChange(this::onChange);

        view.setOnAddTerm(this::addTerm);
        view.setOnAddOperator(this::addOperator);
        view.setOnCopy(this::copy);
        view.setOnDisable(this::disable);
        view.setOnDelete(this::delete);

        own(expressionTree.addSelectionListener(event -> setButtonsEnabled()));
        own(expressionTree.addContextMenuHandler(event -> {
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

    @Override
    public void focus() {
        view.focus();
    }

    /// Sets what a term's field is chosen from, and its values suggested from.
    ///
    /// @param dataSource              The data source, or null for none (no values are suggested).
    /// @param fieldSelectionListModel The fields.
    public void init(final DocRef dataSource,
                     final FieldSelectionListModel fieldSelectionListModel) {
        expressionTree.init(dataSource, fieldSelectionListModel);
    }

    /// Stops the expression being changed, for a document the user can't change: terms can't be
    /// selected, edited or dragged, and the add, copy, disable and delete buttons and the
    /// context menu do nothing. Set by its owner rather than watched
    /// ([stroom.widget.component.client.Contexts#READ_ONLY]), as an expression that is a search (a
    /// filter) is changed even in a document the user can't change, and that context only ever
    /// narrows.
    ///
    /// @param readOnly Whether the expression can't be changed.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        expressionTree.setSelectionModel(readOnly
                ? null
                : selectionModel);
        view.setCanAdd(!readOnly);
        setButtonsEnabled();
        // The read-only look: the terms greyed, as a read-only field's value is
        expressionTree.setReadOnlyLook(readOnly);
    }

    /// @param framed Whether the editor is framed as a control is, as it is unless its owner frames
    ///               it (see [ExpressionEditor#setFramed(boolean)]).
    public void setFramed(final boolean framed) {
        view.setFramed(framed);
    }

    /// Offer only the conditions that pass `conditionFilter`, on top of each field's own
    /// `ConditionSet` (see [ExpressionTreeController#setConditionFilter(Predicate)]).
    ///
    /// @param conditionFilter Which conditions are offered.
    public void setConditionFilter(final Predicate<Condition> conditionFilter) {
        expressionTree.setConditionFilter(conditionFilter);
    }

    /// Sets the selected term's value, e.g. one chosen from a list.
    ///
    /// @param value The value.
    public void insertValue(final String value) {
        expressionTree.insertValue(value);
    }

    /// @param expressionOperator The expression to edit.
    public void read(final ExpressionOperator expressionOperator) {
        expressionTree.read(expressionOperator);
    }

    /// @return The expression as edited.
    public ExpressionOperator write() {
        return expressionTree.write();
    }

    /// @param onChange What to run when the user changes the expression.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    private void setButtonsEnabled() {
        final stroom.query.client.Item selectedItem = getSelectedItem();
        view.setItemActions(!readOnly && selectedItem != null,
                selectedItem != null && !selectedItem.isEnabled());
    }

    private void addOperator() {
        expressionTree.addOperator();
    }

    private void addTerm() {
        expressionTree.addTerm();
    }

    private void copy() {
        expressionTree.copy();
    }

    private void disable() {
        expressionTree.disable();
        setButtonsEnabled();
    }

    private void delete() {
        expressionTree.delete();
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
        menuItems.addAll(expressionTree.getMoveMenuItems(5));

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
        if (expressionTree.getSelectionModel() != null) {
            return expressionTree.getSelectionModel().getSelectedObject();
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
}
