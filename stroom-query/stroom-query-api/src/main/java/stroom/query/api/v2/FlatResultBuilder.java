package stroom.query.api.v2;

import stroom.query.api.v2.FlatResult.FlatResultBuilderImpl;

import java.util.List;

public interface FlatResultBuilder extends ResultBuilder<FlatResultBuilder> {

    /**
     * Add headings to our data
     *
     * @param structure the fields which act as headings for our data
     * @return The {@link FlatResultBuilderImpl}, enabling method chaining
     */
    FlatResultBuilder structure(List<Field> structure);

    /**
     * @param values A 'row' of data points to add to our values
     * @return The {@link FlatResultBuilderImpl}, enabling method chaining
     */
    FlatResultBuilder addValues(List<Object> values);

    /**
     * Fix the reported size of the result set.
     *
     * @param value The size to use
     * @return The {@link FlatResultBuilderImpl}, enabling method chaining
     */
    FlatResultBuilder totalResults(Long totalResults);

    FlatResult build();
}
