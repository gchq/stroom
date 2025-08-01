package stroom.query.api;

import stroom.util.shared.ErrorMessage;

import java.util.List;

public interface FlatResultBuilder {

    FlatResultBuilder componentId(String componentId);

    /**
     * Add headings to our data
     *
     * @param structure the columns which act as headings for our data
     * @return The {@link FlatResultBuilder}, enabling method chaining
     */
    FlatResultBuilder structure(List<Column> structure);

    /**
     * @param values A 'row' of data points to add to our values
     * @return The {@link FlatResultBuilder}, enabling method chaining
     */
    FlatResultBuilder addValues(List<Object> values);

    FlatResultBuilder errorMessages(final List<ErrorMessage> errorMessages);

    /**
     * Fix the reported size of the result set.
     *
     * @param totalResults The size to use
     * @return The {@link FlatResultBuilder}, enabling method chaining
     */
    FlatResultBuilder totalResults(Long totalResults);

    FlatResult build();
}
