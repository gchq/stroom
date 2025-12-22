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

package stroom.test.tools;

import stroom.util.shared.RandomId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

public class TestDataCreator {

    private static final int FIELD_COUNT = 500;
    private static final int ROW_COUNT = 500;

    private static int getRandomLength(final int max) {
        return Math.max(1, (int) (Math.random() * max));
    }

    private static String createRandomString(final int maxParts, final int maxPartLength) {
        final int parts = getRandomLength(maxParts);
        final StringBuilder sb = new StringBuilder();
        for (int j = 0; j < parts; j++) {
            final String part = RandomId.createId(getRandomLength(maxPartLength));
            if (sb.length() > 0) {
                sb.append(".");
            }
            sb.append(part);
        }
        return sb.toString();
    }

    public static void main(final String[] args) {
        final String home = System.getProperty("user.home");
        try (final Writer writer = Files.newBufferedWriter(Paths.get(home).resolve("sample-data.txt"))) {
            writer.write("Date,Time,FileNo,LineNo,User,Message");
            for (int i = 0; i < FIELD_COUNT; i++) {
                final String field = createRandomString(5, 10).toLowerCase(Locale.ROOT);
                writer.write(",");
                writer.write(field);
            }

            for (int k = 0; k < ROW_COUNT; k++) {
                writer.write("\n");
                writer.write("01/01/2010,00:00:00,4,1,user1,Some message ");
                writer.write(Integer.toString(k));

                for (int i = 0; i < FIELD_COUNT; i++) {
                    final String value = createRandomString(3, 10).toLowerCase(Locale.ROOT);
                    writer.write(",");
                    if (value.length() > 10) {
                        writer.write(value);
                    }
                }
            }

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
