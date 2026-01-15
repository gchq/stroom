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

package stroom.util.logging;


import stroom.util.logging.AsciiTable.Column;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class TestAsciiTable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAsciiTable.class);

    @Test
    void test() {

        final List<Pojo> sourceData = List.of(
                new Pojo("Mr", "Joe", "Bloggs",
                        LocalDate.of(1971, 3, 23), 180),
                new Pojo("Mrs", "Joanna", "Bloggs",
                        LocalDate.of(1972, 4, 1), 170),
                new Pojo("Mr", "No Surname", null,
                        LocalDate.of(1972, 4, 1), 170),
                new Pojo("Mrs", "Magdalena Clementine", "Fotherington-Smythe",
                        LocalDate.of(1971, 3, 6), 166)
        );

        final String table = AsciiTable.builder(sourceData)
                .withColumn(Column.of("Title", Pojo::getTitle))
                .withColumn(Column.of("First Name", Pojo::getFirstName))
                .withColumn(Column.builder("Surname", Pojo::getSurname)
                        .withNullValueSupplier(() -> "-")
                        .build())
                .withColumn(Column.builder("Date of Birth", Pojo::getDob)
                        .centerAligned()
                        .withFormat(localDate -> localDate.format(DateTimeFormatter
                                .ofLocalizedDate(FormatStyle.LONG)))
                        .build())
                .withColumn(Column.integer("Height (cm)", Pojo::getHeightCm))
                .withColumn(Column.decimal(
                        "Height (m)",
                        row -> row.getHeightCm() / (double) 100,
                        2))
                .withColumn(Column.integer("Height (mm)", pojo -> pojo.getHeightCm() * 10))
                .withRowLimit(100)
                .build();

        LOGGER.info("table:\n{}", table);
    }

    @Test
    void testSingleCol() {
        final List<Integer> numbers = IntStream.rangeClosed(1, 10)
                .boxed()
                .collect(Collectors.toList());

        final String table = AsciiTable.builder(numbers)
                .withColumn(Column.builder("Number", (Integer num) -> num.toString())
                        .rightAligned()
                        .build())
                .build();

        LOGGER.info("table:\n{}", table);

        final List<String> lines = table.lines()
                .collect(Collectors.toList());

        Assertions.assertThat(lines.get(0))
                .isEqualTo("| Number |");
        Assertions.assertThat(lines.get(1))
                .isEqualTo("|--------|");
        Assertions.assertThat(lines.get(2))
                .isEqualTo("|      1 |");
    }

    @Test
    void testAuto() {

        final List<Pojo> sourceData = List.of(
                new Pojo("Mr", "Joe", "Bloggs",
                        LocalDate.of(1971, 3, 23), 180),
                new Pojo("Mrs", "Joanna", "Bloggs",
                        LocalDate.of(1972, 4, 1), 170),
                new Pojo("Mr", "No Surname", null,
                        LocalDate.of(1972, 4, 1), 170)
        );

        final String table = AsciiTable.fromCollection(sourceData);

        LOGGER.info("table:\n{}", table);

        final List<String> lines = table.lines()
                .collect(Collectors.toList());

        Assertions.assertThat(lines)
                .hasSize(5);

        Assertions.assertThat(lines.get(0))
                .contains("Height Cm");
        Assertions.assertThat(lines.get(1))
                .matches("(\\|-+)+\\|");
        Assertions.assertThat(lines.get(2))
                .contains("Joe");
    }

    @Test
    void testAuto_singleCol_String() {

        final List<String> sourceData = List.of(
                "One",
                "Two",
                "Three");

        final String table = AsciiTable.fromCollection(sourceData);

        LOGGER.info("table:\n{}", table);

        final List<String> lines = table.lines()
                .collect(Collectors.toList());

        Assertions.assertThat(lines)
                .hasSize(5); // header + line + 3 rows

        Assertions.assertThat(lines.get(0))
                .contains("String");
        Assertions.assertThat(lines.get(1))
                .matches("(\\|-+)+\\|");
        Assertions.assertThat(lines.get(2))
                .contains("One");
    }

    @Test
    void testAuto_singleCol_int() {

        final List<Integer> sourceData = List.of(
                1,
                2,
                3);

        final String table = AsciiTable.fromCollection(sourceData);

        LOGGER.info("table:\n{}", table);

        final List<String> lines = table.lines()
                .collect(Collectors.toList());

        Assertions.assertThat(lines)
                .hasSize(5); // header + line + 3 rows

        Assertions.assertThat(lines.get(0))
                .contains("Integer");
        Assertions.assertThat(lines.get(1))
                .matches("(\\|-+)+\\|");
        Assertions.assertThat(lines.get(2))
                .contains("1");
    }

    @Test
    void testAuto_sorted() {

        final List<Pojo> sourceData = List.of(
                new Pojo("Mr", "Joe", "Bloggs",
                        LocalDate.of(1971, 3, 23), 180),
                new Pojo("Mrs", "Joanna", "Bloggs",
                        LocalDate.of(1972, 4, 1), 170),
                new Pojo("Mr", "No Surname", null,
                        LocalDate.of(1972, 4, 1), 170)
        );

        final String table = AsciiTable.fromCollection(sourceData, true);

        LOGGER.info("table:\n{}", table);

        final List<String> lines = table.lines()
                .collect(Collectors.toList());

        Assertions.assertThat(lines)
                .hasSize(5);

        Assertions.assertThat(lines.get(0))
                .contains("Height Cm");
        Assertions.assertThat(lines.get(1))
                .matches("(\\|-+)+\\|");
        Assertions.assertThat(lines.get(2))
                .contains("Joe");
    }

    @Test
    void testAsciiBar1() {

        final int min = 0;
        final int max = 64;
        IntStream.rangeClosed(min, max)
                .boxed()
                .map(i -> AsciiTable.asciiBar(i, min, max, 8))
                .forEach(System.out::println);
    }


    @Test
    void testAsciiBar2() {

        final int min = 10;
        final int max = 74;
        IntStream.rangeClosed(min, max)
                .boxed()
                .map(i -> AsciiTable.asciiBar(i, min, max, 8))
                .forEach(System.out::println);
    }

    @Test
    void testAsciiBar_inTable() {

        final int seed = 123;
        final int min = 0;
        final int max = 64;
        final Random random = new Random(seed);
        final List<Tuple2<Integer, String>> data = IntStream.rangeClosed(1, 15)
                .boxed()
                .map(i -> {
                    final int randomNum = random.nextInt(max);
                    return Tuple.of(
                            randomNum,
                            AsciiTable.asciiBar(randomNum, min, max, 8)
                    );
                })
                .collect(Collectors.toList());

        final String table = AsciiTable.builder(data)
                .withColumn(Column.builder("Number", (Tuple2<Integer, String> tuple2) -> tuple2._1.toString())
                        .rightAligned()
                        .build())
                .withColumn(Column.builder("Bar", (Tuple2<Integer, String> tuple2) -> tuple2._2)
                        .build())
                .build();

        LOGGER.info("table:\n{}", table);
    }

    private static class Pojo {

        private final String title;
        private final String firstName;
        private final String surname;
        private final LocalDate dob;
        private final Integer heightCm;

        public Pojo(final String title,
                    final String firstName,
                    final String surname,
                    final LocalDate dob,
                    final Integer heightCm) {
            this.title = title;
            this.firstName = firstName;
            this.surname = surname;
            this.dob = dob;
            this.heightCm = heightCm;
        }

        public String getTitle() {
            return title;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getSurname() {
            return surname;
        }

        public LocalDate getDob() {
            return dob;
        }

        public Integer getHeightCm() {
            return heightCm;
        }
    }
}
