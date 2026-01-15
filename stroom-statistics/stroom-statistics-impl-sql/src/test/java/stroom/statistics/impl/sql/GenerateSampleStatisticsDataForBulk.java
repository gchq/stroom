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

package stroom.statistics.impl.sql;

import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.util.date.DateUtil;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class GenerateSampleStatisticsDataForBulk {

    private static final String START_DATE = "2015-05-01T00:00:00.000Z";

    // 52,000 is just over 3 days at 5000ms intervals
    private static final int ITERATION_COUNT = 200_000;
    private static final int EVENT_TIME_DELTA_MS = 5000;

    private static final String COLOUR_RED = "Red";
    private static final String COLOUR_GREEN = "Green";
    private static final String COLOUR_BLUE = "Blue";

    private static final Random RANDOM = new Random(293874928374298744L);

    private static final List<String> COLOURS = Arrays.asList(COLOUR_RED, COLOUR_GREEN, COLOUR_BLUE);

    private static final List<String> STATES = Arrays.asList("IN", "OUT");

    private static final String[] users = new String[]{"user1", "user2", "user3", "user4", "user5"};

    public static void main(final String[] args) throws IOException {
        System.out.println("Writing value data...");

        try (final Writer writer = Files.newBufferedWriter(Paths.get("StatsBulkTestData_Values.xml"))) {
            generateValueData(writer);
        }
        System.out.println("Writing count data...");

        try (final Writer writer = Files.newBufferedWriter(Paths.get("StatsBulkTestData_Counts.xml"))) {
            generateCountData(writer);
        }
        System.out.println("Finished!");
    }

    public static String generateValueData(final Writer writer) throws IOException {
        final long eventTime = DateUtil.parseNormalDateTimeString(START_DATE);

        final StringBuilder stringBuilder = new StringBuilder();

        buildEvents(writer, eventTime, StatisticType.VALUE);

        return stringBuilder.toString();
    }

    public static String generateCountData(final Writer writer) throws IOException {
        final long eventTime = DateUtil.parseNormalDateTimeString(START_DATE);

        final StringBuilder stringBuilder = new StringBuilder();

        buildEvents(writer, eventTime, StatisticType.COUNT);

        return stringBuilder.toString();
    }

    private static void buildEvents(final Writer writer, final long initialEventTime, final StatisticType statisticType)
            throws IOException {
        long eventTime = initialEventTime;

        writer.write("<data>\n");

        final StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i <= ITERATION_COUNT; i++) {
            for (final String user : users) {
                for (final String colour : COLOURS) {
                    for (final String state : STATES) {
                        stringBuilder.append("<event>");
                        stringBuilder.append("<time>" + DateUtil.createNormalDateTimeString(eventTime) + "</time>");
                        stringBuilder.append("<user>" + user + "</user>");
                        stringBuilder.append("<colour>" + colour + "</colour>");
                        stringBuilder.append("<state>" + state + "</state>");

                        if (statisticType.equals(StatisticType.COUNT)) {
                            stringBuilder.append("<value>" + (RANDOM.nextInt(10) + 1) + "</value>");
                        } else {
                            final String val = Double.toString(RANDOM.nextInt(100) + RANDOM.nextDouble());
                            stringBuilder.append("<value>" + val + "</value>");
                        }
                        stringBuilder.append("</event>\n");

                        writer.write(stringBuilder.toString());
                        stringBuilder.setLength(0);
                    }
                }
            }
            eventTime += EVENT_TIME_DELTA_MS;
        }
        writer.write("</data>\n");
    }
}
