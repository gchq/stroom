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

package stroom.streamstore.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.entity.shared.DocRef;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.List;

public class EntityChoicePresenter extends MyPresenterWidget<EntityChoicePresenter.EntityChoiceView>
        implements HasDataSelectionHandlers<DocRef> {
    @Inject
    public EntityChoicePresenter(final EventBus eventBus, final EntityChoiceView view) {
        super(eventBus, view);
    }

    public void setData(final List<DocRef> data) {
        getView().setData(data);
    }

    public void show() {
        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final DocRef entity = getView().getEntity();
                    if (entity != null) {
                        DataSelectionEvent.fire(EntityChoicePresenter.this, entity, false);
                    }
                }
                HidePopupEvent.fire(EntityChoicePresenter.this, EntityChoicePresenter.this);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        };
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, "Add Stream Type", popupUiHandlers);
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<DocRef> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    public interface EntityChoiceView extends View {
        void setData(List<DocRef> data);

        DocRef getEntity();
    }
}
