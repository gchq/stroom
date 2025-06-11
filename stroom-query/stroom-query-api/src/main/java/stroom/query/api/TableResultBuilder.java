package stroom.query.api;

import java.util.List;

public interface TableResultBuilder {

    TableResultBuilder componentId(String componentId);

    TableResultBuilder columns(List<Column> columns);

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
