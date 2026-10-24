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

package stroom.statistics.impl.sql.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.statistics.impl.sql.client.presenter.StatisticsDataSourceSettingsPresenter.StatisticsDataSourceSettingsView;
import stroom.statistics.impl.sql.shared.EventStoreTimeIntervalEnum;
import stroom.statistics.impl.sql.shared.StatisticRollUpType;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;
import stroom.widget.tickbox.client.view.TickBox;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class StatisticsDataSourceSettingsPresenter
        extends DocPresenter<StatisticsDataSourceSettingsView, StatisticStoreDoc>
        implements StatisticsDataSourceSettingsUiHandlers {

    @Inject
    public StatisticsDataSourceSettingsPresenter(final EventBus eventBus, final StatisticsDataSourceSettingsView view) {
        super(eventBus, view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, getView()::onReadOnly);
        view.setUiHandlers(this);
    }

    @Override
    protected void onRead(final DocRef docRef, final StatisticStoreDoc document) {
        getView().setStatisticType(document.getStatisticType());
        getView().getEnabled().setValue(document.isEnabled());
        getView().setPrecision(EventStoreTimeIntervalEnum.fromColumnInterval(document.getPrecision()));
        getView().setRollUpType(document.getRollUpType());
    }

    @Override
    protected StatisticStoreDoc onWrite(final StatisticStoreDoc document) {
        return document
                .copy()
                .statisticType(getView().getStatisticType())
                .enabled(getView().getEnabled().getValue())
                .precision(getView().getPrecision().columnInterval())
                .rollUpType(getView().getRollUpType())
                .build();
    }

    public interface StatisticsDataSourceSettingsView
            extends View, HasUiHandlers<StatisticsDataSourceSettingsUiHandlers> {

        /// Shows whether the user may change what this shows.
        ///
        /// @param readOnly Whether it may not be changed.
        void onReadOnly(boolean readOnly);

        StatisticType getStatisticType();

        void setStatisticType(StatisticType statisticType);

        StatisticRollUpType getRollUpType();

        void setRollUpType(StatisticRollUpType statisticRollUpType);

        EventStoreTimeIntervalEnum getPrecision();

        void setPrecision(EventStoreTimeIntervalEnum precision);

        TickBox getEnabled();
    }
}
