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

package stroom.dashboard.client.query;

import stroom.activity.client.CurrentActivity;
import stroom.activity.shared.Activity;
import stroom.alert.client.event.AlertEvent;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.QueryConfig;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class QueryInfoPresenter
        extends MyPresenterWidget<QueryInfoPresenter.QueryInfoView> {

    private final UiConfigCache uiConfigCache;
    private final CurrentActivity currentActivity;

    @Inject
    public QueryInfoPresenter(final EventBus eventBus,
                              final QueryInfoView view,
                              final UiConfigCache uiConfigCache,
                              final CurrentActivity currentActivity) {
        super(eventBus, view);
        this.uiConfigCache = uiConfigCache;
        this.currentActivity = currentActivity;
    }

    private boolean isRequired(final Activity activity) {
        boolean required = true;
        if (activity != null && activity.getDetails() != null) {
            final String value = activity.getDetails().value("requireQueryInfo");
            if (value != null && value.equalsIgnoreCase("false")) {
                required = false;
            }
        }
        return required;
    }

    public void show(final String queryInfo,
                     final Consumer<State> consumer,
                     final TaskMonitorFactory taskMonitorFactory) {
        currentActivity.getActivity(activity -> {
            final boolean required = isRequired(activity);
            uiConfigCache.get(uiConfig -> {
                if (uiConfig != null) {
                    final QueryConfig queryConfig = uiConfig.getQuery();
                    final boolean queryInfoPopupEnabled = queryConfig.getInfoPopup().isEnabled();
                    final String queryInfoPopupTitle = queryConfig.getInfoPopup().getTitle();
                    final String queryInfoPopupValidationRegex = queryConfig.getInfoPopup().getValidationRegex();

                    if (queryInfoPopupEnabled && required) {
                        getView().setQueryInfo(queryInfo);
                        final PopupSize popupSize = PopupSize.resizable(640, 480);
                        ShowPopupEvent.builder(this)
                                .popupType(PopupType.OK_CANCEL_DIALOG)
                                .popupSize(popupSize)
                                .caption(queryInfoPopupTitle)
                                .onShow(e -> getView().focus())
                                .onHideRequest(e -> {
                                    if (e.isOk()) {
                                        boolean valid = true;
                                        if (queryInfoPopupValidationRegex != null
                                                && !queryInfoPopupValidationRegex.isEmpty()) {
                                            valid = false;
                                            try {
                                                valid = getView().getQueryInfo().matches(queryInfoPopupValidationRegex);
                                            } catch (final RuntimeException ex) {
                                                AlertEvent
                                                        .fireErrorFromException(QueryInfoPresenter.this, ex, e::reset);
                                            }
                                        }

                                        if (valid) {
                                            e.hide();
                                            consumer.accept(new State(getView().getQueryInfo(), true));
                                        } else {
                                            AlertEvent.fireWarn(QueryInfoPresenter.this,
                                                    "The text entered is not valid",
                                                    e::reset);
                                        }
                                    } else {
                                        e.hide();
                                        consumer.accept(new State(getView().getQueryInfo(), false));
                                    }
                                })
                                .fire();
                    } else {
                        consumer.accept(new State(null, true));
                    }
                }
            }, taskMonitorFactory);
        }, taskMonitorFactory);
    }

    public interface QueryInfoView extends View, Focus {

        String getQueryInfo();

        void setQueryInfo(String queryInfo);
    }

    public class State {

        private final String queryInfo;
        private final boolean ok;

        State(final String queryInfo, final boolean ok) {
            this.queryInfo = queryInfo;
            this.ok = ok;
        }

        public String getQueryInfo() {
            return queryInfo;
        }

        public boolean isOk() {
            return ok;
        }
    }
}
