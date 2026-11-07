/*
 * Copyright 2026 Crown Copyright
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

package stroom.mcp.impl.e2e;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/// Streams whose events' times are in different places, each record with the time its event should be given (the
/// work package's T2, T3, T5, T7 and T11): a record's own time beside its container's, in some records only, beside
/// times that are an object's, a kind whose time the agent chooses, and a stream of two formats. The same text every
/// run (seeded).
final class EventTimeFeed {

    /// Records in each stream.
    static final int RECORDS = 30;

    private static final Instant START = Instant.parse("2026-11-06T09:00:00Z");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")
            .withZone(ZoneOffset.UTC);
    // CEF's rt as a date: no zone (the user says UTC).
    private static final DateTimeFormatter RT = DateTimeFormatter.ofPattern("MMM dd yyyy HH:mm:ss", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter SYSLOG = DateTimeFormatter.ofPattern("MMM ppd HH:mm:ss", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final List<String> KINDS = List.of("APPFW_SQL", "APPFW_XSS", "APPFW_STARTURL");
    private static final List<String> USERS = List.of("alice", "bob", "carol", "dave");

    private EventTimeFeed() {
        // Static only.
    }

    /// T2: CEF after rsyslog's ISO header, rt on every record: rt's time (to the second), not the relay's.
    ///
    /// @return The stream.
    static Feed cefOwnTimeEvery() {
        return cef(1, 1);
    }

    /// T3: rt on 2 records in 5: rt's time where given, else the relay's header time (not the received time).
    ///
    /// @return The stream.
    static Feed cefOwnTimeSome() {
        return cef(2, 5);
    }

    /// T5: JSON with event_time beside an account's creation (2019) and a token's expiry (2027): event_time.
    ///
    /// @return The stream.
    static Feed objectTimes() {
        final Random random = new Random(5);
        final Feed feed = new Feed();
        Instant time = START;
        for (int i = 0; i < RECORDS; i++) {
            time = next(random, time);
            feed.add("{\"event_time\": \"" + ISO.format(time) + "\", \"user\": \"" + pick(random, USERS)
                     + "\", \"action\": \"token_issued\", \"created_at\": \"2019-03-0" + (1 + random.nextInt(9))
                     + "T08:00:00Z\", \"expires\": \"2027-01-0" + (1 + random.nextInt(9)) + "T00:00:00Z\"}", time);
        }
        return feed;
    }

    /// T7: two kinds; file_access has event_time, session has start and end (the agent chooses end, when the
    /// session was logged).
    ///
    /// @return The stream.
    static Feed twoKinds() {
        final Random random = new Random(7);
        final Feed feed = new Feed();
        Instant time = START;
        for (int i = 0; i < RECORDS; i++) {
            time = next(random, time);
            if (i % 2 == 0) {
                feed.add("{\"type\": \"file_access\", \"event_time\": \"" + ISO.format(time) + "\", \"user\": \""
                         + pick(random, USERS) + "\", \"file\": \"/srv/share/" + random.nextInt(100) + ".docx\"}",
                        time);
            } else {
                final Instant start = time.minus(1 + random.nextInt(50), ChronoUnit.MINUTES);
                feed.add("{\"type\": \"session\", \"start\": \"" + ISO.format(start) + "\", \"end\": \""
                         + ISO.format(time) + "\", \"user\": \"" + pick(random, USERS) + "\"}", time);
            }
        }
        return feed;
    }

    /// T11: one relay's stream of CEF with rt (its own time) and RFC 3164 host lines with none (the header's).
    ///
    /// @return The stream.
    static Feed mixed() {
        final Random random = new Random(11);
        final Feed feed = new Feed();
        Instant time = START;
        for (int i = 0; i < RECORDS; i++) {
            time = next(random, time);
            if (i % 2 == 0) {
                final Instant own = time.truncatedTo(ChronoUnit.SECONDS);
                feed.add(relayed(random, time, "rt=" + RT.format(own) + " src=10.0.0." + (1 + random.nextInt(250))
                                               + " act=blocked"), own);
            } else {
                // RFC 3164: no year, which Stroom takes from the stream; to the second.
                final Instant own = time.truncatedTo(ChronoUnit.SECONDS);
                feed.add("<30>" + SYSLOG.format(own) + " host7 sshd[" + (1000 + random.nextInt(9000))
                         + "]: Accepted publickey for " + pick(random, USERS) + " from 10.2.0."
                         + (1 + random.nextInt(250)), own);
            }
        }
        return feed;
    }

    // CEF lines through a relay, rt on `of` records in `every`.
    private static Feed cef(final int of, final int every) {
        final Random random = new Random(every);
        final Feed feed = new Feed();
        Instant time = START;
        for (int i = 0; i < RECORDS; i++) {
            time = next(random, time);
            final boolean own = i % every < of;
            final Instant device = time.truncatedTo(ChronoUnit.SECONDS);
            final Instant relay = time.plusMillis(500 + random.nextInt(2_500));
            final String ext = (own
                    ? "rt=" + RT.format(device) + " "
                    : "") + "src=10.0." + random.nextInt(4) + "." + (1 + random.nextInt(250)) + " act=blocked";
            feed.add("<134>" + ISO.format(relay) + " relay1 ns: CEF:0|Citrix|NetScaler|NS13.1|APPFW|"
                     + pick(random, KINDS) + "|6|" + ext, own
                    ? device
                    : relay.truncatedTo(ChronoUnit.MILLIS));
        }
        return feed;
    }

    private static String relayed(final Random random, final Instant time, final String ext) {
        return "<134>" + ISO.format(time.plusMillis(500 + random.nextInt(2_500))) + " relay1 ns: "
               + "CEF:0|Citrix|NetScaler|NS13.1|APPFW|" + pick(random, KINDS) + "|6|" + ext;
    }

    private static Instant next(final Random random, final Instant time) {
        return time.plusMillis(1_000 + random.nextInt(29_000));
    }

    private static String pick(final Random random, final List<String> from) {
        return from.get(random.nextInt(from.size()));
    }


    // --------------------------------------------------------------------------------


    /// A stream's lines and the time each record's event should have.
    static final class Feed {

        private final List<String> lines = new ArrayList<>();
        private final List<Instant> times = new ArrayList<>();

        private void add(final String line, final Instant time) {
            lines.add(line);
            times.add(time.truncatedTo(ChronoUnit.MILLIS));
        }

        /// @return The stream's text.
        String text() {
            return String.join("\n", lines) + "\n";
        }

        /// @return Each record's expected time, in order.
        List<Instant> times() {
            return List.copyOf(times);
        }
    }
}
