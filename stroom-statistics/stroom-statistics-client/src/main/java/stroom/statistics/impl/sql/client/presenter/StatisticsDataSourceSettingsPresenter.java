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

package stroom.statistics.impl.sql.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.statistics.impl.sql.client.presenter.StatisticsDataSourceSettingsPresenter.StatisticsDataSourceSettingsView;
import stroom.statistics.impl.sql.shared.EventStoreTimeIntervalEnum;
import stroom.statistics.impl.sql.shared.StatisticRollUpType;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class StatisticsDataSourceSettingsPresenter
        extends DocumentEditPresenter<StatisticsDataSourceSettingsView, StatisticStoreDoc>
        implements StatisticsDataSourceSettingsUiHandlers {

    @Inject
    public StatisticsDataSourceSettingsPresenter(final EventBus eventBus, final StatisticsDataSourceSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    public void onChange() {
        DirtyEvent.fire(StatisticsDataSourceSettingsPresenter.this, true);
    }

    @Override
    protected void onRead(final DocRef docRef, final StatisticStoreDoc document, final boolean readOnly) {
        getView().onReadOnly(readOnly);
        if (document != null) {
            getView().setStatisticType(document.getStatisticType());
            getView().getEnabled().setValue(document.isEnabled());
            getView().setPrecision(EventStoreTimeIntervalEnum.fromColumnInterval(document.getPrecision()));
            getView().setRollUpType(document.getRollUpType());
        }
    }

    @Override
    protected StatisticStoreDoc onWrite(final StatisticStoreDoc document) {
        if (document != null) {
            document.setStatisticType(getView().getStatisticType());
            document.setEnabled(getView().getEnabled().getValue());
            document.setPrecision(getView().getPrecision().columnInterval());
            document.setRollUpType(getView().getRollUpType());
        }
        return document;
    }

    public interface StatisticsDataSourceSettingsView
            extends View, HasUiHandlers<StatisticsDataSourceSettingsUiHandlers>, ReadOnlyChangeHandler {

        StatisticType getStatisticType();

        void setStatisticType(StatisticType statisticType);

        StatisticRollUpType getRollUpType();

        void setRollUpType(StatisticRollUpType statisticRollUpType);

        EventStoreTimeIntervalEnum getPrecision();

        void setPrecision(EventStoreTimeIntervalEnum precision);

        CustomCheckBox getEnabled();
    }
}
