/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.client.main;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.CellTable.Resources;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.SelectionChangeEvent.Handler;
import com.google.gwt.view.client.SelectionChangeEvent.HasSelectionChangedHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.data.table.client.CellTableView;
import stroom.data.table.client.CellTableViewImpl;
import stroom.data.table.client.CellTableViewImpl.HoverResources;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.List;

public class ComponentAddPresenter extends MyPresenterWidget<CellTableView<ComponentType>>
        implements HasSelectionChangedHandlers {
    private final MySingleSelectionModel<ComponentType> selectionModel = new MySingleSelectionModel<>();

    @Inject
    public ComponentAddPresenter(final EventBus eventBus) {
        super(eventBus, new CellTableViewImpl<ComponentType>(true, (Resources) GWT.create(HoverResources.class)));
        // // Icon.
        // final Column<ElementType, ImageResource> iconColumn = new
        // Column<ElementType, ImageResource>(
        // new FACell()) {
        // @Override
        // public ImageResource getValue(final ElementType elementType) {
        // final IconProvider provider =
        // iconProviders.getProvider(elementType.getType());
        // if (provider != null) {
        // return provider.getIcon();
        // }
        //
        // return null;
        // }
        // };
        // getView().addColumn(iconColumn);

        // Text.
        final Column<ComponentType, String> textColumn = new Column<ComponentType, String>(new TextCell()) {
            @Override
            public String getValue(final ComponentType type) {
                return type.getName();
            }
        };
        getView().addColumn(textColumn, 200);
        getView().setSelectionModel(selectionModel);
    }

    public void setTypes(final List<ComponentType> types) {
        getView().setRowData(0, types);
        getView().setRowCount(types.size());
    }

    public ComponentType getSelectedObject() {
        return selectionModel.getSelectedObject();
    }

    public void clearSelection() {
        selectionModel.clear();
    }

    @Override
    public HandlerRegistration addSelectionChangeHandler(final Handler handler) {
        return selectionModel.addSelectionChangeHandler(handler);
    }
}
