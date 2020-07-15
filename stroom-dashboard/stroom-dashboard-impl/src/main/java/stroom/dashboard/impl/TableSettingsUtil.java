package stroom.dashboard.impl;

import stroom.dashboard.shared.DateTimeFormatSettings;
import stroom.dashboard.shared.Filter;
import stroom.dashboard.shared.Format;
import stroom.dashboard.shared.FormatSettings;
import stroom.dashboard.shared.NumberFormatSettings;
import stroom.dashboard.shared.Sort;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TimeZone;
import stroom.query.api.v2.DateTimeFormat;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.NumberFormat;
import stroom.query.api.v2.TableSettings;
import stroom.util.shared.OffsetRange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class TableSettingsUtil {
    public static TableSettings mapTableSettings(final TableComponentSettings tableComponentSettings) {
        if (tableComponentSettings == null) {
            return null;
        }

        final TableSettings tableSettings = new TableSettings.Builder()
                .queryId(tableComponentSettings.getQueryId())
                .addFields(mapFields(tableComponentSettings.getFields()))
                .extractValues(tableComponentSettings.extractValues())
                .extractionPipeline(tableComponentSettings.getExtractionPipeline())
                .addMaxResults(mapIntArray(tableComponentSettings.getMaxResults()))
                .showDetail(tableComponentSettings.getShowDetail())
                .build();

        return tableSettings;
    }

    public static List<Field> mapFields(final List<stroom.dashboard.shared.Field> fields) {
        if (fields == null || fields.size() == 0) {
            return Collections.emptyList();
        }

        final List<stroom.query.api.v2.Field> list = new ArrayList<>(fields.size());
        for (final stroom.dashboard.shared.Field field : fields) {
            final stroom.query.api.v2.Field.Builder builder = new stroom.query.api.v2.Field.Builder()
                    .id(field.getId())
                    .name(field.getName())
                    .expression(field.getExpression())
                    .sort(mapSort(field.getSort()))
                    .filter(mapFilter(field.getFilter()))
                    .format(mapFormat(field.getFormat()))
                    .group(field.getGroup());

            list.add(builder.build());
        }

        return list;
    }

    private static List<Integer> mapIntArray(final int[] arr) {
        if (arr == null || arr.length == 0) {
            return null;
        }

        final List<Integer> copy = new ArrayList<>(arr.length);
        for (int i = 0; i < arr.length; i++) {
            copy.add(arr[i]);
        }

        return copy;
    }

    public static <T> List<T> mapCollection(final Class<T> clazz, final Collection<T> collection) {
        if (collection == null || collection.size() == 0) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<T> copy = new ArrayList<>(collection.size());
        int i = 0;
        for (final T t : collection) {
            copy.add(t);
        }

        return copy;
    }

    public static stroom.query.api.v2.OffsetRange mapOffsetRange(final OffsetRange<Integer> offsetRange) {
        if (offsetRange == null) {
            return null;
        }

        return new stroom.query.api.v2.OffsetRange(offsetRange.getOffset(), offsetRange.getLength());
    }

    private static stroom.query.api.v2.Sort mapSort(final Sort sort) {
        if (sort == null) {
            return null;
        }

        stroom.query.api.v2.Sort.SortDirection sortDirection = null;
        if (sort.getDirection() != null) {
            sortDirection = stroom.query.api.v2.Sort.SortDirection.valueOf(sort.getDirection().name());
        }

        return new stroom.query.api.v2.Sort(sort.getOrder(), sortDirection);
    }

    private static stroom.query.api.v2.Filter mapFilter(final Filter filter) {
        if (filter == null) {
            return null;
        }

        return new stroom.query.api.v2.Filter(filter.getIncludes(), filter.getExcludes());
    }

    private static stroom.query.api.v2.Format mapFormat(final Format format) {
        if (format == null) {
            return null;
        }

        stroom.query.api.v2.Format.Type type = null;

        if (format.getType() != null) {
            type = stroom.query.api.v2.Format.Type.valueOf(format.getType().name());
        }

        return new stroom.query.api.v2.Format(type, mapNumberFormat(format.getSettings()), mapDateTimeFormat(format.getSettings()));
    }

    private static stroom.query.api.v2.NumberFormat mapNumberFormat(final FormatSettings formatSettings) {
        if (formatSettings == null || !(formatSettings instanceof NumberFormatSettings)) {
            return null;
        }

        final NumberFormatSettings numberFormatSettings = (NumberFormatSettings) formatSettings;

        return new NumberFormat(numberFormatSettings.getDecimalPlaces(), numberFormatSettings.getUseSeparator());
    }

    private static stroom.query.api.v2.DateTimeFormat mapDateTimeFormat(final FormatSettings formatSettings) {
        if (formatSettings == null || !(formatSettings instanceof DateTimeFormatSettings)) {
            return null;
        }

        final DateTimeFormatSettings dateTimeFormatSettings = (DateTimeFormatSettings) formatSettings;

        return new DateTimeFormat(dateTimeFormatSettings.getPattern(), mapTimeZone(dateTimeFormatSettings.getTimeZone()));
    }

    private static stroom.query.api.v2.TimeZone mapTimeZone(final TimeZone timeZone) {
        if (timeZone == null) {
            return null;
        }

        return new stroom.query.api.v2.TimeZone(stroom.query.api.v2.TimeZone.Use.valueOf(timeZone.getUse().name()), timeZone.getId(), timeZone.getOffsetHours(), timeZone.getOffsetMinutes());
    }

    private TableSettingsUtil(){}
}
