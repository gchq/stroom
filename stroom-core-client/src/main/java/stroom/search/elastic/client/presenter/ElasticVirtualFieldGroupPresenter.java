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

package stroom.search.elastic.client.presenter;

import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.search.elastic.client.presenter.ElasticVirtualFieldGroupPresenter.ElasticVirtualFieldGroupView;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.DialogEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// A resizable dialog for adding or editing a group of a virtual field: the group's name and the query run when
/// users search the virtual field for it.
///
/// The name is checked as it is typed and again when OK is pressed; the dialog stays open while the name is not
/// valid. Nothing is changed unless OK is pressed with a valid name. For a read-only doc the dialog only shows the
/// group, with a Close button.
public class ElasticVirtualFieldGroupPresenter
        extends MyPresenterWidget<ElasticVirtualFieldGroupView>
        implements ElasticVirtualFieldDialogUiHandlers {

    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final int MIN_WIDTH = 500;
    private static final int MIN_HEIGHT = 400;

    private final EditExpressionPresenter expressionPresenter;
    private final RestFactory restFactory;

    private DocRef indexDocRef;
    private List<ElasticVirtualFieldGroup> groups = new ArrayList<>();
    private int index = -1;

    @Inject
    public ElasticVirtualFieldGroupPresenter(final EventBus eventBus,
                                             final ElasticVirtualFieldGroupView view,
                                             final EditExpressionPresenter expressionPresenter,
                                             final RestFactory restFactory) {
        super(eventBus, view);
        this.expressionPresenter = expressionPresenter;
        this.restFactory = restFactory;
        view.setUiHandlers(this);
        view.setExpressionView(expressionPresenter.getView());
    }

    /// Sets the index whose fields a group's query may use.
    ///
    /// @param indexDocRef             the index doc, used when picking terms for the query
    /// @param fieldSelectionListModel the fields the query may use
    public void setIndex(final DocRef indexDocRef, final FieldSelectionListModel fieldSelectionListModel) {
        if (indexDocRef != null && !indexDocRef.equals(this.indexDocRef)) {
            this.indexDocRef = indexDocRef;
            expressionPresenter.init(restFactory, indexDocRef, fieldSelectionListModel);
        }
    }

    /// Shows the dialog.
    ///
    /// @param groups   the virtual field's existing groups, used to check the name is unique
    /// @param index    the position of the group to edit, or -1 to add a new group
    /// @param readOnly `true` to only show the group, without the means to change it
    /// @param onOk     given the new or edited group when OK is pressed with a valid name; not called if `readOnly`
    public void show(final List<ElasticVirtualFieldGroup> groups,
                     final int index,
                     final boolean readOnly,
                     final Consumer<ElasticVirtualFieldGroup> onOk) {
        this.groups = new ArrayList<>(NullSafe.list(groups));
        this.index = index >= 0 && index < this.groups.size()
                ? index
                : -1;
        final ElasticVirtualFieldGroup group = this.index >= 0
                ? this.groups.get(this.index)
                : null;

        getView().setName(NullSafe.get(group, ElasticVirtualFieldGroup::getName));
        getView().setNameError(null);
        getView().setNameEditable(!readOnly);
        expressionPresenter.read(Objects.requireNonNullElseGet(
                NullSafe.get(group, ElasticVirtualFieldGroup::getExpression),
                () -> ExpressionOperator.builder().build()));

        ShowPopupEvent.builder(this)
                .popupType(readOnly
                        ? PopupType.CLOSE_DIALOG
                        : PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DEFAULT_WIDTH, DEFAULT_HEIGHT, MIN_WIDTH, MIN_HEIGHT))
                .caption(getCaption(readOnly))
                .onShow(event -> getView().focus())
                .onHideRequest(event -> {
                    if (!readOnly && event.isOk()) {
                        final String name = NullSafe.trim(getView().getName());
                        final String error = ElasticVirtualFieldGroups.validateName(this.groups, this.index, name);
                        getView().setNameError(error);
                        if (error == null) {
                            onOk.accept(new ElasticVirtualFieldGroup(name, expressionPresenter.write()));
                            event.hide();
                        } else {
                            event.reset();
                            getView().focus();
                        }
                    } else {
                        event.hide();
                    }
                })
                .fire();
    }

    @Override
    public void onNameEdit() {
        getView().setNameError(ElasticVirtualFieldGroups.validateName(groups, index, getView().getName()));
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        DialogEvent.fire(this, this, action);
    }

    private String getCaption(final boolean readOnly) {
        if (readOnly) {
            return "View Group";
        }
        return index >= 0
                ? "Edit Group"
                : "Add Group";
    }

    /// The view of the virtual field group dialog.
    public interface ElasticVirtualFieldGroupView
            extends View, HasUiHandlers<ElasticVirtualFieldDialogUiHandlers> {

        /// @return the group name as entered
        String getName();

        /// @param name the group name to show, or `null` for none
        void setName(String name);

        /// @param error the reason the group name can't be used, or `null`
        void setNameError(String error);

        /// @param editable `true` if the group name may be changed
        void setNameEditable(boolean editable);

        /// @param view the group's expression editor
        void setExpressionView(View view);

        /// Moves the keyboard focus to the group name.
        void focus();
    }
}
