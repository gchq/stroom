package stroom.query.api;

import java.util.List;

public interface TableResultBuilder {

    TableResultBuilder componentId(String componentId);

    TableResultBuilder columns(List<Column> columns);

    TableResultBuilder addRow(Row row);

    TableResultBuilder errors(List<String> errors);

    TableResultBuilder errorMessages(List<ErrorMessage> errorMessages);

    TableResultBuilder resultRange(OffsetRange resultRange);

    TableResultBuilder totalResults(Long totalResults);

    TableResult build();
}
