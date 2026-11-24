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

import stroom.mcp.impl.e2e.InsiderDataset.Act;
import stroom.mcp.impl.e2e.InsiderDataset.Options;
import stroom.mcp.impl.e2e.InsiderDataset.Person;
import stroom.mcp.impl.e2e.InsiderDataset.Source;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/// The insider-risk dataset generator ([InsiderDataset], [InsiderRecords]): the same events every run, a reduced
/// form that is the full form on the days it keeps, and a known answer that matches what was planted, checked
/// against the events themselves. Every day at a reduced scale is generated once for the plants' checks.
///
/// With `INSIDER_DATASET_DIR=<dir>` (and `INSIDER_DATASET_NETSKOPE=<the Netskope sample>`) in the environment, the
/// full dataset's raw files are written there for the dev node (`writeTheDevNodesFiles`).
@SuppressWarnings("unchecked")
class TestInsiderDataset {

    private static InsiderDataset everyDay;

    @BeforeAll
    static void generate() {
        everyDay = InsiderDataset.generate(new Options(InsiderDataset.SEED, 5, 0.3, null));
    }

    @Test
    void theReducedDatasetIsTheSameEveryRunAndTheFullFormOnItsDays() {
        final InsiderDataset first = InsiderDataset.generate(Options.reduced());
        final InsiderDataset second = InsiderDataset.generate(Options.reduced());

        assertThat(fingerprint(first.acts())).isEqualTo(fingerprint(second.acts()));
        assertThat(JsonUtil.writeValueAsString(first.knownAnswer(), false))
                .isEqualTo(JsonUtil.writeValueAsString(second.knownAnswer(), false));
        // The reduced days are exactly every day's events on those days (numbered apart).
        final List<String> kept = everyDay.acts().stream()
                .filter(act -> Options.reduced().keeps(day(act)))
                .map(TestInsiderDataset::withoutNumber)
                .toList();
        assertThat(first.acts().stream().map(TestInsiderDataset::withoutNumber).toList()).isEqualTo(kept);
        // Everyone has events in it.
        assertThat(first.acts().stream().map(Act::personId).collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(first.people().stream().map(Person::id).toList());
    }

    // The reduced dataset's events, pinned: the e2e tests' and eval cases' expectations were written for them. The
    // draws are rejection-sampled (CPython's randbelow), so a changed range (randint(0, 30) for randint(0, 50), in
    // 42731549b2) takes another number of draws and changes every event generated after it in a person-day: a
    // change that is meant must re-run those expectations (TestE2eInsider*, TestEvalCases, the eval references 79
    // on), then update this.
    @Test
    void theReducedDatasetsEventsAreThoseTheExpectationsWereWrittenFor() {
        assertThat(fingerprint(InsiderDataset.generate(Options.reduced()).acts())).isEqualTo("de49b640/21100");
    }

    @Test
    void everyEventRendersAsADocumentWellFormedXmlAndARawRecord() throws Exception {
        final InsiderDataset reduced = InsiderDataset.generate(Options.reduced());
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        for (final Act act : reduced.acts()) {
            factory.newDocumentBuilder().parse(new InputSource(new StringReader(InsiderRecords.eventXml(act))));
            final Map<String, Object> doc = InsiderRecords.document(act, 7);
            assertThat(InsiderRecords.mapping(act.source())).containsKeys(doc.keySet().stream()
                    .filter(key -> !"_id".equals(key)).toArray(String[]::new));
            assertThat(doc).containsEntry("user.id", act.personId()).containsEntry("EventId", act.number());
        }
        final Act endpoint = reduced.acts(Source.ENDPOINT).stream()
                .filter(act -> "focus.in".equals(act.type())).findFirst().orElseThrow();
        assertThat(InsiderRecords.endpointLine(endpoint)).contains(" deskwatch[1180]: evt=focus.in user=CORP\\")
                .contains(" hwnd=0x").contains(" title=\"");
        assertThat(InsiderRecords.eventXml(endpoint)).contains("<UserDetails><Id>" + endpoint.personId() + "</Id>"
                                                               + "<Organisation>")
                .contains("<Data Name=\"WindowHandle\" Value=\"0x");
        assertThat(InsiderRecords.normalised("CORP\\DWhitfield")).isEqualTo("dwhitfield");
        assertThat(InsiderRecords.normalised("dana.whitfield@corp.example")).isEqualTo("dana.whitfield");
    }

    @Test
    void eachPersonsLogonIdsDifferBySourceWhileTheirPersonIdIsTheSame() {
        final Person leaver = everyDay.person(InsiderDataset.LEAVER);
        final Person admin = everyDay.person(InsiderDataset.ADMIN);

        assertThat(leaver.logons(Source.ENDPOINT)).containsExactly("CORP\\dwhitfield");
        assertThat(leaver.logons(Source.PROXY)).containsExactly("dana.whitfield@corp.example");
        assertThat(leaver.logons(Source.VPN)).containsExactly("dana.whitfield");
        assertThat(admin.logons(Source.ENDPOINT)).containsExactly("CORP\\moyelaran", "CORP\\adm-moyelaran");
        assertThat(everyDay.reference()).contains(List.of("CORP\\adm-moyelaran", "endpoint", InsiderDataset.ADMIN,
                InsiderDataset.OPERATIONS, InsiderDataset.FIRST_DAY.toString()));
        // The organisations: 3, each with people.
        assertThat(everyDay.people().stream().map(Person::organisation).distinct()).hasSize(3);
    }

    @Test
    void theLeaversPlantsAreWhereTheKnownAnswerSays() {
        final List<Act> leaver = of(InsiderDataset.LEAVER);
        final Map<String, Object> plant = plant(InsiderDataset.LEAVER);
        final Map<String, Object> facts = (Map<String, Object>) plant.get("facts");

        assertThat(leaver).allSatisfy(act -> assertThat(day(act)).isBeforeOrEqualTo(InsiderDataset.LEAVING));
        final List<Act> uploads = leaver.stream()
                .filter(act -> InsiderDataset.PERSONAL_CLOUD.equals(act.field("domain"))
                               && "POST".equals(act.field("method")))
                .toList();
        assertThat(uploads.stream().map(act -> day(act).toString()).distinct().toList())
                .containsExactlyElementsOf((List<String>) facts.get("uploads"));
        // Each upload within the hour after an archive.
        for (final Act upload : uploads) {
            assertThat(leaver).anySatisfy(act -> {
                assertThat(act.type()).isEqualTo("file.archive");
                assertThat(upload.time() - act.time()).isBetween(0L, 3_600_000L);
            });
        }
        // The VPN nights: a login after 19:00.
        for (final String night : (List<String>) facts.get("vpn_nights")) {
            assertThat(leaver).anySatisfy(act -> {
                assertThat(act.type()).isEqualTo("vpn.login");
                assertThat(day(act).toString()).isEqualTo(night);
                assertThat(local(act).getHour()).isGreaterThanOrEqualTo(19);
            });
        }
        assertThat(leaver.stream().filter(act -> "usb.copy".equals(act.type())).map(act -> day(act).toString())
                .distinct()).containsExactly((String) facts.get("usb"));
        // Job-site browsing only from July.
        assertThat(leaver.stream().filter(act -> InsiderDataset.JOB_CATEGORY.equals(act.field("category"))))
                .isNotEmpty()
                .allSatisfy(act -> assertThat(day(act)).isAfterOrEqualTo(LocalDate.of(2026, 7, 1)));
        // Bulk downloads: far more a day in the window than before it.
        assertThat(downloadsADay(leaver, LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 19)))
                .isGreaterThan(10 * Math.max(1, downloadsADay(leaver, LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28))));
    }

    @Test
    void theAdminsCompromisedAccountsAndViewersPlantsAreWhereTheKnownAnswerSays() {
        final List<Act> admin = of(InsiderDataset.ADMIN);
        final List<Act> tool = admin.stream()
                .filter(act -> "proc.start".equals(act.type())
                               && Objects.toString(act.field("command"), "").contains(InsiderDataset.NEW_TOOL))
                .toList();
        assertThat(tool).isNotEmpty().allSatisfy(act -> assertThat(day(act)).isEqualTo(LocalDate.of(2026, 9, 3)));
        assertThat(admin.stream().filter(act -> "log.clear".equals(act.type())))
                .singleElement().satisfies(act -> {
                    assertThat(day(act)).isEqualTo(LocalDate.of(2026, 9, 4));
                    assertThat(act.account()).isEqualTo("CORP\\adm-moyelaran");
                });
        assertThat(admin.stream().filter(act -> "pam.session.start".equals(act.type())
                                                && local(act).getHour() >= 23)
                .map(act -> day(act).toString()).distinct().toList())
                .containsExactlyElementsOf((List<String>) ((Map<String, Object>) plant(InsiderDataset.ADMIN)
                        .get("facts")).get("nights"));

        // On leave, only the cloud and the VPN, from the new country, at night.
        final List<Act> onLeave = of(InsiderDataset.COMPROMISED).stream()
                .filter(act -> everyDay.person(InsiderDataset.COMPROMISED).onLeave(day(act)))
                .toList();
        assertThat(onLeave).isNotEmpty().allSatisfy(act -> {
            assertThat(act.source()).isIn(Source.CLOUD, Source.VPN);
            assertThat(local(act).getHour()).isLessThan(6);
        });
        assertThat(onLeave.stream().filter(act -> act.field("country") != null).map(act -> act.field("country"))
                .distinct()).containsExactly("RO");

        // The viewer's look-ups: far above their Finance peers'.
        final LocalDate from = LocalDate.of(2026, 6, 1);
        final LocalDate to = LocalDate.of(2026, 6, 26);
        final long viewer = views(of(InsiderDataset.VIEWER), from, to);
        final List<Long> peers = everyDay.people().stream()
                .filter(person -> InsiderDataset.FINANCE.equals(person.organisationOn(from))
                                  && !InsiderDataset.VIEWER.equals(person.id()))
                .map(person -> views(of(person.id()), from, to))
                .toList();
        assertThat(peers).isNotEmpty().allSatisfy(peer -> assertThat(viewer).isGreaterThan(5 * Math.max(1, peer)));
    }

    @Test
    void theRhythmPlantsAndTheDecoysAreWhereTheKnownAnswerSays() {
        // The drift: the first logon of the day later by August than in April.
        assertThat(firstLogonHour(InsiderDataset.DRIFT, LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 28)))
                .isGreaterThan(firstLogonHour(InsiderDataset.DRIFT, LocalDate.of(2026, 4, 6),
                        LocalDate.of(2026, 4, 17)) + 1.5);
        // The drift person's two weeks' leave: nothing.
        assertThat(of(InsiderDataset.DRIFT)).noneSatisfy(act -> assertThat(day(act))
                .isBetween(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 16)));
        // The role change: earlier days, another organisation, a new host from month 11.
        final List<Act> moved = of(InsiderDataset.ROLE_CHANGE);
        assertThat(firstLogonHour(InsiderDataset.ROLE_CHANGE, LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 20)))
                .isLessThan(firstLogonHour(InsiderDataset.ROLE_CHANGE, LocalDate.of(2026, 2, 9),
                        LocalDate.of(2026, 2, 20)) - 1);
        assertThat(moved).allSatisfy(act -> assertThat(act.organisation()).isEqualTo(day(act).isBefore(
                InsiderDataset.ROLE_CHANGE_DAY)
                ? InsiderDataset.ENGINEERING
                : InsiderDataset.FINANCE));
        assertThat(moved.stream().filter(act -> "lt-achen".equals(act.host())))
                .isNotEmpty()
                .allSatisfy(act -> assertThat(day(act)).isAfterOrEqualTo(InsiderDataset.NEW_HOST_DAY));
        // The pipeline change: tunnels logged for everyone from that day, none before.
        final List<Act> tunnels = everyDay.acts(Source.PROXY).stream()
                .filter(act -> "CONNECT".equals(act.field("method")))
                .toList();
        assertThat(tunnels).allSatisfy(act -> assertThat(day(act)).isAfterOrEqualTo(InsiderDataset.PIPELINE_CHANGE));
        assertThat(tunnels.stream().map(Act::personId).distinct().count()).isEqualTo(everyDay.people().size());
        // The decoys and exceptions are in the known answer.
        final Map<String, Object> answer = everyDay.knownAnswer();
        assertThat((List<Map<String, Object>>) answer.get("decoys")).extracting(decoy -> decoy.get("kind"))
                .containsExactly("approved migration", "on-call week", "source-wide pipeline change");
        assertThat((List<Map<String, Object>>) answer.get("exceptions")).hasSize(2);
        assertThat((Map<String, Object>) answer.get("tasks")).containsKeys(InsiderDataset.LEAVER,
                InsiderDataset.DRIFT);
    }

    @Test
    void theEventsDoneToPeopleAreAStreamOfTheirOwnWhereTheKnownAnswerSays() {
        final InsiderDataset reduced = InsiderDataset.generate(Options.reduced());
        // None of them is among the people's own events, and the reduced days hold every day's of them.
        assertThat(everyDay.acts()).noneSatisfy(act -> assertThat(act.fields()).containsKey("target_person"));
        assertThat(reduced.targetActs().stream().map(TestInsiderDataset::withoutNumber).toList())
                .isEqualTo(everyDay.targetActs().stream()
                        .filter(act -> Options.reduced().keeps(day(act)))
                        .map(TestInsiderDataset::withoutNumber)
                        .toList());
        // Numbered on after their source's own events, so every own event keeps its number.
        final long cloud = reduced.acts(Source.CLOUD).size();
        assertThat(reduced.targetActs(Source.CLOUD)).extracting(Act::number).containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(cloud + 1, cloud + reduced.targetActs().size()).boxed()
                        .map(Long::intValue).toList());

        // The leaver: a privileged group granted by an administrator, 11 days before the leaving date.
        assertThat(reduced.targetActs()).filteredOn(act -> InsiderDataset.LEAVER.equals(act.field("target_person")))
                .singleElement().satisfies(act -> {
                    assertThat(act.type()).isEqualTo("group.add");
                    assertThat(act.personId()).isEqualTo(InsiderDataset.GRANTING_ADMIN);
                    assertThat(day(act)).isEqualTo(InsiderDataset.GRANT_DAY);
                    assertThat(act.field("group")).isEqualTo(InsiderDataset.PRIVILEGED_GROUP);
                });
        // The compromised account: failed logons against it (no account acted), then a reset and an MFA device
        // added by the service desk, in the night, while its owner is on leave.
        final List<Act> takeover = reduced.targetActs().stream()
                .filter(act -> InsiderDataset.COMPROMISED.equals(act.field("target_person")))
                .toList();
        assertThat(takeover).extracting(Act::type).containsExactly("signin.failed", "signin.failed",
                "signin.failed", "signin.failed", "signin.failed", "signin.failed", "password.reset", "mfa.add");
        assertThat(takeover).allSatisfy(act -> {
            assertThat(day(act)).isEqualTo(InsiderDataset.TAKEOVER_DAY);
            assertThat(local(act).getHour()).isLessThan(6);
            assertThat(act.personId()).isNull();
        });
        assertThat(takeover.stream().filter(act -> act.account() != null).map(Act::account).distinct())
                .containsExactly(InsiderDataset.SERVICE_DESK);
        // The noise: the service desk's resets of background people only.
        assertThat(everyDay.targetActs()).filteredOn(act -> InsiderDataset.SERVICE_DESK.equals(act.account())
                                                            && !InsiderDataset.COMPROMISED.equals(
                act.field("target_person"))).isNotEmpty().allSatisfy(act -> {
                    assertThat(act.type()).isEqualTo("password.reset");
                    assertThat(act.field("target_person")).startsWith("P2");
                });
        final Map<String, Object> answer = everyDay.knownAnswer();
        assertThat((List<Map<String, Object>>) answer.get("targets")).extracting(plant -> plant.get("person_id"),
                plant -> plant.get("kind")).containsExactly(
                org.assertj.core.groups.Tuple.tuple(InsiderDataset.LEAVER, "privileged group granted"),
                org.assertj.core.groups.Tuple.tuple(InsiderDataset.COMPROMISED, "account taken over"));
        assertThat((List<Map<String, Object>>) answer.get("target_noise")).hasSize((int) everyDay.targetActs()
                .stream().filter(act -> act.field("target_person").startsWith("P2")).count());
        assertThat(answer).containsEntry("target_events", everyDay.targetActs().size());
    }

    @Test
    void theEventsDoneToPeopleRenderWithWhomTheyWereDoneTo() throws Exception {
        final InsiderDataset reduced = InsiderDataset.generate(Options.reduced());
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        for (final Act act : reduced.targetActs()) {
            factory.newDocumentBuilder().parse(new InputSource(new StringReader(InsiderRecords.eventXml(act))));
            final Map<String, Object> doc = InsiderRecords.document(act, 7);
            assertThat(InsiderRecords.mapping(act.source())).containsKeys(doc.keySet().stream()
                    .filter(key -> !"_id".equals(key)).toArray(String[]::new));
            assertThat(doc).containsEntry("user.target.id", act.field("target_person"))
                    .containsEntry("user.target.name", InsiderRecords.normalised(act.field("target_account")));
            assertThat(InsiderRecords.flat(act)).containsEntry("TargetPersonId", act.field("target_person"))
                    .doesNotContainValue(null);
        }
        final Act grant = reduced.targetActs().stream().filter(act -> "group.add".equals(act.type())).findFirst()
                .orElseThrow();
        assertThat(InsiderRecords.document(grant, 7)).containsEntry("user.id", InsiderDataset.GRANTING_ADMIN)
                .containsEntry("event.action", "AddGroup").containsEntry("group.name",
                        InsiderDataset.PRIVILEGED_GROUP);
        assertThat(InsiderRecords.eventXml(grant)).contains("<Authorise><User><Id>dana.whitfield@corp.example</Id>"
                                                            + "<UserDetails><Id>P10001</Id></UserDetails></User>"
                                                            + "<Action>Modify</Action><AddGroups><Group><Name>"
                                                            + InsiderDataset.PRIVILEGED_GROUP);
        final Act failed = reduced.targetActs().stream().filter(act -> "signin.failed".equals(act.type()))
                .findFirst().orElseThrow();
        assertThat(InsiderRecords.document(failed, 7)).doesNotContainKeys("user.id", "user.name")
                .containsEntry("event.action", "Logon");
        assertThat(InsiderRecords.eventXml(failed)).contains("</Generator><Client><IPAddress>")
                .contains("<Outcome><Success>false</Success>");
        assertThat(InsiderRecords.eventXml(failed).substring(0, InsiderRecords.eventXml(failed)
                .indexOf("</EventSource>"))).doesNotContain("<User>");
    }

    // Writes the full dataset's raw files for the dev node, a month at a time: opt-in, as it writes a lot.
    @Test
    @EnabledIfEnvironmentVariable(named = "INSIDER_DATASET_DIR", matches = ".+")
    void writeTheDevNodesFiles() {
        final Map<Path, long[]> written = InsiderRecords.writeDevFiles(Options.full(), Path.of(System.getenv(
                "INSIDER_DATASET_DIR")), Path.of(System.getenv("INSIDER_DATASET_NETSKOPE")));
        final Map<String, long[]> bySource = new TreeMap<>();
        written.forEach((path, sizes) -> bySource.merge(path.getParent().getFileName().toString(),
                sizes.clone(), (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]}));
        bySource.forEach((source, sizes) -> System.out.println(source + ": " + sizes[0] + " bytes, " + sizes[1]
                                                               + " events"));
        assertThat(written).isNotEmpty();
    }

    // ------------------------------------------------------------------------------------------------

    private static List<Act> of(final String person) {
        return everyDay.acts().stream().filter(act -> act.personId().equals(person)).toList();
    }

    private static Map<String, Object> plant(final String person) {
        return ((List<Map<String, Object>>) everyDay.knownAnswer().get("plants")).stream()
                .filter(plant -> person.equals(plant.get("person_id")))
                .findFirst()
                .orElseThrow();
    }

    private static long downloadsADay(final List<Act> acts, final LocalDate from, final LocalDate to) {
        final Map<LocalDate, Long> byDay = acts.stream()
                .filter(act -> "download".equals(act.type()) && !day(act).isBefore(from) && !day(act).isAfter(to))
                .collect(Collectors.groupingBy(TestInsiderDataset::day, Collectors.counting()));
        return byDay.isEmpty()
                ? 0
                : byDay.values().stream().mapToLong(Long::longValue).sum() / byDay.size();
    }

    private static long views(final List<Act> acts, final LocalDate from, final LocalDate to) {
        return acts.stream()
                .filter(act -> "record.view".equals(act.type()) && !day(act).isBefore(from) && !day(act).isAfter(to))
                .count();
    }

    private static double firstLogonHour(final String person, final LocalDate from, final LocalDate to) {
        final Map<LocalDate, Long> first = of(person).stream()
                .filter(act -> "logon".equals(act.type()) && !day(act).isBefore(from) && !day(act).isAfter(to))
                .collect(Collectors.toMap(TestInsiderDataset::day, Act::time, Math::min, TreeMap::new));
        assertThat(first).isNotEmpty();
        return first.values().stream()
                .mapToDouble(time -> local(time).getHour() + local(time).getMinute() / 60.0)
                .average()
                .orElseThrow();
    }

    private static LocalDate day(final Act act) {
        return local(act).toLocalDate();
    }

    private static ZonedDateTime local(final Act act) {
        return local(act.time());
    }

    private static ZonedDateTime local(final long time) {
        return Instant.ofEpochMilli(time).atZone(InsiderDataset.ZONE);
    }

    private static String withoutNumber(final Act act) {
        return act.time() + "|" + act.source() + "|" + act.personId() + "|" + act.account() + "|" + act.host() + "|"
               + act.type() + "|" + new TreeMap<>(act.fields());
    }

    private static String fingerprint(final List<Act> acts) {
        final List<String> lines = new ArrayList<>();
        for (final Act act : acts) {
            lines.add(act.number() + "|" + withoutNumber(act) + "|" + InsiderRecords.eventXml(act));
        }
        return Integer.toHexString(String.join("\n", lines).hashCode()) + "/" + lines.size();
    }
}
