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

package stroom.dashboard.client.table;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.table.RenameColumnPresenter.RenameColumnView;
import stroom.query.api.Column;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.function.BiConsumer;

public class RenameColumnPresenter
        extends MyPresenterWidget<RenameColumnView>
        implements HidePopupRequestEvent.Handler {

    private TablePresenter tablePresenter;
    private Column column;
    private BiConsumer<Column, Column> columnChangeConsumer;

    @Inject
    public RenameColumnPresenter(final EventBus eventBus, final RenameColumnView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getNameBox().addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                HidePopupRequestEvent.builder(this).fire();
            }
        }));
    }

    public void show(final TablePresenter tablePresenter,
                     final Column column,
                     final BiConsumer<Column, Column> columnChangeConsumer) {
        this.tablePresenter = tablePresenter;
        this.column = column;
        this.columnChangeConsumer = columnChangeConsumer;

        getView().getName().setText(column.getName());

        final PopupSize popupSize = PopupSize.resizableX();

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Rename Field")
                .onShow(e -> getView().focus())
                .onHideRequest(this)
                .fire();
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            final String newFieldName = getView().getName().getText();
            if (newFieldName != null
                    && !newFieldName.trim().isEmpty()
                    && !Objects.equals(newFieldName, column.getName())) {

                // Need to ensure any conditional formatting rules that use this field name
                // are renamed too.
                tablePresenter.handleFieldRename(column.getName(), newFieldName);

                final boolean isNameInUse = tablePresenter.getTableComponentSettings()
                        .getColumns()
                        .stream()
                        .map(Column::getName)
                        .anyMatch(name -> Objects.equals(name, newFieldName));

                if (isNameInUse) {
                    AlertEvent.fireError(
                            tablePresenter,
                            "Field name \"" + newFieldName + "\" is already in use",
                            e::reset);
                } else {
                    columnChangeConsumer.accept(column, column.copy().name(newFieldName).build());
                    e.hide();
                }
            } else {
                e.hide();
            }
        } else {
            e.hide();
        }
    }

    public String getName() {
        return getView().getName().getText();
    }

    public interface RenameColumnView extends View, Focus {

        HasText getName();

        HasKeyDownHandlers getNameBox();
    }
}
