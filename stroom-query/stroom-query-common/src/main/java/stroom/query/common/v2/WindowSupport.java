package stroom.query.common.v2;

import stroom.query.api.v2.Column;
import stroom.query.api.v2.HoppingWindow;
import stroom.query.api.v2.ParamSubstituteUtil;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;
import stroom.query.api.v2.TableSettings;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WindowSupport {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(WindowSupport.class);
    private SimpleDuration window;
    private SimpleDuration advance;
    private List<SimpleDuration> offsets;
    private TableSettings tableSettings;

    public WindowSupport(final TableSettings tableSettings) {
        this.tableSettings = tableSettings;
        if (tableSettings != null && tableSettings.getWindow() != null) {
            if (tableSettings.getWindow() instanceof HoppingWindow) {
                final HoppingWindow hoppingWindow = (HoppingWindow) tableSettings.getWindow();
                try {
                    window = SimpleDurationUtil.parse(hoppingWindow.getWindowSize());
                    advance = SimpleDurationUtil.parse(hoppingWindow.getAdvanceSize());

                    offsets = new ArrayList<>();
                    SimpleDuration offset = SimpleDuration.ZERO;

                    LocalDateTime reference = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
                    LocalDateTime maximum = SimpleDurationUtil.plus(reference, window);
                    LocalDateTime added = reference;

                    while (added.isBefore(maximum) || added.equals(maximum)) {
                        offsets.add(offset);
                        if (offset.getTime() == 0) {
                            offset = advance;
                        } else {
                            offset = offset.copy().time(offset.getTime() + advance.getTime()).build();
                        }
                        added = SimpleDurationUtil.plus(reference, offset);
                    }
                } catch (final RuntimeException | ParseException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }

                // Add all the additional fields we want for time windows.
                final List<Column> columns = new ArrayList<>();
                if (tableSettings.getColumns() != null) {
                    columns.addAll(tableSettings.getColumns());
                }

                final Map<String, Column> columnMap = columns
                        .stream()
                        .collect(Collectors.toMap(Column::getId, Function.identity()));

                Column timeColumn = columnMap.get(hoppingWindow.getTimeField());
                if (timeColumn != null) {
                    final int index = columns.indexOf(timeColumn);
                    columns.set(index, timeColumn
                            .copy()
                            .expression(ParamSubstituteUtil.makeParam(hoppingWindow.getTimeField()))
                            .group(0)
                            .build());
                } else {
                    columns.add(Column.builder()
                            .id(hoppingWindow.getTimeField())
                            .name(hoppingWindow.getTimeField())
                            .expression(ParamSubstituteUtil.makeParam(hoppingWindow.getTimeField()))
                            .group(0)
                            .sort(Sort.builder().order(0).direction(SortDirection.ASCENDING).build())
                            .visible(true)
                            .build());
                }

                for (int i = 0; i < offsets.size(); i++) {
                    final String fieldId = "period-" + i;
                    final Column column = columnMap.get(fieldId);
                    if (column != null) {
                        final int index = columns.indexOf(column);
                        columns.set(index, column
                                .copy()
                                .expression("countPrevious(" + i + ")")
                                .build());
                    } else {
                        columns.add(Column.builder()
                                .id(fieldId)
                                .name(fieldId)
                                .expression("countPrevious(" + i + ")")
                                .visible(true)
                                .build());
                    }
                }

                this.tableSettings = tableSettings.copy().columns(columns).build();
            }
        }
    }

    public Val[] addWindow(final FieldIndex fieldIndex,
                           final Val[] values,
                           final SimpleDuration offset) {
        final int windowTimeFieldPos = fieldIndex.getWindowTimeFieldIndex();
        final Val val = values[windowTimeFieldPos];
        final Val adjusted = adjustWithOffset(val, offset);
        final Val[] arr = new Val[values.length];
        System.arraycopy(values, 0, arr, 0, values.length);
        arr[windowTimeFieldPos] = adjusted;
        return arr;
    }

    private Val adjustWithOffset(final Val val, final SimpleDuration offset) {
        try {
            final Instant instant = Instant.ofEpochMilli(val.toLong());
            final LocalDateTime dateTime =
                    LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
            final LocalDateTime baseline =
                    SimpleDurationUtil.roundDown(dateTime, window);
            // Add advance until we exceed baseline.
            LocalDateTime adjusted = baseline;
            LocalDateTime rounded = baseline;
            while (adjusted.isBefore(dateTime)) {
                rounded = adjusted;
                adjusted = SimpleDurationUtil.plus(adjusted, window);
            }

            final LocalDateTime advanced = SimpleDurationUtil.plus(rounded, offset);
            return ValDate.create(advanced.toInstant(ZoneOffset.UTC).toEpochMilli());
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
        return val;
    }

    public TableSettings getTableSettings() {
        return tableSettings;
    }

    public List<SimpleDuration> getOffsets() {
        return offsets;
    }

    public static List<Column> modifyColumns(final TableSettings tableSettings) {
        if (tableSettings == null || tableSettings.getColumns() == null) {
            return Collections.emptyList();
        }

        final WindowSupport windowSupport = new WindowSupport(tableSettings);
        final TableSettings modifiedTableSettings = windowSupport.getTableSettings();
        return modifiedTableSettings.getColumns();
    }
}
