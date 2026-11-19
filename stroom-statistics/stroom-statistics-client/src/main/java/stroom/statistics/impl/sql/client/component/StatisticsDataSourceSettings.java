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

package stroom.statistics.impl.sql.client.component;

import stroom.statistics.impl.sql.shared.EventStoreTimeIntervalEnum;
import stroom.statistics.impl.sql.shared.StatisticRollUpType;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.widget.component.client.Component;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// A statistic store's Settings tab: its statistic type, precision, roll-up type and whether it is
/// enabled.
public class StatisticsDataSourceSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<StatisticType> statisticType;
    @UiField
    SelectionBox<EventStoreTimeIntervalEnum> precision;
    @UiField
    SelectionBox<StatisticRollUpType> rollUpType;
    @UiField
    TickBox enabled;

    private Runnable onChange = () -> {
    };

    /// Makes an empty tab.
    public StatisticsDataSourceSettings() {
        initWidget(BINDER.createAndBindUi(this));

        statisticType.addItem(StatisticType.COUNT);
        statisticType.addItem(StatisticType.VALUE);

        rollUpType.addItem(StatisticRollUpType.NONE);
        rollUpType.addItem(StatisticRollUpType.ALL);
        rollUpType.addItem(StatisticRollUpType.CUSTOM);

        precision.addItem(EventStoreTimeIntervalEnum.SECOND);
        precision.addItem(EventStoreTimeIntervalEnum.MINUTE);
        precision.addItem(EventStoreTimeIntervalEnum.HOUR);
        precision.addItem(EventStoreTimeIntervalEnum.DAY);

        statisticType.addValueChangeHandler(event -> onChange.run());
        rollUpType.addValueChangeHandler(event -> onChange.run());
        precision.addValueChangeHandler(event -> onChange.run());
        enabled.addValueChangeHandler(event -> onChange.run());
    }

    /// Shows a store's settings.
    ///
    /// @param doc The store.
    public void read(final StatisticStoreDoc doc) {
        statisticType.setValue(doc.getStatisticType());
        enabled.setValue(doc.isEnabled());
        precision.setValue(EventStoreTimeIntervalEnum.fromColumnInterval(doc.getPrecision()));
        rollUpType.setValue(doc.getRollUpType());
    }

    /// Writes the settings shown into a copy of a store.
    ///
    /// @param doc The store.
    /// @return A copy of it with the settings shown.
    public StatisticStoreDoc write(final StatisticStoreDoc doc) {
        return doc
                .copy()
                .statisticType(statisticType.getValue())
                .enabled(enabled.getValue())
                .precision(precision.getValue().columnInterval())
                .rollUpType(rollUpType.getValue())
                .build();
    }

    /// @param onChange What to run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        // Read only, not disabled: the settings can be read and copied but not changed
        statisticType.setReadOnly(readOnly);
        precision.setReadOnly(readOnly);
        rollUpType.setReadOnly(readOnly);
        enabled.setReadOnly(readOnly);
    }

    interface Binder extends UiBinder<Widget, StatisticsDataSourceSettings> {

    }
}
