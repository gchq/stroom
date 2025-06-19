package stroom.util.user;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserDesc;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class UserDescUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserDescUtil.class);

    private static final int USER_ID_CSV_COL_IDX = 0;
    private static final int DISPLAY_NAME_CSV_COL_IDX = 1;
    private static final int FULL_NAME_CSV_COL_IDX = 2;

    /**
     * Parses usersCsvData into a {@link List<UserDesc>}. usersCsvData is CSV format with
     * one line per user. Each line is of the form:
     * <pre>{@code
     * <unique identifier>,[<opt. display name>,[<opt. full name>]]
     * }<</pre>
     * e.g.
     * <pre>{@code
     * 59634c61-7c67-4abc-8487-cb94e1f5b10f
     * ffaa3cc7-9664-4bf2-9019-d930c7281a66,jbloggs
     * a59cfb57-0ec9-47cd-9545-55e8f00f7679,jsmith,John Smith
     * "ed82c7d9-b29a-4712-bc78-cf178f41c691","jdoe","John Doe"
     * }<</pre>
     * i.e. You must supply the unique identifier then optionally the display name or display
     * name and full name.
     * Blank lines are ignored.
     *
     * @param usersCsvData The user names in CSV form.
     * @return A list of {@link UserDesc}
     */
    public static List<UserDesc> parseUsersCsvData(final String usersCsvData) {
        if (NullSafe.isBlankString(usersCsvData)) {
            return Collections.emptyList();
        } else {
            final StringReader stringReader = new StringReader(usersCsvData);
            try {
                final CSVParser parse = CSVFormat.DEFAULT.builder()
                        .setTrim(true)
                        .build()
                        .parse(stringReader);

                // Expecting something like
                // <user id>,[display name],[full name]
                return StreamSupport.stream(parse.spliterator(), false)
                        .filter(csvRec -> csvRec.isSet(USER_ID_CSV_COL_IDX))
                        .map(csvRec -> {
                            final String userId = csvRec.get(USER_ID_CSV_COL_IDX);
                            // These two may be null
                            final String displayName = csvRec.isSet(DISPLAY_NAME_CSV_COL_IDX)
                                    ? csvRec.get(DISPLAY_NAME_CSV_COL_IDX)
                                    : null;
                            final String fullName = csvRec.isSet(FULL_NAME_CSV_COL_IDX)
                                    ? csvRec.get(FULL_NAME_CSV_COL_IDX)
                                    : null;

                            return new UserDesc(
                                    userId,
                                    displayName,
                                    fullName);
                        })
                        .collect(Collectors.toList());
            } catch (final IOException e) {
                LOGGER.error("Unable to parse users CSV data\n{}", usersCsvData);
                throw new RuntimeException(LogUtil.message("Error parsing user CSV data: {}",
                        e.getMessage()), e);
            }
        }
    }

    public static Optional<UserDesc> parseSingleCSVUser(final String userCsvData) {
        final List<UserDesc> userNames = parseUsersCsvData(userCsvData);
        if (userNames.size() > 1) {
            throw new RuntimeException(LogUtil.message("Expecting one user only in data '{}'", userCsvData));
        }
        return userNames.stream().findAny();
    }
}
