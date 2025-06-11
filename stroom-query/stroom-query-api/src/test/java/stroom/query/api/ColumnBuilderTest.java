package stroom.query.api;

import stroom.query.api.Format.Type;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnBuilderTest {
    @Test
    void doesBuild() {
        final String name = "someName";
        final Integer group = 57;
        final Integer sortOrder = 2;
        final Sort.SortDirection sortDirection = Sort.SortDirection.DESCENDING;
        final String expression = "someExpression";
        final String filterExcludes = "stuff to exclude **";
        final String filterIncludes = "stuff to include &&";
        final Integer numberFormatDecimalPlaces = 5;
        final Boolean numberFormatUseSeperator = true;

        final Column column = Column
                .builder()
                .id(name)
                .name(name)
                .expression(expression)
                .sort(Sort
                        .builder()
                        .order(sortOrder)
                        .direction(sortDirection)
                        .build())
                .filter(IncludeExcludeFilter
                        .builder()
                        .includes(filterIncludes)
                        .excludes(filterExcludes)
                        .build())
                .format(Format
                        .builder()
                        .type(Type.NUMBER)
                        .settings(NumberFormatSettings
                                .builder()
                                .decimalPlaces(numberFormatDecimalPlaces)
                                .useSeparator(numberFormatUseSeperator)
                                .build())
                        .build())
                .group(group)
                .build();

        assertThat(column.getName()).isEqualTo(name);
        assertThat(column.getExpression()).isEqualTo(expression);
        assertThat(column.getGroup()).isEqualTo(group);

        assertThat(column.getSort()).isNotNull();
        assertThat(column.getSort().getOrder()).isEqualTo(sortOrder);
        assertThat(column.getSort().getDirection()).isEqualTo(sortDirection);

        assertThat(column.getFilter()).isNotNull();
        assertThat(column.getFilter().getExcludes()).isEqualTo(filterExcludes);
        assertThat(column.getFilter().getIncludes()).isEqualTo(filterIncludes);

        assertThat(column.getFormat()).isNotNull();
        assertThat(column.getFormat().getType()).isEqualTo(Format.Type.NUMBER);
        assertThat(column.getFormat().getSettings()).isNotNull();
        assertThat(((NumberFormatSettings) column.getFormat().getSettings()).getDecimalPlaces())
                .isEqualTo(numberFormatDecimalPlaces);
        assertThat(((NumberFormatSettings) column.getFormat().getSettings()).getUseSeparator())
                .isEqualTo(numberFormatUseSeperator);
    }
}
