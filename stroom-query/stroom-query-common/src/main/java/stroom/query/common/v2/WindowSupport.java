package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValDate;
import stroom.dashboard.expression.v1.Values;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.HoppingWindow;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;
import stroom.query.api.v2.TableSettings;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;

public class WindowSupport {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(WindowSupport.class);

    private TemporalAmount windowSize;
    private List<TemporalAmount> offsets;
    //    private int timeFieldIndex = -1;
    private int windowTimeFieldPos;
    private TableSettings tableSettings;

    public WindowSupport(final TableSettings tableSettings) {
        this.tableSettings = tableSettings;
        if (tableSettings != null && tableSettings.getWindow() != null) {
            if (tableSettings.getWindow() instanceof HoppingWindow) {
                final HoppingWindow hoppingWindow = (HoppingWindow) tableSettings.getWindow();
                try {
                    Period window = Period.parse(hoppingWindow.getWindowSize());
                    Period advance = Period.parse(hoppingWindow.getAdvanceSize());
                    windowSize = window;

                    offsets = new ArrayList<>();
                    Period offset = Period.ZERO;
                    while (offset.getYears() <= window.getYears() &&
                            offset.getMonths() <= window.getMonths() &&
                            offset.getDays() <= window.getDays()) {
                        offsets.add(offset);
                        offset = offset.plus(advance);
                    }
                } catch (final RuntimeException e) {
                    Duration window = StroomDuration.parse(hoppingWindow.getWindowSize()).getDuration();
                    Duration advance = StroomDuration.parse(hoppingWindow.getAdvanceSize()).getDuration();
                    windowSize = window;

                    Duration offset = Duration.ZERO;
                    while (offset.compareTo(window) < 0) {
                        offsets.add(offset);
                        offset = offset.plus(advance);
                    }
                }
                // Add all the additional fields we want for time windows.
                final List<Field> newFields = new ArrayList<>();
                newFields.add(Field.builder()
                        .id(hoppingWindow.getTimeField())
                        .name(hoppingWindow.getTimeField())
                        .expression("${" + hoppingWindow.getTimeField() + "}")
                        .group(0)
                        .sort(Sort.builder().order(0).direction(SortDirection.ASCENDING).build())
                        .visible(true)
                        .build());
                newFields.addAll(tableSettings.getFields());

                for (int i = 0; i < offsets.size(); i++) {
                    newFields.add(Field.builder()
                            .id("period-" + i)
                            .name("period-" + i)
                            .expression("countPrevious(" + i + ")")
                            .visible(true)
                            .build());
                }

                this.tableSettings = tableSettings.copy().fields(newFields).build();
                windowTimeFieldPos = 0;
            }
        }
    }


    public Values addWindow(final Values values,
                            final TemporalAmount offset) {
        final Val val = values.get(windowTimeFieldPos);
        final Val adjusted = adjustWithOffset(val, offset);
        Val[] orig = values.toUnsafeArray();
        Val[] arr = new Val[orig.length];
        System.arraycopy(orig, 0, arr, 0, orig.length);
        arr[windowTimeFieldPos] = adjusted;
        return Values.of(arr);
    }

    private Val adjustWithOffset(final Val val, final TemporalAmount offset) {
        try {
            final Instant instant = Instant.ofEpochMilli(val.toLong());
            if (windowSize instanceof Period) {
                final LocalDateTime dateTime =
                        LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                final LocalDateTime rounded =
                        DateUtil.roundDown(dateTime, (Period) windowSize);
                final LocalDateTime adjusted = rounded.plus(offset);
                return ValDate.create(adjusted.toInstant(ZoneOffset.UTC).toEpochMilli());
            } else {
                final Instant rounded = DateUtil.roundDown(instant, (Duration) windowSize);
                final Instant adjusted = rounded.plus(offset);
                return ValDate.create(adjusted.toEpochMilli());
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
        return val;
    }

    public TableSettings getTableSettings() {
        return tableSettings;
    }

    public List<TemporalAmount> getOffsets() {
        return offsets;
    }
}
