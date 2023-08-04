package stroom.query.api.v2;

import java.util.List;

public interface TableResultBuilder {

    TableResultBuilder componentId(String componentId);

    TableResultBuilder fields(List<Field> fields);

    TableResultBuilder addRow(Row row);

    /**
     * Add an error to the result.
     *
     * @param error The Error to add.
     * @return The {@link FlatResultBuilder}, enabling method chaining
     */
    TableResultBuilder errors(List<String> errors);

    TableResultBuilder resultRange(OffsetRange resultRange);

    TableResultBuilder totalResults(Long totalResults);

    TableResult build();
}
