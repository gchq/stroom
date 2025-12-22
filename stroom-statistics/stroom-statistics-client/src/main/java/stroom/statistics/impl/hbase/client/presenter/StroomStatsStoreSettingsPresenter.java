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

package stroom.statistics.impl.hbase.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.statistics.impl.hbase.client.presenter.StroomStatsStoreSettingsPresenter.StroomStatsStoreSettingsView;
import stroom.statistics.impl.hbase.shared.EventStoreTimeIntervalEnum;
import stroom.statistics.impl.hbase.shared.StatisticRollUpType;
import stroom.statistics.impl.hbase.shared.StatisticType;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class StroomStatsStoreSettingsPresenter
        extends DocumentEditPresenter<StroomStatsStoreSettingsView, StroomStatsStoreDoc>
        implements
        StroomStatsStoreSettingsUiHandlers {

    @Inject
    public StroomStatsStoreSettingsPresenter(
            final EventBus eventBus,
            final StroomStatsStoreSettingsPresenter.StroomStatsStoreSettingsView view) {

        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    public void onChange() {
        DirtyEvent.fire(StroomStatsStoreSettingsPresenter.this, true);
    }

    @Override
    protected void onRead(final DocRef docRef, final StroomStatsStoreDoc document, final boolean readOnly) {
        getView().onReadOnly(readOnly);
        if (document != null) {
            getView().setStatisticType(document.getStatisticType());
            getView().getEnabled().setValue(document.isEnabled());
            getView().setPrecision(document.getPrecision());
            getView().setRollUpType(document.getRollUpType());
        }
    }

    @Override
    protected StroomStatsStoreDoc onWrite(final StroomStatsStoreDoc document) {
        if (document != null) {
            document.setStatisticType(getView().getStatisticType());
            document.setEnabled(getView().getEnabled().getValue());
            document.setPrecision(getView().getPrecision());
            document.setRollUpType(getView().getRollUpType());
        }
        return document;
    }

    public interface StroomStatsStoreSettingsView
            extends View, HasUiHandlers<StroomStatsStoreSettingsUiHandlers>, ReadOnlyChangeHandler {

        StatisticType getStatisticType();

        void setStatisticType(StatisticType statisticType);

        StatisticRollUpType getRollUpType();

        void setRollUpType(StatisticRollUpType statisticRollUpType);

        EventStoreTimeIntervalEnum getPrecision();

        void setPrecision(EventStoreTimeIntervalEnum precision);

        CustomCheckBox getEnabled();
    }
}
