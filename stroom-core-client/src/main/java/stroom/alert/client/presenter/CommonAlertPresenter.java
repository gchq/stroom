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

package stroom.alert.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.CommonAlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;

public class CommonAlertPresenter
        extends MyPresenterWidget<CommonAlertPresenter.CommonAlertView>
        implements HidePopupEvent.Handler {

    private final List<CommonAlertEvent<?>> stack = new ArrayList<>();

    @Inject
    public CommonAlertPresenter(final EventBus eventBus, final CommonAlertView view) {
        super(eventBus, view);
    }

    public void show(final CommonAlertEvent<?> event) {
        stack.add(event);
        if (stack.size() == 1) {
            doShow();
        }
    }

    private void doShow() {
        final CommonAlertEvent<?> event = stack.get(0);

        if (CommonAlertEvent.Level.INFO.equals(event.getLevel())) {
            getView().setInfo(event.getMessage());
        } else if (CommonAlertEvent.Level.QUESTION.equals(event.getLevel())) {
            getView().setQuestion(event.getMessage());
        } else if (CommonAlertEvent.Level.WARN.equals(event.getLevel())) {
            getView().setWarn(event.getMessage());
        } else if (CommonAlertEvent.Level.ERROR.equals(event.getLevel())) {
            getView().setError(event.getMessage());
        } else {
            getView().setError(event.getMessage());
        }

        final SafeHtml detail = event.getDetail();
        final int height;
        if (detail == null || detail.asString().trim().isEmpty()) {
            height = 200;
        } else {
            height = 500;
        }

        getView().setDetail(detail);

        final PopupSize popupSize = PopupSize.builder()
                .width(Size
                        .builder()
                        .initial(600)
                        .min(200)
                        .resizable(true)
                        .build())
                .height(Size
                        .builder()
//                        .initial(height)
//                        .min(200)
                        .resizable(true)
                        .build())
                .build();

        if (event instanceof ConfirmEvent) {
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .caption("Confirm")
                    .modal(true)
                    .popupSize(popupSize)
                    .onHide(this)
                    .fire();
        } else {
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.CLOSE_DIALOG)
                    .caption("Alert")
                    .modal(true)
                    .popupSize(popupSize)
                    .onHide(this)
                    .fire();
        }
    }

    @Override
    public void onHide(final HidePopupEvent e) {
        try {
            final CommonAlertEvent<?> event = stack.get(0);

            // Tell the caller what the user decided.
            if (event instanceof ConfirmEvent) {
                final ConfirmEvent confirmEvent = (ConfirmEvent) event;
                if (confirmEvent.getCallback() != null) {
                    confirmEvent.getCallback().onResult(e.isOk());
                }
            } else if (event instanceof AlertEvent) {
                final AlertEvent alertEvent = (AlertEvent) event;
                if (alertEvent.getCallback() != null) {
                    alertEvent.getCallback().onClose();
                }
            }

        } catch (final RuntimeException exception) {
            GWT.log(exception.getMessage());

        } finally {
            stack.remove(0);
            if (stack.size() > 0) {
                doShow();
            }
        }
    }

    public interface CommonAlertView extends View {

        void setQuestion(SafeHtml text);

        void setInfo(SafeHtml text);

        void setError(SafeHtml text);

        void setWarn(SafeHtml text);

        void setDetail(SafeHtml text);
    }
}
