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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TestFlatFileExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFlatFileExtension.class);

    private static final int NUMBER_FILES = 3;
    private static final int ROW_COUNT = 8;

    @RegisterExtension()
    public static final FlatFileTestDataExtension testDataRule = FlatFileTestDataExtension.withTempDirectory()
            .testDataGenerator(TestFlatFileExtension::generateTestData)
            .numberOfFiles(NUMBER_FILES)
            .build();

    private static void generateTestData(final Consumer<String> writer) {
        DataGenerator.buildDefinition()
                .addFieldDefinition(DataGenerator.randomValueField("Species", Arrays.asList(
                        "spider", "whale", "dog", "tiger", "monkey", "lion", "woodlouse", "honey-badger")))
                .addFieldDefinition(DataGenerator.randomValueField("Continent",
                        Arrays.asList("europe", "asia", "america", "antarctica", "africa", "australia")))
                .addFieldDefinition(DataGenerator.randomValueField("ObservationTeam",
                        Arrays.asList("alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel")))
                .addFieldDefinition(DataGenerator.randomDateTimeField("Time",
                        LocalDateTime.of(2016, 1, 1, 0, 0, 0),
                        LocalDateTime.of(2018, 1, 1, 0, 0, 0),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .setDataWriter(FlatDataWriterBuilder.defaultCsvFormat())
                .rowCount(ROW_COUNT)
                .consumedBy(s -> s.forEach(writer))
                .generate();
    }

    @Test
    public void testFilesCreated() throws IOException {
        // The rule keeps trak of the files it has created
        Assertions.assertThat(testDataRule.getDataFiles().size())
                .isEqualTo(NUMBER_FILES);

        // The rule exposes the folder it has created so you can find the files with a walk
        Files.walk(testDataRule.getFolder().toAbsolutePath())
                .filter(p -> !Files.isDirectory(p))
                .peek(p -> Assertions.assertThat(testDataRule.getDataFiles()).contains(p))
                .forEach(p -> {
                    LOGGER.info("Path Created {}", p);

                    try (final Stream<String> stream = Files.lines(p)) {
                        stream.forEach(LOGGER::debug);
                    } catch (final IOException e) {
                        Assertions.fail(e.getLocalizedMessage());
                    }
                });
    }
}
