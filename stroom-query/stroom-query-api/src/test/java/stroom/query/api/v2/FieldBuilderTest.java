package stroom.query.api.v2;

import stroom.query.api.v2.Format.Type;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldBuilderTest {
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

        final Field field = new Field.Builder()
                .id(name)
                .name(name)
                .expression(expression)
                .sort(new Sort.Builder()
                        .order(sortOrder)
                        .direction(sortDirection)
                        .build())
                .filter(new Filter.Builder()
                        .includes(filterIncludes)
                        .excludes(filterExcludes)
                        .build())
                .format(new Format.Builder()
                        .type(Type.NUMBER)
                        .settings(new NumberFormatSettings.Builder()
                                .decimalPlaces(numberFormatDecimalPlaces)
                                .useSeparator(numberFormatUseSeperator)
                                .build())
                        .build())
                .group(group)
                .build();

        assertThat(field.getName()).isEqualTo(name);
        assertThat(field.getExpression()).isEqualTo(expression);
        assertThat(field.getGroup()).isEqualTo(group);

        assertThat(field.getSort()).isNotNull();
        assertThat(field.getSort().getOrder()).isEqualTo(sortOrder);
        assertThat(field.getSort().getDirection()).isEqualTo(sortDirection);

        assertThat(field.getFilter()).isNotNull();
        assertThat(field.getFilter().getExcludes()).isEqualTo(filterExcludes);
        assertThat(field.getFilter().getIncludes()).isEqualTo(filterIncludes);

        assertThat(field.getFormat()).isNotNull();
        assertThat(field.getFormat().getType()).isEqualTo(Format.Type.NUMBER);
        assertThat(field.getFormat().getSettings()).isNotNull();
        assertThat(((NumberFormatSettings) field.getFormat().getSettings()).getDecimalPlaces())
                .isEqualTo(numberFormatDecimalPlaces);
        assertThat(((NumberFormatSettings) field.getFormat().getSettings()).getUseSeparator())
                .isEqualTo(numberFormatUseSeperator);
    }
}
