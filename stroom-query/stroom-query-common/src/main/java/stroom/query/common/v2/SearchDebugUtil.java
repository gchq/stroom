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

package stroom.query.common.v2;

import stroom.query.api.FlatResult;
import stroom.query.api.Result;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.util.io.DiffUtil;
import stroom.util.io.FileUtil;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

public class SearchDebugUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchDebugUtil.class);

    private static Path dir;
    private static boolean enabled;

    private static final boolean writeActual = true;
    private static final boolean writeExpected = false;
    private static Writer writer;

    private SearchDebugUtil() {
    }

    public static Path initialise() {
        dir = resolveDir().resolve("src/test/resources/TestSearchResultCreation");
        enabled = true;
        return dir;
    }

    private static Path resolveDir() {
        final String path = SearchDebugUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        Path root = Paths.get(path).toAbsolutePath().normalize();
        while (root != null && !Files.isDirectory(root.resolve("stroom-query"))) {
            root = root.getParent();
        }

        if (root == null) {
            throw new RuntimeException("Path not found: " + dir);
        }

        return root.resolve("stroom-search").resolve("stroom-search-impl");
    }

    private static String getSuffix(final boolean actual) {
        final String suffix;
        if (actual) {
            suffix = "_actual.txt";
        } else {
            suffix = "_expected.txt";
        }
        return suffix;
    }

    public static void validateRequest() {
        compareFiles(dir.resolve("searchRequest_actual.txt"), dir.resolve("searchRequest_expected.txt"));
    }

    public static void validateResponse() {
        compareFiles(dir.resolve("searchResponse_actual.txt"), dir.resolve("searchResponse_expected.txt"));
        compareFiles(dir.resolve("table-78LF4_actual.txt"), dir.resolve("table-78LF4_expected.txt"));
        compareFiles(dir.resolve("table-BKJT6_actual.txt"), dir.resolve("table-BKJT6_expected.txt"));
        compareFiles(dir.resolve("vis-L1AL1_actual.txt"), dir.resolve("vis-L1AL1_expected.txt"));
        compareFiles(dir.resolve("vis-QYG7H_actual.txt"), dir.resolve("vis-QYG7H_expected.txt"));
        compareFiles(dir.resolve("vis-SPSCW_actual.txt"), dir.resolve("vis-SPSCW_expected.txt"));
    }

    private static void compareFiles(final Path pathForActualOutput, final Path pathForExpectedOutput) {

        final boolean hasDifferences = DiffUtil.unifiedDiff(
                pathForExpectedOutput,
                pathForActualOutput,
                true,
                5);

        if (hasDifferences) {
            LOGGER.info("vimdiff {} {}",
                    FileUtil.getCanonicalPath(pathForExpectedOutput),
                    FileUtil.getCanonicalPath(pathForActualOutput));

            throw new RuntimeException("Files are not equal " + pathForActualOutput.toAbsolutePath() +
                    " " + pathForExpectedOutput.toAbsolutePath());
        }
    }

    public static void writeRequest(final SearchRequest searchRequest, final boolean actual) {
        if (enabled && ((writeExpected && !actual) || (writeActual && actual))) {
            final String suffix = getSuffix(actual);
            try {
                final ObjectMapper mapper = JsonUtil.getMapper();
                try (final Writer writer = new OutputStreamWriter(
                        Files.newOutputStream(dir.resolve("searchRequest" + suffix)))) {
                    mapper.writeValue(writer, searchRequest);
                }
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    public static void writeResponse(final SearchResponse searchResponse, final boolean actual) {
        if (enabled && ((writeExpected && !actual) || (writeActual && actual))) {
            final String suffix = getSuffix(actual);
            try {
                final ObjectMapper mapper = JsonUtil.getMapper();

                try (final Writer writer = new OutputStreamWriter(Files.newOutputStream(
                        dir.resolve("searchResponse" + suffix)))) {
                    mapper.writeValue(writer, searchResponse);
                }

                for (final Result result : searchResponse.getResults()) {
                    if (result instanceof final TableResult tableResult) {
                        final Path path = dir.resolve(result.getComponentId() + suffix);
                        try (final Writer writer = new OutputStreamWriter(Files.newOutputStream(path))) {
                            for (int i = 0; i < tableResult.getRows().size(); i++) {
                                final Row row = tableResult.getRows().get(i);

                                if (row.getGroupKey() == null) {
                                    writer.write("null");
                                } else {
                                    writer.write(row.getGroupKey());
                                }
                                writer.write("__");

                                writer.write(String.valueOf(row.getDepth()));
                                writer.write("__");

                                for (int j = 0; j < row.getValues().size(); j++) {
                                    final String value = row.getValues().get(j);
                                    writer.write(Objects.requireNonNullElse(value, "null"));
                                    if (j < row.getValues().size() - 1) {
                                        writer.write(",");
                                    }
                                }
                                if (i < tableResult.getRows().size() - 1) {
                                    writer.write("\n");
                                }
                            }
                        }

                    } else if (result instanceof final FlatResult flatResult) {
                        final Path path = dir.resolve(result.getComponentId() + suffix);
                        try (final Writer writer = new OutputStreamWriter(Files.newOutputStream(path))) {
                            for (int i = 0; i < flatResult.getValues().size(); i++) {
                                final List<Object> list = flatResult.getValues().get(i);
                                for (int j = 0; j < list.size(); j++) {
                                    final Object object = list.get(j);
                                    if (object == null) {
                                        writer.write("null");
                                    } else {
                                        writer.write(object.toString());
                                    }
                                    if (j < list.size() - 1) {
                                        writer.write(",");
                                    }
                                }
                                if (i < flatResult.getValues().size() - 1) {
                                    writer.write("\n");
                                }
                            }
                        }
                    }
                }
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    public static synchronized void writeExtractionData(final List<StringFieldValue> values) {
        if (enabled && writeExpected) {
            try {
                if (writer == null) {
                    writer = new OutputStreamWriter(Files.newOutputStream(dir.resolve("data.txt")));
                }

                for (int i = 0; i < values.size(); i++) {
                    final StringFieldValue value = values.get(i);
                    writer.write(value.fieldValue());
                    if (i < values.size() - 1) {
                        writer.write(",");
                    } else {
                        writer.write("\n");
                    }
                }

                writer.flush();

            } catch (final Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }
}
