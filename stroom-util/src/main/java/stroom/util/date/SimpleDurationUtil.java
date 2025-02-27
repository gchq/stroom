package stroom.util.date;

import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleDurationUtil {

    private static final Pattern NUMBERS = Pattern.compile("\\d*");

    private SimpleDurationUtil() {
        // Utility.
    }

    public static SimpleDuration parse(final String string) throws ParseException {
        Objects.requireNonNull(string);
        final Matcher matcher = NUMBERS.matcher(string);
        if (matcher.find()) {
            final long time;
            final TimeUnit timeUnit;

            final int index = matcher.end();
            try {
                final String numeric = string.substring(0, index);
                time = Long.parseLong(numeric);
            } catch (final NumberFormatException e) {
                throw new ParseException("Unable to find numeric part", 0);
            }

            final String unit = string.substring(index);
            final Optional<TimeUnit> optional = Arrays
                    .stream(TimeUnit.values())
                    .filter(tu -> tu.getShortForm().equals(unit))
                    .findFirst();
            timeUnit = optional.orElseThrow(() -> new ParseException("Unable to find correct unit", index));

            return new SimpleDuration(time, timeUnit);
        } else {
            throw new ParseException("Unable to find numeric part", 0);
        }
    }
}
