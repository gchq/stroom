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
 *
 */

package stroom.statistics.client.common.presenter;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.query.api.v2.DocRef;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.shared.common.EventStoreTimeIntervalEnum;
import stroom.statistics.shared.common.StatisticRollUpType;
import stroom.widget.tickbox.client.view.TickBox;

public class StatisticsDataSourceSettingsPresenter
        extends MyPresenterWidget<StatisticsDataSourceSettingsPresenter.StatisticsDataSourceSettingsView>
        implements HasDocumentRead<StatisticStoreEntity>, HasWrite<StatisticStoreEntity>, HasDirtyHandlers,
        StatisticsDataSourceSettingsUiHandlers {

    @Inject
    public StatisticsDataSourceSettingsPresenter(final EventBus eventBus, final StatisticsDataSourceSettingsView view,
                                                 final ClientDispatchAsync dispatcher) {
        super(eventBus, view);

        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                DirtyEvent.fire(StatisticsDataSourceSettingsPresenter.this, true);
            }
        };

        registerHandler(view.getDescription().addKeyDownHandler(keyDownHander));

        view.setUiHandlers(this);
    }

    @Override
    public void onChange() {
        DirtyEvent.fire(StatisticsDataSourceSettingsPresenter.this, true);
    }

    @Override
    public void read(final DocRef docRef, final StatisticStoreEntity statisticsDataSource) {
        if (statisticsDataSource != null) {
            getView().getDescription().setText(statisticsDataSource.getDescription());
            getView().setStatisticType(statisticsDataSource.getStatisticType());
            getView().getEnabled().setBooleanValue(statisticsDataSource.isEnabled());
            getView().setPrecision(EventStoreTimeIntervalEnum.fromColumnInterval(statisticsDataSource.getPrecision()));
            getView().setRollUpType(statisticsDataSource.getRollUpType());
        }
    }

    @Override
    public void write(final StatisticStoreEntity statisticsDataSource) {
        if (statisticsDataSource != null) {
            statisticsDataSource.setDescription(getView().getDescription().getText());
            statisticsDataSource.setStatisticType(getView().getStatisticType());
            statisticsDataSource.setEnabled(getView().getEnabled().getBooleanValue());
            statisticsDataSource.setPrecision(getView().getPrecision().columnInterval());
            statisticsDataSource.setRollUpType(getView().getRollUpType());
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface StatisticsDataSourceSettingsView
            extends View, HasUiHandlers<StatisticsDataSourceSettingsUiHandlers> {
        TextArea getDescription();

        StatisticType getStatisticType();

        void setStatisticType(StatisticType statisticType);

        StatisticRollUpType getRollUpType();

        void setRollUpType(StatisticRollUpType statisticRollUpType);

        EventStoreTimeIntervalEnum getPrecision();

        void setPrecision(EventStoreTimeIntervalEnum precision);

        TickBox getEnabled();
    }
}
