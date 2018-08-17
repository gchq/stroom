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

package stroom.dashboard.client.query;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

public class NamePresenter extends MyPresenterWidget<NamePresenter.NameView> {
    @Inject
    public NamePresenter(final EventBus eventBus, final NameView view) {
        super(eventBus, view);
    }

    public String getName() {
        return getView().getName();
    }

    public void setName(final String name) {
        getView().setName(name);
    }

    public void setUihandlers(final PopupUiHandlers uiHandlers) {
        getView().setUiHandlers(uiHandlers);
    }

    public interface NameView extends View, HasUiHandlers<PopupUiHandlers> {
        String getName();

        void setName(String name);

        void focus();
    }
}