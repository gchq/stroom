package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;
import stroom.query.api.v2.FlatResult;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class SearchDebugUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchDebugUtil.class);
    private static final Path dir = Paths.get("/home/stroomdev66/work/stroom-master-temp2/stroom-app/src/test/resources/TestSearchResultCreation");
    private static final boolean enabled = true;
    private static Writer writer;

    private SearchDebugUtil() {
    }

    private static String getSuffix(final boolean actual) {
        String suffix;
        if (actual) {
            suffix = "_actual.txt";
        } else {
            suffix = "_expected.txt";
        }
        return suffix;
    }

    public static void writeRequest(final SearchRequest searchRequest, final boolean actual) {
        if (enabled) {
            final String suffix = getSuffix(actual);
            try {
                final ObjectMapper mapper = new ObjectMapper();
                mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
                try (final Writer writer = new OutputStreamWriter(Files.newOutputStream(dir.resolve("searchRequest" + suffix)))) {
                    mapper.writeValue(writer, searchRequest);
                }
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    public static void writeResponse(final SearchResponse searchResponse, final boolean actual) {
        if (enabled) {
            final String suffix = getSuffix(actual);
            try {
                final ObjectMapper mapper = new ObjectMapper();
                mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

                try (final Writer writer = new OutputStreamWriter(Files.newOutputStream(dir.resolve("searchResponse" + suffix)))) {
                    mapper.writeValue(writer, searchResponse);
                }

                for (final Result result : searchResponse.getResults()) {
                    if (result instanceof TableResult) {
                        final TableResult tableResult = (TableResult) result;
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
                                    if (value == null) {
                                        writer.write("null");
                                    } else {
                                        writer.write(value);
                                    }
                                    if (j < row.getValues().size() - 1) {
                                        writer.write(",");
                                    }
                                }
                                if (i < tableResult.getRows().size() - 1) {
                                    writer.write("\n");
                                }
                            }
                        }

                    } else if (result instanceof FlatResult) {
                        final FlatResult flatResult = (FlatResult) result;
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

    public static synchronized void writeExtractionData(final Val[] values) {
        if (enabled) {
            try {
                if (writer == null) {
                    writer = new OutputStreamWriter(Files.newOutputStream(dir.resolve("data.txt")));
                }

                for (int i = 0; i < values.length; i++) {
                    Val value = values[i];
                    writer.write(value.toString());
                    if (i < values.length - 1) {
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
