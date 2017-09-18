/*
 * Copyright 2016 Crown Copyright
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

import stroom.statistics.shared.StatisticType;
import stroom.util.date.DateUtil;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class GenerateSampleStatisticsData {
    private static final String USER1 = "user1";
    private static final String USER2 = "user2";

    // 52,000 is just over 3 days at 5000ms intervals
    private static final int ITERATION_COUNT = 52_000;
    private static final int EVENT_TIME_DELTA_MS = 5000;

    private static final String COLOUR_RED = "Red";
    private static final String COLOUR_GREEN = "Green";
    private static final String COLOUR_BLUE = "Blue";

    private static final List<String> COLOURS = Arrays.asList(COLOUR_RED, COLOUR_GREEN, COLOUR_BLUE);

    private static final List<String> STATES = Arrays.asList("IN", "OUT");

    private static final String[] users = new String[]{USER1, USER2};

    public static void main(final String[] args) throws Exception {
        System.out.println("Writing value data...");

        try (final Writer writer = Files.newBufferedWriter(Paths.get("StatsTestData_Values.xml"))) {
            writer.write(generateValueData());
        }
        System.out.println("Writing count data...");

        try (final Writer writer = Files.newBufferedWriter(Paths.get("StatsTestData_Counts.xml"))) {
            writer.write(generateCountData());
        }
        System.out.println("Finished!");
    }

    private static long getStartTime() {
        final long now = System.currentTimeMillis();

        final long daysSinceEpoch = now / DateUtil.DAY_MS;

        return daysSinceEpoch * DateUtil.DAY_MS;
    }

    public static String generateValueData() {
        final long eventTime = getStartTime();

        final StringBuilder stringBuilder = new StringBuilder();

        buildEvents(stringBuilder, eventTime, StatisticType.VALUE);

        return stringBuilder.toString();
    }

    public static String generateCountData() {
        final long eventTime = getStartTime();

        final StringBuilder stringBuilder = new StringBuilder();

        buildEvents(stringBuilder, eventTime, StatisticType.COUNT);

        return stringBuilder.toString();
    }

    private static void buildEvents(final StringBuilder stringBuilder, final long initialEventTime,
                                    final StatisticType statisticType) {
        long eventTime = initialEventTime;

        stringBuilder.append("<data>\n");

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
                            stringBuilder.append("<value>" + 1 + "</value>");
                        } else {
                            String val = "";
                            if (colour.equals(COLOUR_RED)) {
                                val = "10.1";
                            } else if (colour.equals(COLOUR_GREEN)) {
                                val = "20.2";
                            } else if (colour.equals(COLOUR_BLUE)) {
                                val = "69.7";
                            }
                            stringBuilder.append("<value>" + val + "</value>");
                        }
                        stringBuilder.append("</event>\n");
                    }
                }
            }
            eventTime += EVENT_TIME_DELTA_MS;
        }
        stringBuilder.append("</data>\n");
    }
}
