/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.elastic.client.component;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.component.ExpressionEditorController;
import stroom.query.client.model.FieldSelectionListModel;
import stroom.search.elastic.client.model.ElasticVirtualFieldGroups;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// A resizable dialog for adding or editing a group of a virtual field
/// ([ElasticVirtualFieldGroupEditor]), made for one use: the group's name and the query run when users
/// search the virtual field for it. OK refuses while the name can't be used, saying why under it.
/// For a read-only index it only shows the group, with Close.
public class ElasticVirtualFieldGroupEditorController extends Controller {

    private final ElasticVirtualFieldGroupEditor view;
    private final ExpressionEditorController expressionEditor;

    /// Made by GIN for one use, with the dialog's content.
    ///
    /// @param eventBus         The application's event bus, which the dialog is shown on.
    /// @param view             The dialog's content.
    /// @param expressionEditor Edits the group's query.
    @Inject
    public ElasticVirtualFieldGroupEditorController(final EventBus eventBus,
                                                    final ElasticVirtualFieldGroupEditor view,
                                                    final ExpressionEditorController expressionEditor) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.expressionEditor = own(expressionEditor).attach(view.getExpressionEditor());
    }

    /// Shows the dialog, owned by `opener` until it is closed.
    ///
    /// @param opener     What shows it.
    /// @param index      The index, whose fields the query's terms may use.
    /// @param fields     The fields the query's terms may use.
    /// @param groups     The virtual field's groups, which a group's name must differ from.
    /// @param groupIndex The position of the group to edit, or -1 to add one.
    /// @param readOnly   Whether only to show the group, with no means to change it.
    /// @param onOk       Given the new or edited group when OK is pressed; not called if read only.
    public void show(final HasControllerNode opener,
                     final DocRef index,
                     final FieldSelectionListModel fields,
                     final List<ElasticVirtualFieldGroup> groups,
                     final int groupIndex,
                     final boolean readOnly,
                     final Consumer<ElasticVirtualFieldGroup> onOk) {
        Objects.requireNonNull(onOk, "onOk");
        final List<ElasticVirtualFieldGroup> otherGroups = new ArrayList<>(NullSafe.list(groups));
        final int editIndex = groupIndex >= 0 && groupIndex < otherGroups.size()
                ? groupIndex
                : -1;
        final ElasticVirtualFieldGroup group = editIndex >= 0
                ? otherGroups.get(editIndex)
                : null;

        view.setName(NullSafe.get(group, ElasticVirtualFieldGroup::getName));
        view.setInvalidReason(name -> ElasticVirtualFieldGroups.validateName(otherGroups, editIndex, name));
        view.setReadOnly(readOnly);
        expressionEditor.init(index, fields);
        expressionEditor.setReadOnly(readOnly);
        expressionEditor.read(Objects.requireNonNullElseGet(
                NullSafe.get(group, ElasticVirtualFieldGroup::getExpression),
                () -> ExpressionOperator.builder().build()));

        ControllerDialog.showOnce(opener, this)
                .popupType(readOnly
                        ? PopupType.CLOSE_DIALOG
                        : PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(800, 600, 500, 400))
                .caption(readOnly
                        ? "View Group"
                        : editIndex >= 0
                                ? "Edit Group"
                                : "Add Group")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    e.hide();
                    if (!readOnly && e.isOk()) {
                        onOk.accept(new ElasticVirtualFieldGroup(view.getName(), expressionEditor.write()));
                    }
                })
                .fire();
    }
}
