/*
 * Copyright 2026 Crown Copyright
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

package stroom.planb.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.query.api.Column;
import stroom.query.client.DateTimeSettingsFactory;
import stroom.query.client.component.AbstractQueryDataController;
import stroom.query.client.component.QueryDataTab;
import stroom.query.client.component.QueryResultTableController;
import stroom.query.client.model.ResultStoreModel;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Arrays;
import java.util.List;

/// A Plan B store's Data tab: a StroomQL query of its rows, with the columns of its state type.
public class PlanBDataController extends AbstractQueryDataController<PlanBDoc> {


    // Column definitions
    private final Column timeCol = Column.builder()
            .id("EffectiveTime").name("Effective Time").expression("EffectiveTime").build();
    private final Column keyCol = Column.builder().id("Key").name("Key").expression("Key").build();
    // Plain Value, not substring(Value, 1, 100) as this was until 2026-09-18. That expression never
    // reached the server and the column was never truncated: QueryTablePreferencesUtil matches
    // preferences to query columns by id and copies presentation only - never an expression - and
    // the ids StroomQL derives from a column alias ("value-1") never matched "Value" anyway. It read
    // as a promise the code did not keep, so it is gone rather than repaired: substring truncates
    // the data, which would leave hover showing 100 characters instead of the value.
    private final Column valueCol = Column.builder()
            .id("Value").name("Value").expression("Value").build();
    private final Column keyStartCol = Column.builder().id("KeyStart").name("Key Start").expression("KeyStart").build();
    private final Column keyEndCol = Column.builder().id("KeyEnd").name("Key End").expression("KeyEnd").build();
    private final Column startCol = Column.builder().id("Start").name("Start").expression("Start").build();
    private final Column endCol = Column.builder().id("End").name("End").expression("End").build();
    private final Column histTimeCol = Column.builder().id("Time").name("Time").expression("Time").build();
    private final Column resolutionCol = Column.builder()
            .id("Resolution").name("Resolution").expression("Resolution").build();
    private final Column minCol = Column.builder().id("Min").name("Min").expression("Min").build();
    private final Column maxCol = Column.builder().id("Max").name("Max").expression("Max").build();
    private final Column countCol = Column.builder().id("Count").name("Count").expression("Count").build();
    private final Column sumCol = Column.builder().id("Sum").name("Sum").expression("Sum").build();
    private final Column avgCol = Column.builder().id("Average").name("Average").expression("Average").build();
    private final Column traceIdCol = Column.builder().id("TraceId").name("Trace Id").expression("TraceId").build();
    private final Column parentSpanIdCol = Column.builder()
            .id("ParentSpanId")
            .name("Parent Span Id")
            .expression("ParentSpanId")
            .build();
    private final Column spanIdCol = Column.builder().id("SpanId").name("Span Id").expression("SpanId").build();
    private final Column traceStartTimeCol = Column.builder()
            .id("StartTime")
            .name("Start Time")
            .expression("StartTime")
            .build();
    private final Column traceEndTimeCol = Column.builder()
            .id("EndTime")
            .name("End Time")
            .expression("EndTime")
            .build();

    /// Made by GIN, with its content and its parts.
    ///
    /// @param eventBus                The application's event bus.
    /// @param view                    Its component.
    /// @param tableController         The table controller.
    /// @param restFactory             Makes REST requests.
    /// @param dateTimeSettingsFactory The user's date and time settings.
    /// @param resultStoreModel        The result stores.
    @Inject
    public PlanBDataController(final EventBus eventBus,
                               final QueryDataTab view,
                               final QueryResultTableController tableController,
                               final RestFactory restFactory,
                               final DateTimeSettingsFactory dateTimeSettingsFactory,
                               final ResultStoreModel resultStoreModel) {
        super(eventBus, view, tableController, restFactory, dateTimeSettingsFactory, resultStoreModel);
    }

    @Override
    protected String getDefaultQuery(final DocRef docRef, final PlanBDoc doc) {
        final StateType stateType = doc.getStateType() != null ? doc.getStateType() : StateType.TEMPORAL_STATE;
        final String prefix = "from \"" + docRef.getName() + "\" limit 100 select ";
        return switch (stateType) {
            case STATE -> prefix + "Key, Value";
            case TEMPORAL_STATE -> prefix + "EffectiveTime as \"Effective Time\", Key, Value";
            case RANGED_STATE -> prefix + "KeyStart as \"Key Start\", KeyEnd as \"Key End\", Value";
            case TEMPORAL_RANGED_STATE -> prefix
                    + "EffectiveTime as \"Effective Time\", KeyStart as \"Key Start\", KeyEnd as \"Key End\", Value";
            case SESSION -> prefix + "Start, End, Key";
            case HISTOGRAM -> prefix + "Time, Key, Resolution, Value";
            case METRIC -> prefix + "Time, Key, Resolution, Value, Min, Max, Count, Sum, Average";
            case TRACE -> prefix
                    + "StartTime as \"Start Time\", EndTime as \"End Time\", TraceId as \"Trace Id\", "
                    + "ParentSpanId as \"Parent Span Id\", SpanId as \"Span Id\"";
            default -> prefix + "EffectiveTime as \"Effective Time\", Key, Value";
        };
    }

    @Override
    protected List<Column> getPreferredColumns(final PlanBDoc doc) {
        final StateType stateType = doc.getStateType() != null ? doc.getStateType() : StateType.TEMPORAL_STATE;
        return switch (stateType) {
            case STATE -> Arrays.asList(keyCol, valueCol);
            case TEMPORAL_STATE -> Arrays.asList(timeCol, keyCol, valueCol);
            case RANGED_STATE -> Arrays.asList(keyStartCol, keyEndCol, valueCol);
            case TEMPORAL_RANGED_STATE -> Arrays.asList(timeCol, keyStartCol, keyEndCol, valueCol);
            case SESSION -> Arrays.asList(startCol, endCol, keyCol);
            case HISTOGRAM -> Arrays.asList(histTimeCol, keyCol, resolutionCol, valueCol);
            case METRIC -> Arrays.asList(
                    histTimeCol, keyCol, resolutionCol, valueCol, minCol, maxCol, countCol, sumCol, avgCol);
            case TRACE -> Arrays.asList(traceStartTimeCol, traceEndTimeCol, traceIdCol, parentSpanIdCol, spanIdCol);
            default -> Arrays.asList(timeCol, keyCol, valueCol);
        };
    }
}
