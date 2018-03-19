package stroom.util.test.data;

import java.util.List;
import java.util.stream.Stream;

public interface DataWriter {
    Stream<String> mapRecords(final List<Field> fieldDefinitions,
                              final Stream<Record> recordStream);
}
