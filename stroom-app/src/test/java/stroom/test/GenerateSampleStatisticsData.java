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

package stroom.test;

import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.util.date.DateUtil;

import io.vavr.Tuple3;
import io.vavr.Tuple4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class GenerateSampleStatisticsData {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateSampleStatisticsData.class);

    private static final String USER1 = "user1";
    private static final String USER2 = "user2";

    // 52,000 is just over 3 days at 5000ms intervals
    private static final int DEFAULT_ITERATION_COUNT = 52_000;
    private static final int EVENT_TIME_DELTA_MS = 5000;

    private static final String COLOUR_RED = "Red";
    private static final String COLOUR_GREEN = "Green";
    private static final String COLOUR_BLUE = "Blue";

    private static final List<String> COLOURS = Arrays.asList(COLOUR_RED, COLOUR_GREEN, COLOUR_BLUE);

    private static final List<String> STATES = Arrays.asList("IN", "OUT");

    private static final List<String> USERS = Arrays.asList(USER1, USER2);

    public static void main(final String[] args) throws IOException {
        System.out.println("Writing value data...");

        try (final Writer writer = Files.newBufferedWriter(Paths.get("StatsTestData_Values.xml"))) {
            writer.write(generateValueData(DEFAULT_ITERATION_COUNT, getStartTime()));
        }
        System.out.println("Writing count data...");

        try (final Writer writer = Files.newBufferedWriter(Paths.get("StatsTestData_Counts.xml"))) {
            writer.write(generateCountData(DEFAULT_ITERATION_COUNT, getStartTime()));
        }
        System.out.println("Finished!");
    }

    private static Instant getStartTime() {
        //get the start of today
        return Instant.now().truncatedTo(ChronoUnit.DAYS);
    }

    public static String generateValueData(final int iterations, final Instant startTime) {

        return buildEvents(iterations, startTime, StatisticType.VALUE);
    }

    public static String generateCountData(final int iterations, final Instant startTime) {

        return buildEvents(iterations, startTime, StatisticType.COUNT);
    }

    private static List<Tuple3<String, String, String>> buildUserColourStateCombinations() {
        final List<Tuple3<String, String, String>> combinations = new ArrayList<>();
        for (final String user : USERS) {
            for (final String colour : COLOURS) {
                for (final String state : STATES) {
                    combinations.add(new Tuple3<>(user, colour, state));
                }
            }
        }
        return combinations;
    }

    private static String buildEvents(final int iterations,
                                      final Instant initialEventTime,
                                      final StatisticType statisticType) {
        final StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("<data>\n");

        final AtomicLong eventCount = new AtomicLong(0);

        LOGGER.info("Building statistic test data of type {} with {} iterations",
                statisticType, iterations);

        //each time iteration will have a combination of each of users|colours|states
        final List<Tuple3<String, String, String>> combinations = buildUserColourStateCombinations();

        //parallel stream so data will be in a random order
        final String data = LongStream.range(0, iterations)
                .parallel()
                .boxed()
                .map(i ->
                        DateUtil.createNormalDateTimeString(
                                initialEventTime.plusMillis(i * EVENT_TIME_DELTA_MS).toEpochMilli()))
                .flatMap(timeStr ->
                        combinations.stream()
                                .map(userColourState -> new Tuple4<>(
                                        timeStr, userColourState._1(), userColourState._2(), userColourState._3())))
                .map(tuple4 -> {
                    //build the event xml
                    final StringBuilder stringBuilder = new StringBuilder()
                            .append("<event>")
                            .append("<time>")
                            .append(tuple4._1()) //time
                            .append("</time>")
                            .append("<user>")
                            .append(tuple4._2()) //user
                            .append("</user>")
                            .append("<colour>")
                            .append(tuple4._3()) //colour
                            .append("</colour>")
                            .append("<state>")
                            .append(tuple4._4()) //state
                            .append("</state>")
                            .append("<value>")
                            .append(getStatValue(statisticType, tuple4._3()))
                            .append("</value>")
                            .append("</event>\n");
                    eventCount.incrementAndGet();
                    return stringBuilder.toString();
                })
                .collect(Collectors.joining("\n"));

        stringBuffer.append(data);
        stringBuffer.append("</data>\n");
        LOGGER.info("Created {} {} statistic events", String.format("%,d", eventCount.get()), statisticType);
        return stringBuffer.toString();
    }

    private static String getStatValue(final StatisticType statisticType, final String colour) {
        final String val;
        switch (statisticType) {
            case COUNT:
                val = "1";
                break;
            case VALUE:
                if (colour.equals(COLOUR_RED)) {
                    val = "10.1";
                } else if (colour.equals(COLOUR_GREEN)) {
                    val = "20.2";
                } else if (colour.equals(COLOUR_BLUE)) {
                    val = "69.7";
                } else {
                    throw new RuntimeException("Unexpected colour " + colour);
                }
                break;
            default:
                throw new RuntimeException("Unexpected statisticType " + statisticType);
        }
        return val;
    }

}
