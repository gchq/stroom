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

package stroom.dashboard.client.table;

import stroom.item.client.presenter.AutocompletePopupView;
import stroom.item.client.view.AutocompletePopupViewImpl;
import stroom.query.api.v2.Field;

import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

public class FieldAddPresenter extends MyPresenterWidget<AutocompletePopupView<Field>> implements
        HasSelectionHandlers<Field> {

    @Inject
    public FieldAddPresenter(final EventBus eventBus) {
        super(eventBus, new AutocompletePopupViewImpl<>());

        // Set the height of the autocomplete list
        getView().setVisibleItemCount(15);
    }

    @Override
    public HandlerRegistration addSelectionHandler(final SelectionHandler<Field> handler) {
        return getView().addSelectionHandler(handler);
    }
}
