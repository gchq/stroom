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

package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.HoppingWindow;
import stroom.query.api.ParamUtil;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.Window;
import stroom.query.language.functions.Expression;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.ExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ParamFactory;
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
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CompiledWindow {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CompiledWindow.class);

    public static final String FIELD_PREFIX = "period";

    private final String timeField;
    private final SimpleDuration windowSize;
    private final List<SimpleDuration> offsets;
    private final String function;

    public CompiledWindow(final String timeField,
                          final SimpleDuration windowSize,
                          final List<SimpleDuration> offsets,
                          final String function) {
        this.timeField = timeField;
        this.windowSize = windowSize;
        this.offsets = offsets;
        this.function = function;
    }

    public static CompiledWindow create(final Window window) {
        SimpleDuration windowSize = SimpleDuration.ZERO;
        List<SimpleDuration> offsets = Collections.emptyList();
        String timeField = "EventTime";
        String function = "count()";

        if (window instanceof final HoppingWindow hoppingWindow) {
            function = Objects.requireNonNullElse(hoppingWindow.getFunction(), "count()");

            try {
                timeField = hoppingWindow.getTimeField();
                windowSize = SimpleDurationUtil.parse(hoppingWindow.getWindowSize());
                final SimpleDuration advance = SimpleDurationUtil.parse(hoppingWindow.getAdvanceSize());

                offsets = new ArrayList<>();
                SimpleDuration offset = SimpleDuration.ZERO;

                final LocalDateTime reference = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
                final LocalDateTime maximum = SimpleDurationUtil.plus(reference, windowSize);
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
        }

        return new CompiledWindow(timeField, windowSize, offsets, function);
    }

    public void addWindowFields(final ExpressionContext expressionContext,
                                final FieldIndex fieldIndex,
                                final Map<String, Expression> expressionMap) {
        // Ensure time field is present.
        fieldIndex.create(timeField);

        for (int i = 0; i < offsets.size(); i++) {
            final String fieldId = FIELD_PREFIX + i;
            final String expression = "period(" + i + ", " + function + ")";
            final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory(expressionMap));
            try {
                final Expression exp = expressionParser.parse(expressionContext, fieldIndex, expression);
                expressionMap.put(fieldId, exp);
            } catch (final ParseException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }

    public List<Column> addPeriodColumns(final List<Column> orig,
                                         final Map<String, Expression> expressionMap) {
        // Add all the additional fields we want for time windows.
        final List<Column> columns = new ArrayList<>();
        if (orig != null) {
            columns.addAll(orig);
        }

        final Map<String, Column> columnMap = columns
                .stream()
                .collect(Collectors.toMap(Column::getId, Function.identity()));

        // Fix or insert time column.
        if (timeField != null) {
            final Column timeColumn = columnMap.get(timeField);
            if (timeColumn != null) {
                final int index = columns.indexOf(timeColumn);
                columns.set(index, timeColumn
                        .copy()
                        .expression(ParamUtil.create(timeField))
                        .group(0)
                        .build());
            } else {
                columns.add(Column.builder()
                        .id(timeField)
                        .name(timeField)
                        .expression(ParamUtil.create(timeField))
                        .group(0)
                        .sort(Sort.builder().order(0).direction(SortDirection.ASCENDING).build())
                        .visible(true)
                        .build());
            }
        }

        // Add period columns.
        for (int i = 0; i < offsets.size(); i++) {
            final String fieldId = FIELD_PREFIX + i;
            final Expression expression = expressionMap.get(fieldId);
            final String expressionString = expression.toString();
            final Column column = columnMap.get(fieldId);
            if (column != null) {
                final int index = columns.indexOf(column);
                columns.set(index, column
                        .copy()
                        .expression(expressionString)
                        .build());
            } else {
                columns.add(Column.builder()
                        .id(fieldId)
                        .name(fieldId)
                        .expression(expressionString)
                        .visible(true)
                        .build());
            }
        }

        return columns;
    }

    public WindowProcessor createWindowProcessor(final FieldIndex fieldIndex) {
        if (offsets == null || offsets.isEmpty()) {
            return new NoOpWindowProcessor();
        }

        final int windowTimeFieldPos = fieldIndex.create(timeField);
        return new OffsetWindowProcessor(windowSize, offsets, windowTimeFieldPos);
    }

    public interface WindowProcessor {

        void process(Val[] values,
                     BiConsumer<Val[], Integer> consumer);
    }

    private static class NoOpWindowProcessor implements WindowProcessor {

        @Override
        public void process(final Val[] values,
                            final BiConsumer<Val[], Integer> consumer) {
            consumer.accept(values, -1);
        }
    }

    private static class OffsetWindowProcessor implements WindowProcessor {

        private final SimpleDuration windowSize;
        private final List<SimpleDuration> offsets;
        private final int windowTimeFieldPos;

        public OffsetWindowProcessor(final SimpleDuration windowSize,
                                     final List<SimpleDuration> offsets,
                                     final int windowTimeFieldPos) {
            this.windowSize = windowSize;
            this.offsets = offsets;
            this.windowTimeFieldPos = windowTimeFieldPos;
        }

        @Override
        public void process(final Val[] values,
                            final BiConsumer<Val[], Integer> consumer) {
            int iteration = 0;
            for (final SimpleDuration offset : offsets) {
                final Val[] modifiedValues = addWindow(values, offset);
                consumer.accept(modifiedValues, iteration);
                iteration++;
            }
        }

        private Val[] addWindow(final Val[] values,
                                final SimpleDuration offset) {
            final Val val = values[windowTimeFieldPos];
            if (val == null) {
                throw new RuntimeException("Unable to find time field for window offsets");
            }
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
                        SimpleDurationUtil.roundDown(dateTime, windowSize);
                // Add advance until we exceed baseline.
                LocalDateTime adjusted = baseline;
                LocalDateTime rounded = baseline;
                while (adjusted.isBefore(dateTime)) {
                    rounded = adjusted;
                    adjusted = SimpleDurationUtil.plus(adjusted, windowSize);
                }

                final LocalDateTime advanced = SimpleDurationUtil.plus(rounded, offset);
                return ValDate.create(advanced.toInstant(ZoneOffset.UTC).toEpochMilli());
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
            return val;
        }
    }
}
