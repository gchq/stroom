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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// The insider-risk test dataset: 12 months of a few hundred thousand events (at full scale) of 6 scenario people and
/// up to 30 background people in 3 `Organisation`s, from 5 sources (an endpoint agent, a web proxy, a VPN, a cloud
/// audit log and privileged sessions), each person with a person id (decorated on their events, indexed as
/// `user.id`), an `Organisation` (in the Event XML only) and logon ids whose form differs by source. Behaviour is
/// generated a person-day at a time from a seed (working days with a lunch gap, quiet weekends, tasks of 20 minutes
/// to 3 hours with interruptions and returns, the browser's domains those of the task), with planted behaviours and
/// decoys written to a known answer ([#knownAnswer]).
///
/// Each person-day has its own seeded draw, so a reduced form ([Options#reduced]: fewer background people, fewer
/// routine events, only some days) gives exactly the full form's events on the days it keeps, for e2e tests.
/// [InsiderRecords] writes the events as index documents, Events XML and the dev node's raw files.
final class InsiderDataset {

    /// The zone everyone works in.
    static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    /// The first day: month 1.
    static final LocalDate FIRST_DAY = LocalDate.of(2025, 11, 1);
    /// The day after the last.
    static final LocalDate END_DAY = FIRST_DAY.plusMonths(12);
    /// The seed of the full dataset.
    static final long SEED = 5848;
    /// The organisations.
    static final String FINANCE = "Finance";
    static final String ENGINEERING = "Engineering";
    static final String OPERATIONS = "Operations";
    /// The scenario people's ids.
    static final String LEAVER = "P10001";
    static final String ADMIN = "P10002";
    static final String VIEWER = "P10003";
    static final String COMPROMISED = "P10004";
    static final String ROLE_CHANGE = "P10005";
    static final String DRIFT = "P10006";
    /// The domain cloud and proxy accounts are in.
    static final String MAIL_DOMAIN = "corp.example";
    /// The leaver's leaving date (their last day).
    static final LocalDate LEAVING = LocalDate.of(2026, 9, 25);
    /// The day the proxy's pipeline changed, everyone's volume stepping up (month 8, a decoy).
    static final LocalDate PIPELINE_CHANGE = LocalDate.of(2026, 6, 15);
    /// The day the role change took effect (month 5).
    static final LocalDate ROLE_CHANGE_DAY = LocalDate.of(2026, 3, 2);
    /// The day the role-change person's new host first appears (month 11).
    static final LocalDate NEW_HOST_DAY = LocalDate.of(2026, 9, 14);
    /// The days the leaver archives files and uploads them to personal cloud (once more on two of them, in the
    /// evening).
    static final Set<LocalDate> UPLOAD_DAYS = Set.of(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 15),
            LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 22));
    /// The personal cloud domain the leaver uploads to.
    static final String PERSONAL_CLOUD = "mydrive.personal-cloud.example";
    static final String PERSONAL_CLOUD_CATEGORY = "Personal Cloud Storage";
    static final String JOB_CATEGORY = "Job Search";
    static final String WEBMAIL_CATEGORY = "Webmail";
    /// The tool the disgruntled admin had never used before.
    static final String NEW_TOOL = "netdump64.exe";
    /// The day an administrator grants the leaver a privileged group, 11 days before they leave (done to them).
    static final LocalDate GRANT_DAY = LocalDate.of(2026, 9, 14);
    /// The privileged group granted.
    static final String PRIVILEGED_GROUP = "Finance Data Admins";
    /// The background administrator who grants it.
    static final String GRANTING_ADMIN = "P20002";
    /// The night of the compromised account's takeover: failed logons against it, a reset and an MFA device added.
    static final LocalDate TAKEOVER_DAY = LocalDate.of(2026, 4, 8);
    /// The service desk's account, of no person: it resets passwords and registers MFA devices.
    static final String SERVICE_DESK = "servicedesk@" + MAIL_DOMAIN;
    /// The MFA device added to the compromised account.
    static final String MFA_DEVICE = "Authenticator app (SM-A146)";
    /// The content delivery network everyone's browser fetches from ([#cdnActs]): its common host, and its
    /// subdomains (a new one most days for each person), each rare by name alone.
    static final String CDN = "cdn-edge.example";
    static final String CDN_HOST = "static." + CDN;
    static final String CDN_CATEGORY = "Content Delivery";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final int MINUTE = 60_000;
    private static final List<String> FIRST_NAMES = List.of("Ava", "Ben", "Chloe", "Daniel", "Ella", "Felix",
            "Grace", "Harry", "Isla", "Jack", "Kiara", "Liam", "Mia", "Noah", "Olivia", "Patrick", "Quinn", "Ruby",
            "Sean", "Tara", "Umar", "Vera", "Will", "Xena", "Yusuf", "Zoe", "Aaron", "Bella", "Caleb", "Delia");
    private static final List<String> LAST_NAMES = List.of("Hart", "Petrov", "Ito", "Marsh", "Pillai", "Fraser",
            "Greene", "Hughes", "Irwin", "Jensen", "Kaur", "Lowe", "Moreau", "Nash", "Ortiz", "Price", "Quigley",
            "Russo", "Sato", "Turner", "Usman", "Vance", "Walsh", "Xu", "Young", "Zhang", "Abbott", "Baird",
            "Coleman", "Doyle");
    private static final List<String> ORGANISATIONS = List.of(FINANCE, ENGINEERING, OPERATIONS);

    private final Options options;
    private final List<Person> people = new ArrayList<>();
    private final List<Act> acts = new ArrayList<>();
    private final List<Act> targetActs = new ArrayList<>();
    private final List<Act> noiseActs = new ArrayList<>();
    private final List<Act> cdnActs = new ArrayList<>();
    private final List<Map<String, Object>> nearMisses = new ArrayList<>();
    private final List<Map<String, Object>> targetPlants = new ArrayList<>();
    private final List<Map<String, Object>> targetNoise = new ArrayList<>();
    private final List<Map<String, Object>> plants = new ArrayList<>();
    private final List<Map<String, Object>> decoys = new ArrayList<>();
    private final List<Map<String, Object>> oneOffs = new ArrayList<>();
    private final Map<String, Map<String, List<Map<String, Object>>>> tasks = new LinkedHashMap<>();

    private InsiderDataset(final Options options) {
        this.options = options;
    }

    /// Generates the dataset.
    ///
    /// @param options The seed, the background people, the scale and the days kept.
    /// @return The dataset.
    static InsiderDataset generate(final Options options) {
        final InsiderDataset dataset = new InsiderDataset(options);
        dataset.makePeople();
        dataset.makeActs();
        dataset.makeTargets();
        dataset.makeNoise();
        dataset.makeCdn();
        dataset.describePlants();
        return dataset;
    }

    /// @return The options it was made with.
    Options options() {
        return options;
    }

    /// @return Everyone, the scenario people first.
    List<Person> people() {
        return people;
    }

    /// @param id A person id.
    /// @return The person.
    Person person(final String id) {
        return people.stream().filter(person -> person.id().equals(id)).findFirst().orElseThrow();
    }

    /// @return Every event, in time order (then by source and person), each numbered from 1 within its source.
    List<Act> acts() {
        return acts;
    }

    /// @param source A source.
    /// @return Its events, in time order.
    List<Act> acts(final Source source) {
        return acts.stream().filter(act -> act.source() == source).toList();
    }

    /// The events done to people by others (a group granted, a password reset, an MFA device added, failed logons
    /// against an account), drawn from a stream of their own so every event of [#acts] is the same with or without
    /// them: each with its actor (a person, the service desk's account of no person, or none for a failed logon) and
    /// whom it was done to (`target_person`, `target_account`), numbered on from the last of its source's [#acts].
    ///
    /// @return Them, in time order.
    List<Act> targetActs() {
        return targetActs;
    }

    /// @param source A source.
    /// @return Its events done to people by others, in time order.
    List<Act> targetActs(final Source source) {
        return targetActs.stream().filter(act -> act.source() == source).toList();
    }

    /// The background people's ordinary archives and personal cloud use, drawn from a stream of their own so every
    /// event of [#acts] and [#targetActs] is the same with or without them: archives of their work, personal cloud
    /// page views and small uploads (under 10 MB), uploads over 10 MB with no archive that day, and a few near
    /// misses of the leaver's archive-then-upload ([#knownAnswer]'s `near_misses`). Numbered on from the last of
    /// their source's [#acts] and [#targetActs].
    ///
    /// @return Them, in time order.
    List<Act> noiseActs() {
        return noiseActs;
    }

    /// @param source A source.
    /// @return Its noise events, in time order.
    List<Act> noiseActs(final Source source) {
        return noiseActs.stream().filter(act -> act.source() == source).toList();
    }

    /// Everyone's browser fetching from a content delivery network, drawn from a stream of its own so every other
    /// event is the same with or without it: on each day a person has proxy events, a few fetches of its common
    /// host ([#CDN_HOST]) and, most days, one of a subdomain of its own (`e<hex>.cdn-edge.example`), each at one of
    /// the person's own proxy events' times (so their hours are the same), with its tunnel from the pipeline change
    /// on. The subdomains are each held by one person: rare by name, common by service ([#knownAnswer]'s `cdn`:
    /// never flagged rare). Numbered on from the last of their source's [#acts], [#targetActs] and [#noiseActs].
    ///
    /// @return Them, in time order.
    List<Act> cdnActs() {
        return cdnActs;
    }

    /// @param source A source.
    /// @return Its content delivery network events, in time order.
    List<Act> cdnActs(final Source source) {
        return cdnActs.stream().filter(act -> act.source() == source).toList();
    }

    /// The known answer: the people (their ids, accounts, organisation, dates), each planted behaviour and decoy with
    /// its dates and what it left, the one-off anomalies, the approved exceptions, and the scenario people's tasks
    /// by day (what they were working on, for the evals).
    ///
    /// @return It, as JSON takes it.
    Map<String, Object> knownAnswer() {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("seed", options.seed());
        map.put("scale", options.scale());
        map.put("background_people", options.background());
        map.put("time_zone", ZONE.getId());
        map.put("from", FIRST_DAY.toString());
        map.put("to", END_DAY.minusDays(1).toString());
        final List<Map<String, Object>> listed = new ArrayList<>();
        for (final Person person : people) {
            final Map<String, Object> one = new LinkedHashMap<>();
            one.put("person_id", person.id());
            one.put("name", person.first() + " " + person.last());
            one.put("organisation", person.organisation());
            one.put("scenario", person.scenario());
            one.put("admin", person.admin());
            final Map<String, Object> logons = new LinkedHashMap<>();
            for (final Source source : Source.values()) {
                logons.put(source.key(), person.logons(source));
            }
            one.put("logons", logons);
            one.put("hosts", person.hosts());
            one.put("start", person.start().toString());
            if (person.leaving() != null) {
                one.put("leaving", person.leaving().toString());
            }
            one.put("leave", person.leave().stream().map(Period::asList).toList());
            listed.add(one);
        }
        map.put("people", listed);
        map.put("plants", plants);
        map.put("decoys", decoys);
        map.put("one_offs", oneOffs);
        map.put("exceptions", decoys.stream().filter(decoy -> decoy.containsKey("exception"))
                .map(decoy -> Map.of("person_id", decoy.get("person_id"), "what", decoy.get("exception"), "from",
                        decoy.get("from"), "to", decoy.get("to")))
                .toList());
        map.put("tasks", tasks);
        map.put("targets", targetPlants);
        map.put("target_noise", targetNoise);
        map.put("target_events", targetActs.size());
        map.put("near_misses", nearMisses);
        map.put("noise_events", noiseActs.size());
        map.put("cdn", Map.of("host", CDN_HOST, "parent", CDN, "events", cdnActs.size(), "what", "every person's "
                                                                                             + "browser's content "
                                                                                             + "delivery network: "
                                                                                             + "its subdomains "
                                                                                             + "are rare by name "
                                                                                             + "only, never to be "
                                                                                             + "flagged rare"));
        final Map<String, Object> counts = new LinkedHashMap<>();
        for (final Source source : Source.values()) {
            counts.put(source.key(), acts.stream().filter(act -> act.source() == source).count());
        }
        map.put("events", counts);
        return map;
    }

    /// The reference data the dev node decorates events with: each account, as a source writes it, to its person
    /// and `Organisation` (an organisation the role-change person moved to applies from that day).
    ///
    /// @return The rows, a header first: `account,source,person_id,organisation,from`.
    List<List<String>> reference() {
        final List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("account", "source", "person_id", "organisation", "from"));
        for (final Person person : people) {
            for (final Source source : Source.values()) {
                for (final String logon : person.logons(source)) {
                    rows.add(List.of(logon, source.key(), person.id(), person.organisationOn(FIRST_DAY),
                            FIRST_DAY.toString()));
                    if (person.movedTo() != null) {
                        rows.add(List.of(logon, source.key(), person.id(), person.movedTo(),
                                ROLE_CHANGE_DAY.toString()));
                    }
                }
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------------------------------------
    // People

    private void makePeople() {
        people.add(new Person(LEAVER, "Dana", "Whitfield", FINANCE, null, "leaver", false, FINANCE,
                FIRST_DAY.minusYears(3), LEAVING, List.of(), 8 * 60 + 30, Set.of(DayOfWeek.FRIDAY)));
        people.add(new Person(ADMIN, "Marcus", "Oyelaran", OPERATIONS, null, "disgruntled admin", true,
                OPERATIONS, FIRST_DAY.minusYears(6), null, List.of(), 8 * 60, Set.of(DayOfWeek.MONDAY)));
        people.add(new Person(VIEWER, "Priya", "Raman", FINANCE, null, "curious viewer", false, FINANCE,
                FIRST_DAY.minusYears(2), null, List.of(), 9 * 60, Set.of(DayOfWeek.WEDNESDAY)));
        people.add(new Person(COMPROMISED, "Tom", "Bakker", ENGINEERING, null, "compromised account", false,
                ENGINEERING, FIRST_DAY.minusYears(4), null, List.of(new Period(LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 4, 17))), 9 * 60 + 15, Set.of(DayOfWeek.THURSDAY)));
        people.add(new Person(ROLE_CHANGE, "Alex", "Chen", ENGINEERING, FINANCE, "role change", false, ENGINEERING,
                FIRST_DAY.minusYears(1), null, List.of(), 10 * 60, Set.of(DayOfWeek.TUESDAY)));
        people.add(new Person(DRIFT, "Sam", "Okafor", ENGINEERING, null, "drift to later hours", false,
                ENGINEERING, FIRST_DAY.minusYears(5), null, List.of(new Period(LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 16))), 8 * 60 + 30, Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)));
        // The background: the decoys' and one-offs' people first, so a reduced dataset keeps them.
        for (int i = 0; i < options.background(); i++) {
            final String organisation = i < 5
                    ? List.of(OPERATIONS, OPERATIONS, ENGINEERING, FINANCE, ENGINEERING).get(i)
                    : ORGANISATIONS.get(i % 3);
            final boolean admin = i == 1 || (OPERATIONS.equals(organisation) && i % 6 == 2);
            final int start = 7 * 60 + 45 + (i * 37) % 120;
            final Set<DayOfWeek> remote = Set.of(DayOfWeek.of(1 + (i % 5)));
            people.add(new Person("P2%04d".formatted(i + 1), FIRST_NAMES.get(i), LAST_NAMES.get(i), organisation,
                    null, null, admin, organisation, FIRST_DAY.minusYears(1 + i % 4), null, i % 7 == 3
                    ? List.of(new Period(LocalDate.of(2026, 7, 6).plusWeeks(i % 5),
                    LocalDate.of(2026, 7, 10).plusWeeks(i % 5)))
                    : List.of(), start, remote));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Events

    private void makeActs() {
        for (int p = 0; p < people.size(); p++) {
            final Person person = people.get(p);
            for (LocalDate day = FIRST_DAY; day.isBefore(END_DAY); day = day.plusDays(1)) {
                if (options.keeps(day)) {
                    new DayMaker(person, p, day).make();
                }
            }
        }
        acts.sort(Comparator.comparingLong(Act::time).thenComparing(Act::source).thenComparing(Act::personId)
                .thenComparing(Act::type));
        final Map<Source, Integer> numbers = new LinkedHashMap<>();
        final List<Act> numbered = new ArrayList<>(acts.size());
        for (final Act act : acts) {
            final int number = numbers.merge(act.source(), 1, Integer::sum);
            numbered.add(act.numbered(number));
        }
        acts.clear();
        acts.addAll(numbered);
    }

    // The events done to people by others, a stream of their own (its own seeded draw a person-day, so the reduced
    // form keeps exactly the full form's on its days): the leaver's privileged group granted by an administrator,
    // the compromised account's takeover (failed logons against it, then a reset and an MFA device added by the
    // service desk's account), and the service desk's routine resets of background people's passwords as noise.
    private void makeTargets() {
        final List<Act> made = new ArrayList<>();
        for (int p = 0; p < people.size(); p++) {
            final Person person = people.get(p);
            for (LocalDate day = FIRST_DAY; day.isBefore(END_DAY); day = day.plusDays(1)) {
                if (!options.keeps(day) || day.isBefore(person.start())
                    || (person.leaving() != null && day.isAfter(person.leaving()))) {
                    continue;
                }
                final SeededRandom random = new SeededRandom(Math.floorMod(options.seed() * 7_000_003L + p * 10_009L
                                                                           + day.toEpochDay() * 37L + 5_848L,
                        4_294_967_296L));
                final long midnight = ZonedDateTime.of(day, LocalTime.MIDNIGHT, ZONE).toInstant().toEpochMilli();
                if (LEAVER.equals(person.id()) && GRANT_DAY.equals(day)
                    && people.stream().anyMatch(one -> one.id().equals(GRANTING_ADMIN))) {
                    final Person admin = person(GRANTING_ADMIN);
                    made.add(targetAct(midnight + (10 * 60 + random.randint(30, 50)) * (long) MINUTE,
                            admin.id(), admin.logon(Source.CLOUD), admin.organisationOn(day), "group.add", person,
                            Map.of("group", PRIVILEGED_GROUP, "app", "Directory")));
                }
                if (COMPROMISED.equals(person.id()) && TAKEOVER_DAY.equals(day)) {
                    final String ip = "185.220.101." + random.randint(10, 200);
                    final long first = midnight + (60 + random.randint(15, 25)) * (long) MINUTE;
                    for (int i = 0; i < 6; i++) {
                        made.add(targetAct(first + i * 50_000L, null, null, null, "signin.failed", person,
                                Map.of("country", "RO", "ip", ip, "reason", "Invalid password")));
                    }
                    made.add(targetAct(first + 12L * MINUTE, null, SERVICE_DESK, null, "password.reset", person,
                            Map.of("app", "Directory")));
                    made.add(targetAct(first + 16L * MINUTE, null, SERVICE_DESK, null, "mfa.add", person,
                            Map.of("device", MFA_DEVICE, "app", "Directory")));
                }
                final boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY
                                        || day.getDayOfWeek() == DayOfWeek.SUNDAY;
                if (person.scenario() == null && !weekend && !person.onLeave(day) && random.random() < 0.02) {
                    made.add(targetAct(midnight + (9 * 60 + random.randint(0, 420)) * (long) MINUTE, null,
                            SERVICE_DESK, null, "password.reset", person, Map.of("app", "Directory")));
                    targetNoise.add(Map.of("person_id", person.id(), "date", day.toString(), "what",
                            "a password reset by the service desk"));
                }
            }
        }
        made.sort(Comparator.comparingLong(Act::time).thenComparing(Act::source).thenComparing(Act::type)
                .thenComparing(act -> act.field("target_person")));
        final Map<Source, Integer> numbers = new LinkedHashMap<>();
        for (final Source source : Source.values()) {
            numbers.put(source, (int) acts.stream().filter(act -> act.source() == source).count());
        }
        for (final Act act : made) {
            targetActs.add(act.numbered(numbers.merge(act.source(), 1, Integer::sum)));
        }
    }

    // The background people's archives and personal cloud use, a stream of their own (its own seeded draw a
    // person-day): on working days, an archive of their work (now and then followed by a small upload), personal
    // cloud page views most days, small uploads, and now and then an upload over 10 MB with no archive that day (a
    // photo backup); and three near misses of the leaver's archive-then-upload, on days no other noise of theirs is
    // made: an archive then an upload over 10 MB 70 to 90 minutes later, an upload over 10 MB with no archive, and
    // an archive by one person with an upload over 10 MB by another within the hour.
    private void makeNoise() {
        final List<Act> made = new ArrayList<>();
        final Map<String, LocalDate> missDays = Map.of("P20003", LocalDate.of(2026, 7, 14), "P20004",
                LocalDate.of(2026, 8, 5), "P20005", LocalDate.of(2026, 9, 2), "P20001", LocalDate.of(2026, 9, 2));
        for (int p = 0; p < people.size(); p++) {
            final Person person = people.get(p);
            if (person.scenario() != null) {
                continue;
            }
            for (LocalDate day = FIRST_DAY; day.isBefore(END_DAY); day = day.plusDays(1)) {
                final boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY
                                        || day.getDayOfWeek() == DayOfWeek.SUNDAY;
                if (!options.keeps(day) || weekend || person.onLeave(day) || day.isBefore(person.start())) {
                    continue;
                }
                final SeededRandom random = new SeededRandom(Math.floorMod(options.seed() * 9_000_011L + p * 10_037L
                                                                           + day.toEpochDay() * 41L + 2_026L,
                        4_294_967_296L));
                final long midnight = ZonedDateTime.of(day, LocalTime.MIDNIGHT, ZONE).toInstant().toEpochMilli();
                if (day.equals(missDays.get(person.id()))) {
                    nearMiss(made, person, day, midnight, random);
                    continue;
                }
                final boolean bigUpload = random.random() < 0.02;
                if (!bigUpload && random.random() < 0.08) {
                    final long at = midnight + (10 * 60 + random.randint(0, 360)) * (long) MINUTE;
                    made.add(archive(person, day, at, random));
                    if (random.random() < 0.3) {
                        made.addAll(upload(person, day, at + random.randint(5, 50) * (long) MINUTE,
                                random.randint(50_000, 9_500_000)));
                    }
                }
                if (random.random() < 0.8) {
                    for (int i = random.randint(1, 5); i > 0; i--) {
                        made.addAll(cloudPage(person, day, midnight + (9 * 60 + random.randint(0, 480))
                                                                      * (long) MINUTE, random));
                    }
                }
                if (random.random() < 0.1) {
                    made.addAll(upload(person, day, midnight + (9 * 60 + random.randint(0, 480)) * (long) MINUTE,
                            random.randint(20_000, 9_000_000)));
                }
                if (bigUpload) {
                    made.addAll(upload(person, day, midnight + (12 * 60 + random.randint(0, 60)) * (long) MINUTE,
                            random.randint(12_000_000, 300_000_000)));
                }
            }
        }
        made.sort(Comparator.comparingLong(Act::time).thenComparing(Act::source).thenComparing(Act::personId)
                .thenComparing(Act::type));
        final Map<Source, Integer> numbers = new LinkedHashMap<>();
        for (final Source source : Source.values()) {
            numbers.put(source, (int) (acts.stream().filter(act -> act.source() == source).count()
                                       + targetActs.stream().filter(act -> act.source() == source).count()));
        }
        for (final Act act : made) {
            noiseActs.add(act.numbered(numbers.merge(act.source(), 1, Integer::sum)));
        }
    }

    // The content delivery network's fetches, on each person-day with proxy events, at those events' times.
    private void makeCdn() {
        final Map<String, Map<LocalDate, List<Act>>> proxyDays = new LinkedHashMap<>();
        for (final Act act : acts) {
            if (act.source() == Source.PROXY && act.personId() != null) {
                proxyDays.computeIfAbsent(act.personId(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(Instant.ofEpochMilli(act.time()).atZone(ZONE).toLocalDate(),
                                k -> new ArrayList<>()).add(act);
            }
        }
        final List<Act> made = new ArrayList<>();
        for (int p = 0; p < people.size(); p++) {
            final Person person = people.get(p);
            for (final Map.Entry<LocalDate, List<Act>> day : proxyDays.getOrDefault(person.id(), Map.of())
                    .entrySet()) {
                final SeededRandom random = new SeededRandom(Math.floorMod(options.seed() * 7_000_003L
                                                                           + p * 10_039L
                                                                           + day.getKey().toEpochDay() * 43L
                                                                           + 777L, 4_294_967_296L));
                for (int i = random.randint(2, 5); i > 0; i--) {
                    made.addAll(cdn(random.choice(day.getValue()), CDN_HOST, day.getKey(), random));
                }
                if (random.random() < 0.6) {
                    final String sub = "e%06x.".formatted(random.randint(0, 0xFFFFFF)) + CDN;
                    made.addAll(cdn(random.choice(day.getValue()), sub, day.getKey(), random));
                }
            }
        }
        made.sort(Comparator.comparingLong(Act::time).thenComparing(Act::source).thenComparing(Act::personId)
                .thenComparing(Act::type));
        final Map<Source, Integer> numbers = new LinkedHashMap<>();
        for (final Source source : Source.values()) {
            numbers.put(source, (int) (acts.stream().filter(act -> act.source() == source).count()
                                       + targetActs.stream().filter(act -> act.source() == source).count()
                                       + noiseActs.stream().filter(act -> act.source() == source).count()));
        }
        for (final Act act : made) {
            cdnActs.add(act.numbered(numbers.merge(act.source(), 1, Integer::sum)));
        }
    }

    // A fetch from the content delivery network a moment after one of the person's own proxy events, with its
    // tunnel from the pipeline change on.
    private static List<Act> cdn(final Act beside, final String host, final LocalDate day, final SeededRandom random) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("domain", host);
        fields.put("url", "https://" + host + "/" + random.choice(List.of("app.js", "fonts.css", "logo.svg")));
        fields.put("category", CDN_CATEGORY);
        fields.put("method", "GET");
        fields.put("bytes_out", String.valueOf(random.randint(300, 900)));
        fields.put("bytes_in", String.valueOf(random.randint(5_000, 200_000)));
        fields.put("ip", "203.0.113." + (1 + Math.floorMod(host.hashCode(), 250)));
        fields.put("process", "chrome.exe");
        fields.put("access", beside.field("access") == null
                ? "Office"
                : beside.field("access"));
        final long at = beside.time() + random.randint(1, 20) * 1_000L;
        final List<Act> out = new ArrayList<>();
        out.add(new Act(at, Source.PROXY, beside.personId(), beside.account(), beside.host(), "web", fields,
                beside.organisation(), 0));
        if (!day.isBefore(PIPELINE_CHANGE)) {
            final Map<String, String> tunnel = new LinkedHashMap<>(fields);
            tunnel.put("method", "CONNECT");
            tunnel.put("url", host + ":443");
            tunnel.put("bytes_out", "0");
            tunnel.put("bytes_in", "0");
            out.add(new Act(at - 200, Source.PROXY, beside.personId(), beside.account(), beside.host(), "web",
                    tunnel, beside.organisation(), 0));
        }
        return out;
    }

    // One near miss: the archive and the upload too far apart, the upload alone, or each by another person.
    private void nearMiss(final List<Act> made,
                          final Person person,
                          final LocalDate day,
                          final long midnight,
                          final SeededRandom random) {
        final long at = midnight + (11 * 60 + random.randint(0, 30)) * (long) MINUTE;
        switch (person.id()) {
            case "P20003" -> {
                final int gap = random.randint(70, 90);
                made.add(archive(person, day, at, random));
                made.addAll(upload(person, day, at + gap * (long) MINUTE, random.randint(20_000_000, 90_000_000)));
                nearMisses.add(Map.of("person_id", person.id(), "date", day.toString(), "what", "an archive, then "
                                                                                                + "an upload over 10 "
                                                                                                + "MB " + gap
                                                                                                + " minutes later"));
            }
            case "P20004" -> {
                made.addAll(upload(person, day, at, random.randint(20_000_000, 90_000_000)));
                nearMisses.add(Map.of("person_id", person.id(), "date", day.toString(), "what", "an upload over 10 "
                                                                                                + "MB with no archive "
                                                                                                + "before it"));
            }
            case "P20001" -> {
                made.add(archive(person, day, at, random));
                nearMisses.add(Map.of("person_id", person.id(), "date", day.toString(), "what", "an archive, and "
                                                                                                + "P20005's upload "
                                                                                                + "over 10 MB within "
                                                                                                + "the hour"));
            }
            default -> made.addAll(upload(person, day, midnight + (11 * 60 + 40) * (long) MINUTE,
                    random.randint(20_000_000, 90_000_000)));
        }
    }

    // An archive of a person's work on their host.
    private static Act archive(final Person person, final LocalDate day, final long at, final SeededRandom random) {
        final String name = random.choice(List.of("project-notes", "q3-working-files", "photos", "reports",
                "drafts")) + "-" + day + ".zip";
        return new Act(at, Source.ENDPOINT, person.id(), person.logon(Source.ENDPOINT), person.hostOn(day),
                "file.archive", Map.of("path", "C:\\Users\\" + person.shortName() + "\\Documents\\" + name,
                "name", name, "size", String.valueOf(random.randint(1_000_000, 80_000_000)), "hash", hash(name),
                "files", String.valueOf(random.randint(5, 120))), person.organisationOn(day), 0);
    }

    // A personal cloud page view.
    private static List<Act> cloudPage(final Person person,
                                       final LocalDate day,
                                       final long at,
                                       final SeededRandom random) {
        return proxy(person, day, at, "/" + random.choice(List.of("", "home", "view", "recent")), "GET",
                random.randint(300, 4_000));
    }

    // An upload to personal cloud of so many bytes.
    private static List<Act> upload(final Person person, final LocalDate day, final long at, final int bytes) {
        return proxy(person, day, at, "/upload", "POST", bytes);
    }

    // A proxy event to the personal cloud domain, with its tunnel from the pipeline change on.
    private static List<Act> proxy(final Person person,
                                   final LocalDate day,
                                   final long at,
                                   final String path,
                                   final String method,
                                   final int bytesOut) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("domain", PERSONAL_CLOUD);
        fields.put("url", "https://" + PERSONAL_CLOUD + path);
        fields.put("category", PERSONAL_CLOUD_CATEGORY);
        fields.put("method", method);
        fields.put("bytes_out", String.valueOf(bytesOut));
        fields.put("bytes_in", "POST".equals(method)
                ? "1200"
                : "45000");
        fields.put("ip", "203.0.113." + (1 + Math.floorMod(PERSONAL_CLOUD.hashCode(), 250)));
        fields.put("process", "chrome.exe");
        fields.put("access", "Office");
        final List<Act> out = new ArrayList<>();
        out.add(new Act(at, Source.PROXY, person.id(), person.logon(Source.PROXY), person.hostOn(day), "web",
                fields, person.organisationOn(day), 0));
        if (!day.isBefore(PIPELINE_CHANGE)) {
            final Map<String, String> tunnel = new LinkedHashMap<>(fields);
            tunnel.put("method", "CONNECT");
            tunnel.put("url", PERSONAL_CLOUD + ":443");
            tunnel.put("bytes_out", "0");
            tunnel.put("bytes_in", "0");
            out.add(new Act(at - 200, Source.PROXY, person.id(), person.logon(Source.PROXY), person.hostOn(day),
                    "web", tunnel, person.organisationOn(day), 0));
        }
        return out;
    }

    // A cloud event done to a person by an actor (null fields for none), whom it was done to in its fields.
    private static Act targetAct(final long time,
                                 final String actorId,
                                 final String actorAccount,
                                 final String actorOrganisation,
                                 final String type,
                                 final Person target,
                                 final Map<String, String> more) {
        final Map<String, String> fields = new LinkedHashMap<>(more);
        fields.put("target_person", target.id());
        fields.put("target_account", target.logon(Source.CLOUD));
        return new Act(time, Source.CLOUD, actorId, actorAccount, null, type, fields, actorOrganisation, 0);
    }

    // One person's day: their working pattern, the scenario's plants on it, and the decoys'.
    private final class DayMaker {

        private final Person person;
        private final int index;
        private final LocalDate day;
        private final SeededRandom random;
        private final String host;
        private final Map<String, Integer> pids = new LinkedHashMap<>();
        private final Map<String, String> handles = new LinkedHashMap<>();
        private final List<Map<String, Object>> dayTasks = new ArrayList<>();
        private String focused;
        private String vpnSession;
        private int sessions;

        private DayMaker(final Person person, final int index, final LocalDate day) {
            this.person = person;
            this.index = index;
            this.day = day;
            this.random = new SeededRandom(Math.floorMod(options.seed() * 1_000_003L + index * 10_007L
                                                         + day.toEpochDay() * 31L, 4_294_967_296L));
            this.host = person.hostOn(day);
        }

        private void make() {
            if (day.isBefore(person.start()) || (person.leaving() != null && day.isAfter(person.leaving()))) {
                return;
            }
            final boolean onLeave = person.onLeave(day);
            if (COMPROMISED.equals(person.id()) && onLeave) {
                compromised();
                return;
            }
            if (onLeave) {
                return;
            }
            final boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY
                                    || day.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (weekend) {
                if (random.random() < 0.04 || oneOffWeekend()) {
                    workBlock(minutes(10 * 60 + random.randint(0, 120)), random.randint(20, 60), true, false);
                }
                return;
            }
            workday();
            if (!dayTasks.isEmpty() && isScenario()) {
                tasks.computeIfAbsent(person.id(), id -> new LinkedHashMap<>()).put(day.toString(), dayTasks);
            }
        }

        private boolean isScenario() {
            return person.scenario() != null;
        }

        private void workday() {
            final int start = startMinute();
            final int length = 8 * 60 + random.randint(-30, 30);
            final int lunch = start + 4 * 60 - 30 + random.randint(-20, 20);
            final int lunchLength = random.randint(30, 50);
            final int end = start + length;
            final boolean remote = person.remoteDays().contains(day.getDayOfWeek()) || DRIFT.equals(person.id())
                                                                                       && random.random() < 0.2;
            final long startMs = minutes(start);
            if (remote) {
                vpnSession = vpnLogin(startMs - 2 * MINUTE, "AU", homeIp());
            }
            cloud(startMs + MINUTE, "signin", Map.of("country", "AU", "ip", remote
                    ? homeIp()
                    : officeIp(), "app", "Office 365"));
            endpoint(startMs, "logon", Map.of());
            plantsAtStart(startMs);
            tasksBetween(start + 1, lunch);
            endpoint(minutes(lunch), "lock", Map.of());
            endpoint(minutes(lunch + lunchLength), "unlock", Map.of());
            // On an upload day the leaver's tasks stop early, for the archive and the upload before logging off.
            tasksBetween(lunch + lunchLength + 1, LEAVER.equals(person.id()) && UPLOAD_DAYS.contains(day)
                    ? end - 45
                    : end);
            plantsAtEnd(end);
            closeAll(minutes(end) - MINUTE);
            endpoint(minutes(end), "logoff", Map.of());
            if (vpnSession != null) {
                vpnLogout(minutes(end) + MINUTE, vpnSession);
                vpnSession = null;
            }
            evening(end);
        }

        // The minute of the day work starts: the person's own, shifted by their scenario.
        private int startMinute() {
            int start = person.startMinute();
            if (ROLE_CHANGE.equals(person.id()) && !day.isBefore(ROLE_CHANGE_DAY)) {
                start = 8 * 60;
            }
            if (DRIFT.equals(person.id())) {
                final LocalDate from = LocalDate.of(2026, 5, 1);
                final LocalDate to = LocalDate.of(2026, 8, 31);
                if (day.isAfter(to)) {
                    start = 11 * 60;
                } else if (!day.isBefore(from)) {
                    final double share = (double) ChronoUnit.DAYS.between(from, day) / ChronoUnit.DAYS.between(from,
                            to);
                    start = (int) Math.round(8 * 60 + 30 + share * 150);
                }
            }
            return start + random.randint(-15, 15);
        }

        private void tasksBetween(final int from, final int to) {
            int at = from;
            Task previous = null;
            while (at < to - 10) {
                final Task task = previous != null && random.random() < 0.3
                        ? previous
                        : random.choice(catalogue());
                final int length = Math.min(random.randint(20, 180), to - at);
                workOn(task, at, at + length);
                previous = task;
                at += length + random.randint(0, 5);
            }
        }

        private List<Task> catalogue() {
            final String role = ROLE_CHANGE.equals(person.id()) && !day.isBefore(ROLE_CHANGE_DAY)
                    ? FINANCE
                    : person.role();
            return Task.forRole(role);
        }

        // A task: its application started and focused, actions every few minutes, interruptions (mail, a chat)
        // and a return to the task after each.
        private void workOn(final Task task, final int from, final int to) {
            final Map<String, Object> logged = new LinkedHashMap<>();
            logged.put("start", iso(minutes(from)));
            logged.put("end", iso(minutes(to)));
            logged.put("task", task.label());
            logged.put("application", task.application());
            dayTasks.add(logged);
            focus(task.application(), task.title(random, day), minutes(from));
            int at = from + 1;
            while (at < to) {
                final double draw = random.random();
                if (draw < 0.08 && to - at > 15) {
                    // Interrupted: mail for a few minutes, then back to the task.
                    final int back = at + random.randint(2, 10);
                    focus(Task.MAIL.application(), Task.MAIL.title(random, day), minutes(at));
                    endpoint(minutes(at) + 20_000, "file.read", file(Task.MAIL.files(), 40_000));
                    focus(task.application(), task.title(random, day), minutes(back));
                    at = back + 1;
                    continue;
                }
                // Within the minute's first half, so an action's look-up is back before the next action.
                actOn(task, minutes(at) + random.randint(0, 30) * 1_000L);
                at += Math.max(1, (int) Math.round(random.randint(2, 6) / options.scale()));
            }
        }

        private void actOn(final Task task, final long time) {
            if (task.browser()) {
                web(time, random.choice(task.domains()), task.category(), random.randint(400, 6_000), false);
            } else if (!task.files().isEmpty() && random.random() < 0.5) {
                endpoint(time, random.random() < 0.7
                        ? "file.read"
                        : "file.write", file(task.files(), 2_000_000));
            } else if (!task.domains().isEmpty()) {
                // A look-up in the browser, then back.
                focus(Task.BROWSER, "Search - Chrome", time);
                web(time + 5_000, random.choice(task.domains()), task.category(), random.randint(400, 4_000),
                        false);
                focus(task.application(), task.title(random, day), time + 20_000);
            }
            if (task.files().size() > 0 && random.random() < 0.05) {
                cloud(time + 2_000, "download", Map.of("file", random.choice(task.files()), "site",
                        task.site(), "size", String.valueOf(random.randint(20_000, 3_000_000))));
            }
            if (FINANCE.equals(person.organisationOn(day)) && random.random() < 0.06) {
                cloud(time + 3_000, "record.view", Map.of("record", "CUST-%05d".formatted(random.randint(1, 900)),
                        "app", "CRM"));
            }
            if (person.admin() && "Server maintenance".equals(task.label()) && random.random() < 0.15) {
                privileged(time, random.randint(10, 60), random.choice(Task.TARGETS));
            }
        }

        // A short block of work outside the usual hours (a weekend, an evening), the leaver's archive and upload at
        // its end where asked.
        private void workBlock(final long startMs, final int length, final boolean vpn, final boolean upload) {
            final String session = vpn
                    ? vpnLogin(startMs - MINUTE, "AU", homeIp())
                    : null;
            endpoint(startMs, "logon", Map.of());
            final int from = minuteOf(startMs) + 1;
            workOn(random.choice(catalogue()), from, from + length);
            long end = startMs + (length + 1L) * MINUTE;
            if (upload) {
                exfiltrate(end);
                end += 15L * MINUTE;
            }
            closeAll(end);
            endpoint(end + MINUTE, "logoff", Map.of());
            if (session != null) {
                vpnLogout(end + 2L * MINUTE, session);
            }
        }

        // ---- the plants and decoys, by person and day

        private void plantsAtStart(final long startMs) {
            final boolean leaverWindow = LEAVER.equals(person.id()) && !day.isBefore(LocalDate.of(2026, 7, 1));
            if (leaverWindow && random.random() < 0.2 + 0.6 * share(LocalDate.of(2026, 7, 1), LEAVING)) {
                // Job-site browsing first thing, more often towards the leaving date.
                focus(Task.BROWSER, "Jobs - Chrome", startMs + 30_000);
                for (int i = 0; i < 4 + random.randint(0, 8); i++) {
                    web(startMs + (i + 1) * 45_000L, random.choice(List.of("jobs.seek-like.example",
                            "careers.jobboard.example")), JOB_CATEGORY, random.randint(800, 4_000), false);
                }
            }
            if (LEAVER.equals(person.id()) && !day.isBefore(LocalDate.of(2026, 8, 10))) {
                // Breadth widening: sites outside the person's usual.
                for (int i = 0; i < 2 + random.randint(0, 4); i++) {
                    cloud(startMs + (5 + i) * MINUTE, "download", Map.of("file", random.choice(List.of(
                            "Board pack.pdf", "Salary bands 2026.xlsx", "Legal hold list.docx", "Org chart.vsdx",
                            "Pricing model.xlsx")), "site", random.choice(List.of("sites/hr", "sites/legal",
                            "sites/executive", "sites/sales")), "size", String.valueOf(random.randint(200_000,
                            9_000_000))));
                }
            }
            if (VIEWER.equals(person.id()) && !day.isBefore(LocalDate.of(2026, 6, 1))
                && day.isBefore(LocalDate.of(2026, 6, 27))) {
                // Record look-ups far above the person's peers, outside their caseload.
                final int lookups = 30 + random.randint(0, 30);
                for (int i = 0; i < lookups; i++) {
                    cloud(startMs + (10 + i * 7L) * MINUTE + random.randint(0, 50) * 1_000L, "record.view",
                            Map.of("record", "CUST-%05d".formatted(random.randint(1, 20_000)), "app", "CRM"));
                }
            }
            if ("P20001".equals(person.id()) && !day.isBefore(LocalDate.of(2026, 5, 11))
                && !day.isAfter(LocalDate.of(2026, 5, 15))) {
                // The approved migration's bulk transfers (a decoy).
                for (int i = 0; i < 150; i++) {
                    cloud(startMs + (20 + i) * MINUTE, "download", Map.of("file", "archive-%04d.bak".formatted(i),
                            "site", "sites/operations", "size", String.valueOf(random.randint(50_000_000,
                                    200_000_000))));
                    web(startMs + (20 + i) * MINUTE + 30_000, "migration.vendor.example", "Business",
                            random.randint(50_000_000, 200_000_000), true);
                }
            }
            if ("P20004".equals(person.id()) && day.equals(LocalDate.of(2026, 7, 8))) {
                // A one-off: one day's large download.
                for (int i = 0; i < 80; i++) {
                    cloud(startMs + (30 + i) * MINUTE, "download", Map.of("file", "Statement %03d.pdf".formatted(i),
                            "site", "sites/finance", "size", String.valueOf(random.randint(100_000, 900_000))));
                }
            }
        }

        private void plantsAtEnd(final int end) {
            if (!LEAVER.equals(person.id())) {
                return;
            }
            final long at = minutes(end - 40);
            if (UPLOAD_DAYS.contains(day)) {
                exfiltrate(at);
            }
            if (!day.isBefore(LocalDate.of(2026, 9, 8)) && !day.isAfter(LocalDate.of(2026, 9, 19))
                && day.getDayOfWeek().getValue() <= 5) {
                // Bulk downloads.
                final int count = 40 + random.randint(0, 80);
                for (int i = 0; i < count; i++) {
                    cloud(minutes(end - 120) + i * 50_000L, "download", Map.of("file", "Finance %04d.xlsx".formatted(
                            random.randint(1, 5_000)), "site", random.choice(List.of("sites/finance",
                            "sites/finance-archive", "sites/executive")), "size", String.valueOf(random.randint(
                            100_000, 5_000_000))));
                }
            }
            if (day.equals(LocalDate.of(2026, 9, 23))) {
                endpoint(at, "usb.insert", Map.of("device", "Kingston DataTraveler 64GB", "device_id",
                        "USB\\VID_0951&PID_1666\\0019E06B9C85"));
                for (int i = 0; i < 12; i++) {
                    final String name = "Client list %02d.xlsx".formatted(i);
                    endpoint(at + (i + 1) * 20_000L, "usb.copy", Map.of("path", "C:\\Users\\dwhitfield\\Documents\\"
                                                                                + name, "name", name, "to",
                            "E:\\" + name, "size", String.valueOf(random.randint(100_000, 900_000))));
                }
            }
        }

        // Archive, then upload to personal cloud within the hour (a sequence).
        private void exfiltrate(final long at) {
            final String archive = "handover-" + day + ".zip";
            endpoint(at, "file.archive", Map.of("path", "C:\\Users\\dwhitfield\\Documents\\" + archive, "name",
                    archive, "size", String.valueOf(random.randint(50_000_000, 400_000_000)), "hash", hash(archive),
                    "files", String.valueOf(random.randint(80, 400))));
            focus(Task.BROWSER, "Upload - My Drive - Chrome", at + 5 * MINUTE);
            web(at + 6 * MINUTE + random.randint(0, 300) * 1_000L, PERSONAL_CLOUD, PERSONAL_CLOUD_CATEGORY,
                    random.randint(50_000_000, 400_000_000), true);
        }

        private void evening(final int end) {
            if (LEAVER.equals(person.id()) && Set.of(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 17))
                    .contains(day)) {
                // Out of hours, over the VPN.
                final long at = minutes(20 * 60 + random.randint(0, 60));
                workBlock(at, random.randint(60, 100), true, true);
            }
            if (ADMIN.equals(person.id()) && Set.of(LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 25),
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)).contains(day)) {
                nightAdmin(day.equals(LocalDate.of(2026, 9, 3)));
            }
            if ("P20002".equals(person.id()) && !day.isBefore(LocalDate.of(2026, 7, 20))
                && !day.isAfter(LocalDate.of(2026, 7, 24))) {
                // On call: out-of-hours privileged sessions, approved (a decoy).
                final long at = minutes(21 * 60 + random.randint(0, 120));
                final String session = vpnLogin(at - MINUTE, "AU", homeIp());
                privileged(at, random.randint(20, 70), random.choice(Task.TARGETS));
                vpnLogout(at + 90L * MINUTE, session);
            }
            if ("P20005".equals(person.id()) && day.equals(LocalDate.of(2026, 10, 7))) {
                // A one-off: late-night browsing.
                final long at = minutes(23 * 60 + 20);
                for (int i = 0; i < 25; i++) {
                    web(at + i * 70_000L, random.choice(List.of("news.example.com", "forum.example.org")), "News",
                            random.randint(500, 3_000), false);
                }
            }
        }

        // The disgruntled admin's night: privileged sessions over the VPN; on the last, a tool never used before
        // and the security log cleared.
        private void nightAdmin(final boolean last) {
            final long at = minutes(23 * 60 + random.randint(0, 30));
            final String session = vpnLogin(at - 2 * MINUTE, "AU", homeIp());
            endpoint(at, "logon", Map.of("admin", "true"));
            final int length = random.randint(70, 130);
            privileged(at + MINUTE, length, "srv-db-01.corp.example");
            if (last) {
                // Before midnight: the session starts by 23:30.
                endpoint(at + 20L * MINUTE, "proc.start", Map.of("pid", "6602", "command",
                        "C:\\Tools\\" + NEW_TOOL + " -all -out \\\\srv-db-01\\c$\\temp\\d.bin", "admin", "true"));
                endpoint(at + 55L * MINUTE, "proc.stop", Map.of("pid", "6602", "command",
                        "C:\\Tools\\" + NEW_TOOL, "admin", "true"));
                endpoint(at + (length - 5L) * MINUTE, "log.clear", Map.of("log", "Security", "admin", "true"));
            }
            endpoint(at + (length + 2L) * MINUTE, "logoff", Map.of("admin", "true"));
            vpnLogout(at + (length + 4L) * MINUTE, session);
        }

        // The compromised account, while its person is on leave: sign-ins from a new country at night, downloads,
        // a VPN login from there.
        private void compromised() {
            if (!Set.of(LocalDate.of(2026, 4, 8), LocalDate.of(2026, 4, 9), LocalDate.of(2026, 4, 13),
                    LocalDate.of(2026, 4, 14)).contains(day)) {
                return;
            }
            final long at = minutes(2 * 60 + random.randint(0, 90));
            final String ip = "185.220.101." + random.randint(10, 200);
            cloud(at, "signin", Map.of("country", "RO", "ip", ip, "app", "Office 365"));
            for (int i = 0; i < 20 + random.randint(0, 30); i++) {
                cloud(at + (i + 2) * 40_000L, "download", Map.of("file", random.choice(List.of("Architecture.pptx",
                        "Payments API keys.txt", "Roadmap 2027.docx", "Incident review.docx")), "site",
                        "sites/engineering", "size", String.valueOf(random.randint(100_000, 8_000_000))));
            }
            if (day.equals(LocalDate.of(2026, 4, 9))) {
                final String session = vpnLogin(at + 30L * MINUTE, "RO", ip);
                vpnLogout(at + 75L * MINUTE, session);
            }
        }

        private boolean oneOffWeekend() {
            return "P20003".equals(person.id()) && day.equals(LocalDate.of(2026, 2, 14));
        }

        private double share(final LocalDate from, final LocalDate to) {
            return Math.max(0, Math.min(1, (double) ChronoUnit.DAYS.between(from, day)
                                           / ChronoUnit.DAYS.between(from, to)));
        }

        // ---- the events

        private void focus(final String application, final String title, final long time) {
            if (focused != null && !focused.equals(application)) {
                endpoint(time - 1_000, "focus.out", Map.of("application", focused, "window",
                        handles.get(focused)));
            }
            if (!pids.containsKey(application)) {
                final int pid = 1_000 + random.randint(0, 60_000);
                pids.put(application, pid);
                handles.put(application, "0x%06x".formatted(random.randint(0x10000, 0xffffff)));
                endpoint(time - 2_000, "proc.start", Map.of("pid", String.valueOf(pid), "command",
                        Task.command(application)));
            }
            if (!application.equals(focused)) {
                endpoint(time, "focus.in", Map.of("application", application, "window", handles.get(application),
                        "title", title));
            }
            focused = application;
        }

        private void closeAll(final long time) {
            if (focused != null) {
                endpoint(time - 2_000, "focus.out", Map.of("application", focused, "window", handles.get(focused)));
            }
            long at = time;
            for (final Map.Entry<String, Integer> process : pids.entrySet()) {
                endpoint(at, "proc.stop", Map.of("pid", String.valueOf(process.getValue()), "command",
                        Task.command(process.getKey())));
                at += 1_000;
            }
            pids.clear();
            handles.clear();
            focused = null;
        }

        private Map<String, String> file(final List<String> names, final int most) {
            final String name = random.choice(names).replace("{month}", day.getMonth().getDisplayName(
                    java.time.format.TextStyle.FULL, Locale.ENGLISH));
            return Map.of("path", "\\\\fs01\\" + person.organisationOn(day).toLowerCase(Locale.ROOT) + "\\" + name,
                    "name", name, "size", String.valueOf(random.randint(5_000, most)), "hash", hash(name));
        }

        private String vpnLogin(final long time, final String country, final String ip) {
            sessions++;
            final String session = String.valueOf(day.toEpochDay() % 1_000 * 100_000 + index * 100 + sessions);
            add(time, Source.VPN, person.logon(Source.VPN), "ns-gw0" + (1 + index % 2), "vpn.login",
                    Map.of("session", session, "ip", ip, "country", country));
            return session;
        }

        private void vpnLogout(final long time, final String session) {
            add(time, Source.VPN, person.logon(Source.VPN), "ns-gw0" + (1 + index % 2), "vpn.logout",
                    Map.of("session", session, "ip", homeIp(), "bytes_out", String.valueOf(random.randint(1_000_000,
                            90_000_000)), "bytes_in", String.valueOf(random.randint(1_000_000, 300_000_000))));
        }

        private void privileged(final long time, final int length, final String target) {
            sessions++;
            final String session = String.valueOf(70_000_000 + day.toEpochDay() % 1_000 * 1_000 + index * 10
                                                  + sessions % 10);
            add(time - 30_000, Source.PAM, person.logon(Source.PAM), "ss-web-01", "pam.checkout",
                    Map.of("secret", target + " admin", "session", session));
            add(time, Source.PAM, person.logon(Source.PAM), "ss-web-01", "pam.session.start", Map.of("session",
                    session, "target", target, "protocol", target.startsWith("srv-db")
                            ? "SSH"
                            : "RDP"));
            add(time + (long) length * MINUTE, Source.PAM, person.logon(Source.PAM), "ss-web-01",
                    "pam.session.end", Map.of("session", session, "target", target, "seconds",
                            String.valueOf(length * 60)));
        }

        private void endpoint(final long time, final String type, final Map<String, String> fields) {
            final boolean admin = "true".equals(fields.get("admin"));
            final String account = admin
                    ? person.logons(Source.ENDPOINT).getLast()
                    : person.logon(Source.ENDPOINT);
            add(time, Source.ENDPOINT, account, admin
                    ? "adm-" + host
                    : host, type, fields);
        }

        private void web(final long time,
                         final String domain,
                         final String category,
                         final int bytesOut,
                         final boolean upload) {
            final Map<String, String> fields = new LinkedHashMap<>();
            fields.put("domain", domain);
            fields.put("url", "https://" + domain + (upload
                    ? "/upload"
                    : "/" + random.choice(List.of("", "home", "search", "view", "api/items"))));
            fields.put("category", category);
            fields.put("method", upload
                    ? "POST"
                    : "GET");
            fields.put("bytes_out", String.valueOf(bytesOut));
            fields.put("bytes_in", String.valueOf(upload
                    ? random.randint(500, 3_000)
                    : random.randint(2_000, 900_000)));
            fields.put("ip", "203.0.113." + (1 + Math.floorMod(domain.hashCode(), 250)));
            fields.put("process", "chrome.exe");
            fields.put("access", vpnSession != null
                    ? "VPN"
                    : "Office");
            add(time, Source.PROXY, person.logon(Source.PROXY), host, "web", fields);
            if (!day.isBefore(PIPELINE_CHANGE)) {
                // The proxy's pipeline from month 8 also logs each tunnel: everyone's volume steps (a decoy).
                final Map<String, String> tunnel = new LinkedHashMap<>(fields);
                tunnel.put("method", "CONNECT");
                tunnel.put("url", domain + ":443");
                tunnel.put("bytes_out", "0");
                tunnel.put("bytes_in", "0");
                add(time - 200, Source.PROXY, person.logon(Source.PROXY), host, "web", tunnel);
            }
        }

        private void cloud(final long time, final String type, final Map<String, String> fields) {
            add(time, Source.CLOUD, person.logon(Source.CLOUD), null, type, fields);
        }

        private void add(final long time,
                         final Source source,
                         final String account,
                         final String eventHost,
                         final String type,
                         final Map<String, String> fields) {
            acts.add(new Act(time, source, person.id(), account, eventHost, type, new LinkedHashMap<>(fields),
                    person.organisationOn(day), 0));
        }

        private long minutes(final int minuteOfDay) {
            return ZonedDateTime.of(day, LocalTime.MIDNIGHT, ZONE).plusMinutes(minuteOfDay).toInstant()
                    .toEpochMilli();
        }

        private int minuteOf(final long time) {
            return (int) ((time - minutes(0)) / MINUTE);
        }

        private String homeIp() {
            return "198.51.100." + (10 + index);
        }

        private String officeIp() {
            return "192.0.2." + (10 + index % 40);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // The known answer's plants

    private void describePlants() {
        plants.add(plant(LEAVER, "leaver", "2026-07-01", LEAVING.toString(), List.of(
                "job-site browsing from 2026-07-01, more often towards the leaving date (category " + JOB_CATEGORY
                + ")",
                "breadth widening from 2026-08-10: downloads from sites outside the usual (sites/hr, sites/legal, "
                + "sites/executive, sites/sales)",
                "bulk downloads on working days 2026-09-08 to 2026-09-19 (40 to 120 a day)",
                "an archive then a personal cloud upload within the hour on 2026-09-10, 2026-09-15, 2026-09-17 "
                + "and 2026-09-22 (" + PERSONAL_CLOUD + ")",
                "out of hours over the VPN on the evenings of 2026-09-15 and 2026-09-17",
                "files copied to USB on 2026-09-23",
                "no activity after the leaving date " + LEAVING), Map.of("leaving", LEAVING.toString(),
                "uploads", List.of("2026-09-10", "2026-09-15", "2026-09-17", "2026-09-22"), "vpn_nights",
                List.of("2026-09-15", "2026-09-17"), "usb", "2026-09-23")));
        plants.add(plant(ADMIN, "disgruntled admin", "2026-08-18", "2026-09-04", List.of(
                "a grievance raised 2026-08-03 (an HR date)",
                "out-of-hours privileged sessions over the VPN from 23:00 on 2026-08-18, 2026-08-25, 2026-09-01 and "
                + "2026-09-03",
                "a tool never used before, " + NEW_TOOL + ", on the night of 2026-09-03",
                "the Security log cleared on the night of 2026-09-03 (after midnight, 2026-09-04)"),
                Map.of("grievance", "2026-08-03", "nights", List.of("2026-08-18", "2026-08-25", "2026-09-01",
                        "2026-09-03"), "new_tool", NEW_TOOL)));
        plants.add(plant(VIEWER, "curious viewer", "2026-06-01", "2026-06-26", List.of(
                "30 to 60 record look-ups a working day (CRM, records outside the usual range), where Finance "
                + "peers make a few"), Map.of("peers", FINANCE)));
        plants.add(plant(COMPROMISED, "compromised account", "2026-04-08", "2026-04-14", List.of(
                "on leave 2026-04-06 to 2026-04-17",
                "cloud sign-ins from a new country (RO) at night on 2026-04-08, 2026-04-09, 2026-04-13 and "
                + "2026-04-14, each followed by downloads",
                "a VPN login from RO on 2026-04-09"), Map.of("country", "RO", "leave", List.of("2026-04-06",
                "2026-04-17"))));
        plants.add(plant(ROLE_CHANGE, "role change", ROLE_CHANGE_DAY.toString(), ROLE_CHANGE_DAY.toString(),
                List.of("moves from " + ENGINEERING + " to " + FINANCE + " on " + ROLE_CHANGE_DAY + ": finance "
                        + "applications and sites from then, starting at 08:00 instead of 10:00",
                        "a new host, lt-achen, from " + NEW_HOST_DAY), Map.of("new_host", "lt-achen",
                        "new_host_from", NEW_HOST_DAY.toString())));
        plants.add(plant(DRIFT, "drift to later hours", "2026-05-01", "2026-08-31", List.of(
                "the day's start drifts from about 08:30 to about 11:00 over months 7 to 10",
                "two weeks' leave 2026-01-05 to 2026-01-16"), Map.of("leave", List.of("2026-01-05",
                "2026-01-16"))));
        decoys.add(decoy("P20001", "approved migration", "2026-05-11", "2026-05-15", "bulk transfers to "
                                                                                     + "migration.vendor.example"
                                                                                     + " and downloads",
                "approved data-centre migration"));
        decoys.add(decoy("P20002", "on-call week", "2026-07-20", "2026-07-24", "out-of-hours privileged "
                                                                               + "sessions over the VPN",
                "on-call roster"));
        decoys.add(decoy(null, "source-wide pipeline change", PIPELINE_CHANGE.toString(), END_DAY.minusDays(1)
                .toString(), "the proxy also logs each tunnel (CONNECT) from this day: everyone's proxy volume "
                             + "steps up about twofold", null));
        targetPlants.add(plant(LEAVER, "privileged group granted", GRANT_DAY.toString(), GRANT_DAY.toString(),
                List.of("the privileged group '" + PRIVILEGED_GROUP + "' granted to the leaver's account by an "
                        + "administrator (" + GRANTING_ADMIN + ") on " + GRANT_DAY + ", 11 days before the leaving "
                        + "date: done to them, not their own act"), Map.of("group", PRIVILEGED_GROUP, "by",
                        GRANTING_ADMIN, "day", GRANT_DAY.toString())));
        targetPlants.add(plant(COMPROMISED, "account taken over", TAKEOVER_DAY.toString(), TAKEOVER_DAY.toString(),
                List.of("6 failed logons against the account from RO in the night of " + TAKEOVER_DAY + " (no "
                        + "account acted)", "its password reset by the service desk's account (" + SERVICE_DESK
                                            + ") minutes later, while its owner is on leave", "an MFA device ("
                                                                                              + MFA_DEVICE
                                                                                              + ") added by the "
                                                                                              + "same account"),
                Map.of("day", TAKEOVER_DAY.toString(), "failed_logons", 6, "by", SERVICE_DESK, "device",
                        MFA_DEVICE)));
        oneOffs.add(Map.of("person_id", "P20003", "date", "2026-02-14", "what", "a short weekend session"));
        oneOffs.add(Map.of("person_id", "P20004", "date", "2026-07-08", "what", "80 downloads in one day"));
        oneOffs.add(Map.of("person_id", "P20005", "date", "2026-10-07", "what", "late-night browsing from 23:20"));
    }

    private static Map<String, Object> plant(final String person,
                                             final String kind,
                                             final String from,
                                             final String to,
                                             final List<String> indicators,
                                             final Map<String, Object> facts) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("person_id", person);
        map.put("kind", kind);
        map.put("from", from);
        map.put("to", to);
        map.put("indicators", indicators);
        map.put("facts", facts);
        return map;
    }

    private Map<String, Object> decoy(final String person,
                                      final String kind,
                                      final String from,
                                      final String to,
                                      final String what,
                                      final String exception) {
        final Map<String, Object> map = new LinkedHashMap<>();
        if (person != null) {
            map.put("person_id", person);
        }
        map.put("kind", kind);
        map.put("from", from);
        map.put("to", to);
        map.put("what", what);
        if (exception != null) {
            map.put("exception", exception);
        }
        map.put("must_not_be_flagged", true);
        if (person != null && people.stream().noneMatch(one -> one.id().equals(person))) {
            map.put("not_in_this_dataset", true);
        }
        return map;
    }

    /// @param time An epoch millisecond.
    /// @return It in [#ZONE], ISO 8601 with its offset.
    static String iso(final long time) {
        return ISO.format(java.time.Instant.ofEpochMilli(time).atZone(ZONE));
    }

    // A file's SHA-256 stand-in: the same for the same name.
    static String hash(final String name) {
        final StringBuilder sb = new StringBuilder();
        int h = name.hashCode();
        for (int i = 0; i < 8; i++) {
            h = h * 1_103_515_245 + 12_345;
            sb.append("%08x".formatted(h));
        }
        return sb.toString();
    }


    // --------------------------------------------------------------------------------


    /// A source of events.
    enum Source {
        ENDPOINT("endpoint"),
        PROXY("proxy"),
        VPN("vpn"),
        CLOUD("cloud"),
        PAM("pam");

        private final String key;

        Source(final String key) {
            this.key = key;
        }

        /// @return Its name in files and the known answer.
        String key() {
            return key;
        }
    }


    // --------------------------------------------------------------------------------


    /// How much to make.
    ///
    /// @param seed       The seed.
    /// @param background How many background people (at most 30).
    /// @param scale      The share of routine actions kept, 0 to 1 (plants are always whole).
    /// @param days       The days made, or null for all.
    record Options(long seed, int background, double scale, List<Period> days) {

        /// @return The whole dataset, for the dev node: every person and day, 70% of the routine actions (so the
        /// raw files stay under about 1 GB).
        static Options full() {
            return new Options(SEED, 30, 0.7, null);
        }

        /// The reduced form for e2e tests: the scenario people and the decoys' and one-offs' 5, a third of the
        /// routine actions, and only the days the tests read (a baseline week and the plants' windows).
        ///
        /// @return It.
        static Options reduced() {
            return new Options(SEED, 5, 0.3, List.of(
                    new Period(LocalDate.of(2025, 11, 3), LocalDate.of(2025, 11, 7)),
                    new Period(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3)),
                    new Period(LocalDate.of(2026, 4, 8), LocalDate.of(2026, 4, 9)),
                    new Period(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2)),
                    new Period(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4)),
                    new Period(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18))));
        }

        /// @param day A day.
        /// @return True if it is made.
        boolean keeps(final LocalDate day) {
            return days == null || days.stream().anyMatch(period -> period.contains(day));
        }
    }


    // --------------------------------------------------------------------------------


    /// Days, inclusive.
    ///
    /// @param from The first.
    /// @param to   The last.
    record Period(LocalDate from, LocalDate to) {

        /// @param day A day.
        /// @return True if it is one of them.
        boolean contains(final LocalDate day) {
            return !day.isBefore(from) && !day.isAfter(to);
        }

        /// @return `[from, to]` as text.
        List<String> asList() {
            return List.of(from.toString(), to.toString());
        }
    }


    // --------------------------------------------------------------------------------


    /// A person.
    ///
    /// @param id           Their person id.
    /// @param first        Their first name.
    /// @param last         Their last name.
    /// @param organisation Their `Organisation` at the start.
    /// @param movedTo      The `Organisation` they moved to on [#ROLE_CHANGE_DAY], or null.
    /// @param scenario     Their scenario, or null for the background.
    /// @param admin        True for an administrator, with an admin account.
    /// @param role         Their role, whose tasks they do.
    /// @param start        Their start date.
    /// @param leaving      Their leaving date, or null.
    /// @param leave        Their leave.
    /// @param startMinute  The minute of the day they usually start.
    /// @param remoteDays   The days they work over the VPN.
    record Person(String id,
                  String first,
                  String last,
                  String organisation,
                  String movedTo,
                  String scenario,
                  boolean admin,
                  String role,
                  LocalDate start,
                  LocalDate leaving,
                  List<Period> leave,
                  int startMinute,
                  Set<DayOfWeek> remoteDays) {

        /// @return Their short account name, e.g. `dwhitfield`.
        String shortName() {
            return (first.charAt(0) + last).toLowerCase(Locale.ROOT);
        }

        /// @return Their dotted name, e.g. `dana.whitfield`.
        String dotted() {
            return (first + "." + last).toLowerCase(Locale.ROOT);
        }

        /// @param source A source.
        /// @return Their accounts there, as it writes them, their usual first.
        List<String> logons(final Source source) {
            return switch (source) {
                case ENDPOINT -> admin
                        ? List.of("CORP\\" + shortName(), "CORP\\adm-" + shortName())
                        : List.of("CORP\\" + shortName());
                case PROXY, CLOUD -> List.of(dotted() + "@" + MAIL_DOMAIN);
                case VPN -> List.of(dotted());
                case PAM -> admin
                        ? List.of(MAIL_DOMAIN + "\\" + dotted())
                        : List.of();
            };
        }

        /// @param source A source.
        /// @return Their usual account there.
        String logon(final Source source) {
            return logons(source).getFirst();
        }

        /// @param day A day.
        /// @return Their `Organisation` that day.
        String organisationOn(final LocalDate day) {
            return movedTo != null && !day.isBefore(ROLE_CHANGE_DAY)
                    ? movedTo
                    : organisation;
        }

        /// @param day A day.
        /// @return The host they work on that day.
        String hostOn(final LocalDate day) {
            return ROLE_CHANGE.equals(id) && !day.isBefore(NEW_HOST_DAY)
                    ? "lt-" + shortName()
                    : "ws-" + shortName();
        }

        /// @return Every host they use.
        List<String> hosts() {
            final Set<String> hosts = new LinkedHashSet<>();
            hosts.add("ws-" + shortName());
            if (admin) {
                hosts.add("adm-ws-" + shortName());
            }
            if (ROLE_CHANGE.equals(id)) {
                hosts.add("lt-" + shortName());
            }
            return List.copyOf(hosts);
        }

        /// @param day A day.
        /// @return True if they are on leave.
        boolean onLeave(final LocalDate day) {
            return leave.stream().anyMatch(period -> period.contains(day));
        }
    }


    // --------------------------------------------------------------------------------


    /// One event, as the generator knows it.
    ///
    /// @param time         When, epoch milliseconds.
    /// @param source       Its source.
    /// @param personId     Whose it is.
    /// @param account      The account, as the source writes it.
    /// @param host         The host it was on, or null (the cloud's).
    /// @param type         Its type, the source's own name (e.g. `focus.in`, `vpn.login`).
    /// @param fields       Its values by name.
    /// @param organisation The person's `Organisation` that day.
    /// @param number       Its number within its source, from 1 (its EventId).
    record Act(long time,
               Source source,
               String personId,
               String account,
               String host,
               String type,
               Map<String, String> fields,
               String organisation,
               int number) {

        /// @param newNumber Its number within its source.
        /// @return It numbered.
        Act numbered(final int newNumber) {
            return new Act(time, source, personId, account, host, type, fields, organisation, newNumber);
        }

        /// @param name A field.
        /// @return Its value, or null.
        String field(final String name) {
            return fields.get(name);
        }
    }


    // --------------------------------------------------------------------------------


    /// A kind of task: its application, its windows' titles, the files and sites it touches.
    ///
    /// @param label       What it is.
    /// @param application Its process.
    /// @param titles      Its window titles (`{month}` for the month's name).
    /// @param files       The files it reads and writes.
    /// @param domains     The web sites it uses.
    /// @param category    The proxy's category of those sites.
    /// @param site        The cloud site its files are in.
    record Task(String label,
                String application,
                List<String> titles,
                List<String> files,
                List<String> domains,
                String category,
                String site) {

        static final String BROWSER = "chrome.exe";
        static final List<String> TARGETS = List.of("srv-app-03.corp.example", "srv-web-01.corp.example",
                "srv-db-01.corp.example", "srv-file-02.corp.example");
        static final Task MAIL = new Task("Mail", "OUTLOOK.EXE", List.of("Inbox - Outlook", "RE: {month} "
                                                                                            + "update - Message"),
                List.of("mail.pst"), List.of(), "Business", "sites/mail");
        private static final Map<String, List<Task>> BY_ROLE = Map.of(
                FINANCE, List.of(
                        new Task("Month-end close", "EXCEL.EXE", List.of("{month} close.xlsx - Excel",
                                "Accruals {month}.xlsx - Excel"), List.of("{month} close.xlsx", "Accruals.xlsx",
                                "Trial balance.xlsx"), List.of("erp.corp.example"), "Business", "sites/finance"),
                        new Task("Supplier invoices", BROWSER, List.of("Invoices - ERP - Chrome"), List.of(),
                                List.of("erp.corp.example", "invoices.corp.example"), "Business", "sites/finance"),
                        new Task("Budget forecast", "EXCEL.EXE", List.of("Forecast FY27.xlsx - Excel"),
                                List.of("Forecast FY27.xlsx", "Headcount plan.xlsx"), List.of(
                                "sharepoint.corp.example"), "Business", "sites/finance"),
                        new Task("Board report", "WINWORD.EXE", List.of("Board report {month}.docx - Word"),
                                List.of("Board report.docx"), List.of("xe-rates.example.com"), "Finance",
                                "sites/finance"),
                        MAIL),
                ENGINEERING, List.of(
                        new Task("Feature work", "Code.exe", List.of("PaymentService.java - payments - Visual Studio "
                                                                     + "Code", "LedgerTest.java - payments - Visual "
                                                                               + "Studio Code"),
                                List.of("PaymentService.java", "LedgerTest.java", "build.gradle"),
                                List.of("docs.java-reference.example", "git.corp.example"), "Technology",
                                "sites/engineering"),
                        new Task("Code review", BROWSER, List.of("Pull request 4821 - Git - Chrome"), List.of(),
                                List.of("git.corp.example", "ci.corp.example"), "Technology", "sites/engineering"),
                        new Task("Build fix", "WindowsTerminal.exe", List.of("build-agent-07 - Terminal"),
                                List.of("build.log"), List.of("ci.corp.example", "stackexchange-like.example"),
                                "Technology", "sites/engineering"),
                        new Task("Design doc", "WINWORD.EXE", List.of("Ledger redesign.docx - Word"),
                                List.of("Ledger redesign.docx"), List.of("wiki.corp.example"), "Technology",
                                "sites/engineering"),
                        MAIL),
                OPERATIONS, List.of(
                        new Task("Ticket queue", BROWSER, List.of("Queue - Service Desk - Chrome"), List.of(),
                                List.of("servicedesk.corp.example"), "Business", "sites/operations"),
                        new Task("Server maintenance", "mstsc.exe", List.of("srv-app-03 - Remote Desktop",
                                "srv-web-01 - Remote Desktop"), List.of("patch-notes.txt"),
                                List.of("vendor-support.example"), "Technology", "sites/operations"),
                        new Task("Monitoring", BROWSER, List.of("Dashboards - Grafana - Chrome"), List.of(),
                                List.of("grafana.corp.example", "status.cloudprovider.example"), "Technology",
                                "sites/operations"),
                        new Task("Change planning", "EXCEL.EXE", List.of("Change calendar.xlsx - Excel"),
                                List.of("Change calendar.xlsx"), List.of("servicedesk.corp.example"), "Business",
                                "sites/operations"),
                        MAIL));

        /// @param role A role.
        /// @return Its tasks.
        static List<Task> forRole(final String role) {
            return BY_ROLE.get(role);
        }

        /// @param application A process.
        /// @return Its command line.
        static String command(final String application) {
            return switch (application) {
                case "EXCEL.EXE", "WINWORD.EXE", "OUTLOOK.EXE" -> "C:\\Program Files\\Microsoft Office\\root\\"
                                                                   + "Office16\\" + application;
                case BROWSER -> "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
                case "Code.exe" -> "C:\\Users\\Public\\AppData\\Local\\Programs\\Microsoft VS Code\\Code.exe";
                case "mstsc.exe" -> "C:\\Windows\\System32\\mstsc.exe";
                default -> "C:\\Windows\\System32\\" + application;
            };
        }

        /// @return True if it is done in the browser.
        boolean browser() {
            return BROWSER.equals(application);
        }

        /// @param random The draw.
        /// @param day    The day.
        /// @return A window title of it.
        String title(final SeededRandom random, final LocalDate day) {
            return random.choice(titles).replace("{month}", day.getMonth().getDisplayName(
                    java.time.format.TextStyle.FULL, Locale.ENGLISH));
        }
    }
}
