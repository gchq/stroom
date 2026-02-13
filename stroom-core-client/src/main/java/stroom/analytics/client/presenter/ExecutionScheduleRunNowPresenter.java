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

package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.ExecutionScheduleRunNowPresenter.ExecutionScheduleRunNowView;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;
import javax.inject.Inject;

public class ExecutionScheduleRunNowPresenter
        extends MyPresenterWidget<ExecutionScheduleRunNowView>
        implements ProcessingStatusUiHandlers, HasDirtyHandlers {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);



    @Inject
    public ExecutionScheduleRunNowPresenter(final EventBus eventBus,
                                            final ExecutionScheduleRunNowView view) {
        super(eventBus, view);


        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        super.onBind();
    }

    public void show(final Consumer<Boolean> consumer) {
        final PopupSize popupSize = PopupSize.builder()
                .width(Size
                        .builder()
                        .initial(500)
                        .resizable(false)
                        .build())
                .height(Size
                        .builder()
                        .resizable(false)
                        .build())
                .build();

        ShowPopupEvent.builder(this)
                .popupType(PopupType.SELECTED_AND_FILTERED_DIALOG)
                .popupSize(popupSize)
                .onShow(e -> getView().focus())
                .caption("Run Now")
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        consumer.accept(e.getAction().isApplyToFiltered());
                        e.hide();
                    } else {
                        e.hide();
                    }
                    e.hide();
                })
                .fire();
    }

    @Override
    public void onRefreshProcessingStatus() {
    }

    @Override
    public void onDirty() {
        DirtyEvent.fire(this, true);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface ExecutionScheduleRunNowView
            extends View,
            HasUiHandlers<ProcessingStatusUiHandlers>,
            Focus {

        void setText(final String text);
    }
}
