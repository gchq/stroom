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

package stroom.entity.client.presenter;

import com.google.gwt.resources.client.ImageResource;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.ImageButtonView;
import stroom.widget.button.client.SvgIcon;

public class ManageEntityListPresenter<C extends BaseCriteria, E extends BaseEntity>
        extends MyPresenterWidget<DataGridView<E>> implements Refreshable {
    protected EntityServiceFindActionDataProvider<C, E> dataProvider;

    @Inject
    public ManageEntityListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<E>(true));
    }

    public ImageButtonView addButton(final String title, final ImageResource enabledImage,
                                     final ImageResource disabledImage, final boolean enabled) {
        return getView().addButton(title, enabledImage, disabledImage, enabled);
    }


    public ButtonView addButton(final SvgIcon preset) {
        return getView().addButton(preset);
    }

    @Override
    protected void onReveal() {
        super.onReveal();
        refresh();
    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    public E getSelectedItem() {
        return getView().getSelectionModel().getSelected();
    }

    public void setSelectedItem(final E row) {
        getView().getSelectionModel().setSelected(row);
    }

    public void setCriteria(final C criteria) {
        dataProvider.setCriteria(criteria);
    }
//
//    public HandlerRegistration addSelectionHandler(DataGridSelectEvent.Handler handler) {
//        return getView().addSelectionHandler(handler);
//    }
}
