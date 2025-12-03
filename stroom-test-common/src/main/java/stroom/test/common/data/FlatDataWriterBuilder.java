/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.test.common.data;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlatDataWriterBuilder {
    private boolean isHeaderIncluded = true;
    private String delimiter = ",";
    private Optional<String> optEnclosingChars = Optional.empty();

    public static FlatDataWriterBuilder builder() {
        return new FlatDataWriterBuilder();
    }

    public static DataWriter defaultCsvFormat() {
        return FlatDataWriterBuilder.builder()
                        .outputHeaderRow(true)
                        .delimitedBy(",")
                        .build();
    }

    public FlatDataWriterBuilder outputHeaderRow(final boolean isHeaderIncluded) {
        this.isHeaderIncluded = isHeaderIncluded;
        return this;
    }

    public FlatDataWriterBuilder delimitedBy(final String delimiter) {
        //TODO need to consider escaping instance of the delimiter in values,
        this.delimiter = delimiter;
        return this;
    }

    public FlatDataWriterBuilder enclosedBy(final String enclosingChars) {
        //TODO need to consider escaping instance of the delimiter in values,
        this.optEnclosingChars = Optional.of(enclosingChars);
        return this;
    }

    private Function<DataRecord, String> getDataMapper() {
        final Function<String, String> enclosureMapper = getEnclosureMapper();

        return dataRecord ->
                dataRecord.values().stream()
                        .map(enclosureMapper)
                        .collect(Collectors.joining(delimiter));
    }

    public DataWriter build() {
        //return our mapping function which conforms to the DataWriter interface
        return this::mapRecords;
    }

    private Stream<String> mapRecords(final List<Field> fieldDefinitions, final Stream<DataRecord> recordStream) {
        final Function<DataRecord, String> dataMapper = getDataMapper();

        final Stream<String> dataStream = recordStream.map(dataMapper);
        if (isHeaderIncluded) {
            return Stream.concat(generateHeaderRow(fieldDefinitions), dataStream);
        } else {
            return dataStream;
        }
    }

    private Stream<String> generateHeaderRow(final List<Field> fieldDefinitions) {

        final Function<String, String> enclosureMapper = getEnclosureMapper();
        final String header = fieldDefinitions.stream()
                .map(Field::getName)
                .map(enclosureMapper)
                .collect(Collectors.joining(delimiter));
        return Stream.of(header);
    }

    private Function<String, String> getEnclosureMapper() {
        return optEnclosingChars.map(chars ->
                (Function<String, String>) str ->
                        (chars + str + chars))
                .orElse(Function.identity());
    }
}
