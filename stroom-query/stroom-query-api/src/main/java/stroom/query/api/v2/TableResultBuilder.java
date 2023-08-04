package stroom.query.api.v2;

import java.util.List;

public interface TableResultBuilder extends ResultBuilder<TableResultBuilder> {

    TableResultBuilder componentId(String componentId);

    TableResultBuilder errors(List<String> errors);

    TableResultBuilder fields(List<Field> fields);

    TableResultBuilder addRow(Row row);

    TableResultBuilder resultRange(OffsetRange resultRange);

    TableResultBuilder totalResults(Long totalResults);

    TableResult build();
}
