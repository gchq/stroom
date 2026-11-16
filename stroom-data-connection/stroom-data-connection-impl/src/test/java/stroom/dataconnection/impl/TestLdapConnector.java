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

package stroom.dataconnection.impl;

import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionTestResponse;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.LdapSearchScope;
import stroom.dataconnection.shared.LdapSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.shared.PermissionException;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.util.ssl.JVMDefaultTrustManager;
import com.unboundid.util.ssl.KeyStoreKeyManager;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import com.unboundid.util.ssl.TrustStoreTrustManager;
import com.unboundid.util.ssl.cert.ManageCertificates;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/// [LdapConnector] against UnboundID's in-memory directory, over plain LDAP, StartTLS and LDAPS.
class TestLdapConnector {

    private static final String BASE = "dc=example,dc=org";
    private static final String PEOPLE = "ou=People," + BASE;
    private static final String PARTNERS = "ou=Partners," + BASE;
    // More entries than the entry source's queue of 100 holds, in one page of the default 500.
    private static final String MANY = "ou=Many," + BASE;
    private static final int MANY_ENTRIES = 300;
    private static final String BIND_DN = "cn=Stroom Reader," + BASE;
    private static final String BIND_PASSWORD = "reader-password";
    private static final String CREDENTIAL_NAME = "directory-reader";
    private static final String HOST = "localhost";
    private static final byte[] PHOTO = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00, 0x10};
    private static final List<DataColumn> ATTRIBUTES = List.of(
            new DataColumn("cn", DataColumnType.TEXT),
            new DataColumn("mail", DataColumnType.TEXT),
            new DataColumn("employeeNumber", DataColumnType.LONG),
            new DataColumn("createTimestamp", DataColumnType.DATE));

    private static InMemoryDirectoryServer server;
    private static File certificate;
    private static char[] certificatePin;

    private final StoredSecrets storedSecrets = Mockito.mock(StoredSecrets.class);

    @BeforeAll
    static void startDirectory(@TempDir final Path tempDir) throws Exception {
        certificatePin = "changeit".toCharArray();
        certificate = generateCertificate(tempDir, HOST);
        final SSLUtil serverSsl = serverSsl(certificate);
        // A certificate for another host, to show the host name is checked.
        final SSLUtil misnamedSsl = serverSsl(generateCertificate(tempDir, "other.example.org"));

        final InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE);
        config.addAdditionalBindCredentials(BIND_DN, BIND_PASSWORD);
        config.setListenerConfigs(
                InMemoryListenerConfig.createLDAPConfig("plain", InetAddress.getLoopbackAddress(), 0,
                        serverSsl.createSSLSocketFactory()),
                InMemoryListenerConfig.createLDAPSConfig("secure", InetAddress.getLoopbackAddress(), 0,
                        serverSsl.createSSLServerSocketFactory(), null),
                InMemoryListenerConfig.createLDAPSConfig("misnamed", InetAddress.getLoopbackAddress(), 0,
                        misnamedSsl.createSSLServerSocketFactory(), null));
        server = new InMemoryDirectoryServer(config);
        server.add("dn: " + BASE, "objectClass: domain", "dc: example");
        server.add("dn: " + PEOPLE, "objectClass: organizationalUnit", "ou: People");
        addPerson("asmith", "Alice Smith", "1001", "alice@example.org", "a.smith@example.org");
        addPerson("bjones", "Bob Jones", "1002", "bob@example.org");
        addPerson("cbrown", "Carol Brown", "x12", "carol@example.org");
        addPerson("dgreen", "Dan Green", "1004", "dan@example.org");
        addPerson("ewhite", "Eve White", "1005", "eve@example.org");
        server.add("dn: uid=fblack," + PEOPLE,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: fblack",
                "cn: Fay Black",
                "sn: Black",
                "jpegPhoto:: " + Base64.getEncoder().encodeToString(PHOTO));
        // A subtree that refers searches to another server, kept apart so other searches see no referral.
        server.add("dn: " + PARTNERS, "objectClass: organizationalUnit", "ou: Partners");
        server.add("dn: ou=Elsewhere," + PARTNERS,
                "objectClass: top",
                "objectClass: referral",
                "objectClass: extensibleObject",
                "ou: Elsewhere",
                "ref: ldap://other.example.org:389/ou=Elsewhere," + PARTNERS);
        server.add("dn: " + MANY, "objectClass: organizationalUnit", "ou: Many");
        for (int i = 0; i < MANY_ENTRIES; i++) {
            server.add("dn: uid=user" + i + "," + MANY, "objectClass: account", "uid: user" + i);
        }
        server.startListening();
    }

    // A self-signed certificate naming one host, in a PKCS12 key store of its own.
    private static File generateCertificate(final Path dir, final String host) {
        final File keyStore = dir.resolve(host + ".p12").toFile();
        final ResultCode generated = ManageCertificates.main(
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                OutputStream.nullOutputStream(),
                "generate-self-signed-certificate",
                "--keystore", keyStore.getAbsolutePath(),
                "--keystore-password", new String(certificatePin),
                "--keystore-type", "PKCS12",
                "--alias", "directory",
                "--subject-dn", "CN=" + host,
                "--subject-alternative-name-dns", host);
        assertThat(generated).isEqualTo(ResultCode.SUCCESS);
        return keyStore;
    }

    private static SSLUtil serverSsl(final File keyStore) throws Exception {
        return new SSLUtil(
                new KeyStoreKeyManager(keyStore, certificatePin, "PKCS12", "directory"),
                new TrustAllTrustManager());
    }

    private static void addPerson(final String uid,
                                  final String name,
                                  final String employeeNumber,
                                  final String... mail) throws Exception {
        final List<String> lines = new ArrayList<>(List.of(
                "dn: uid=" + uid + "," + PEOPLE,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: " + uid,
                "cn: " + name,
                "sn: " + name.substring(name.indexOf(' ') + 1),
                "employeeNumber: " + employeeNumber));
        Arrays.stream(mail).forEach(address -> lines.add("mail: " + address));
        server.add(lines.toArray(String[]::new));
    }

    @AfterAll
    static void stopDirectory() {
        server.shutDown(true);
    }

    private static String plainUrl() {
        return "ldap://" + HOST + ":" + server.getListenPort("plain");
    }

    private static String secureUrl() {
        return "ldaps://" + HOST + ":" + server.getListenPort("secure");
    }

    private static DataConnectionConfig config(final boolean allowPlainLdap) {
        return TestLdapConnectionValidator.config(List.of("^ldaps?://" + HOST + ":\\d+$"), allowPlainLdap);
    }

    private static LdapConnectionFactory trustingFactory() {
        return new LdapConnectionFactory(new TrustStoreTrustManager(certificate, certificatePin, "PKCS12", true));
    }

    private LdapConnector connector(final DataConnectionConfig config, final LdapConnectionFactory factory) {
        final StoredSecret storedSecret = new StoredSecret(
                Mockito.mock(Credential.class),
                new UsernamePasswordSecret(BIND_DN, BIND_PASSWORD),
                null);
        Mockito.when(storedSecrets.get(CREDENTIAL_NAME)).thenReturn(storedSecret);
        return new LdapConnector(
                new LdapConnectionResolver(() -> config, () -> storedSecrets),
                factory,
                new LdapQueryRunner(factory),
                () -> config);
    }

    private LdapConnector connector() {
        return connector(config(true), trustingFactory());
    }

    private static DataConnectionDoc doc(final UnaryOperator<LdapSettings.Builder> changes) {
        return DataConnectionDoc.builder()
                .uuid("uuid-1")
                .name("Staff")
                .sourceType(DataConnectionType.LDAP)
                .ldap(changes.apply(LdapSettings.builder()
                                .url(plainUrl())
                                .credentialName(CREDENTIAL_NAME)
                                .baseDn(PEOPLE)
                                .attributes(ATTRIBUTES)
                                .pageSize(2))
                        .build())
                .build();
    }

    private static DataConnectionDoc doc() {
        return doc(UnaryOperator.identity());
    }

    private static Query query(final String filter) {
        return Query.builder().nativeQuery(filter).build();
    }

    // Runs a prepared query with the given fields, returning the rows that passed.
    private static List<Val[]> run(final PreparedQuery prepared,
                                   final List<QueryRunSummary> summaries,
                                   final List<String> progress,
                                   final QueryCancellation cancellation,
                                   final String... fields) throws Exception {
        final FieldIndex fieldIndex = new FieldIndex();
        for (final String field : fields) {
            fieldIndex.create(field);
        }
        final List<Val[]> rows = new ArrayList<>();
        summaries.add(prepared.run().run(
                columns -> SqlRowConsumer.create(
                        columns,
                        fieldIndex,
                        prepared.expression(),
                        DateTimeSettings.builder().build(),
                        new ExpressionPredicateFactory(),
                        rows::add),
                cancellation,
                progress::add));
        return rows;
    }

    private static List<Val[]> run(final PreparedQuery prepared,
                                   final List<QueryRunSummary> summaries,
                                   final String... fields) throws Exception {
        return run(prepared, summaries, new ArrayList<>(), new QueryCancellation(), fields);
    }

    private static List<String> texts(final List<Val[]> rows, final int field) {
        return rows.stream().map(row -> row[field].toString()).toList();
    }

    @Test
    void takesAFilterInEachQuery() {
        assertThat(connector().takesNativeQuery()).isTrue();
        assertThat(connector().getType()).isEqualTo(DataConnectionType.LDAP);
    }

    @Test
    void fieldsAreTheDnAndTheAttributes() {
        assertThat(connector().getFields(doc(), "(objectClass=person)"))
                .extracting(QueryField::getFldName, QueryField::getFldType)
                .containsExactly(
                        tuple("dn", FieldType.TEXT),
                        tuple("cn", FieldType.TEXT),
                        tuple("mail", FieldType.TEXT),
                        tuple("employeeNumber", FieldType.LONG),
                        tuple("createTimestamp", FieldType.DATE));
    }

    @Test
    void fieldsNeedAValidFilter() {
        assertThatThrownBy(() -> connector().getFields(doc(), "(objectClass=person"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("The LDAP filter is not valid");
    }

    @Test
    void minimumRefreshIsTheDocumentsOrNone() {
        assertThat(connector().getMinRefreshMinutes(doc())).isZero();
        assertThat(connector().getMinRefreshMinutes(doc(settings -> settings.minRefreshMinutes(3)))).isEqualTo(3);
    }

    @Test
    void everyPageOfEntriesIsRead() throws Exception {
        final List<QueryRunSummary> summaries = new ArrayList<>();
        final List<String> progress = new ArrayList<>();
        final List<Val[]> rows = run(connector().prepare(doc(), query("(objectClass=person)"), null),
                summaries, progress, new QueryCancellation(), "dn", "cn", "mail");

        assertThat(texts(rows, 1)).containsExactlyInAnyOrder(
                "Alice Smith", "Bob Jones", "Carol Brown", "Dan Green", "Eve White", "Fay Black");
        assertThat(texts(rows, 0)).contains("uid=asmith," + PEOPLE);
        assertThat(progress).as("six entries in pages of two").hasSize(3);
        assertThat(summaries.getFirst().rowsRead()).isEqualTo(6);
        assertThat(summaries.getFirst().limitReached()).isFalse();
    }

    @Test
    void severalValuesAreJoined() throws Exception {
        final List<Val[]> rows = run(connector().prepare(doc(), query("(uid=asmith)"), null),
                new ArrayList<>(), "mail");

        assertThat(texts(rows, 0)).containsExactly("alice@example.org; a.smith@example.org");
    }

    @Test
    void valuesNotOfTheirTypeAreEmptyWithAWarning() throws Exception {
        final List<QueryRunSummary> summaries = new ArrayList<>();
        final List<Val[]> rows = run(connector().prepare(doc(), query("(uid=cbrown)"), null),
                summaries, "employeeNumber");

        assertThat(rows.getFirst()[0]).isEqualTo(ValNull.INSTANCE);
        assertThat(summaries.getFirst().warnings())
                .singleElement()
                .asString()
                .contains("'x12' in column 'employeeNumber' (Long)");
    }

    @Test
    void binaryAttributesAreBase64() throws Exception {
        final LdapConnector connector = connector();
        final DataConnectionDoc doc = doc(settings -> settings.attributes(List.of(
                new DataColumn("jpegPhoto", DataColumnType.TEXT))));
        final List<Val[]> rows = run(connector.prepare(doc, query("(uid=fblack)"), null),
                new ArrayList<>(), "jpegPhoto");

        assertThat(texts(rows, 0)).containsExactly(Base64.getEncoder().encodeToString(PHOTO));
    }

    @Test
    void parameterCannotWidenTheSearch() throws Exception {
        final LdapConnector connector = connector();
        final Query matching = Query.builder()
                .nativeQuery("(uid=:user)")
                .params(List.of(new Param("user", "bjones")))
                .build();
        final Query injecting = matching.copy()
                .params(List.of(new Param("user", "*)(uid=*")))
                .build();

        assertThat(run(connector.prepare(doc(), matching, null), new ArrayList<>(), "cn")).hasSize(1);
        assertThat(run(connector.prepare(doc(), injecting, null), new ArrayList<>(), "cn"))
                .as("the value is matched as text, so finds no one")
                .isEmpty();
    }

    @Test
    void filterTakesTheTimeRange() throws Exception {
        final LdapConnector connector = connector();
        final String filter = "(&(objectClass=person)(createTimestamp>=:from))";

        assertThat(run(connector.prepare(doc(), timeRangeQuery(filter, "now() - 1h"), null),
                new ArrayList<>(), "cn")).hasSize(6);
        assertThat(run(connector.prepare(doc(), timeRangeQuery(filter, "now() + 1d"), null),
                new ArrayList<>(), "cn")).isEmpty();
    }

    @Test
    void timeColumnFiltersInStroomWithoutPlaceholders() throws Exception {
        final LdapConnector connector = connector();
        final DataConnectionDoc doc = doc().copy().timeField("createTimestamp").build();

        assertThat(run(connector.prepare(doc, timeRangeQuery("(objectClass=person)", "now() - 1h"), null),
                new ArrayList<>(), "cn")).hasSize(6);
        assertThat(run(connector.prepare(doc, timeRangeQuery("(objectClass=person)", "now() + 1d"), null),
                new ArrayList<>(), "cn")).isEmpty();
    }

    @Test
    void timeRangeIsIgnoredWithAWarningWhenNothingCanTakeIt() {
        final PreparedQuery prepared = connector().prepare(doc(), timeRangeQuery("(objectClass=person)",
                "now() - 1h"), null);

        assertThat(prepared.warnings()).containsExactly(LdapConnector.TIME_RANGE_IGNORED);
    }

    @Test
    void timeColumnMustBeADateAttribute() {
        final DataConnectionDoc notDate = doc().copy().timeField("cn").build();
        final DataConnectionDoc notListed = doc().copy().timeField("modifyTimestamp").build();

        assertThatThrownBy(() -> connector().prepare(notDate, timeRangeQuery("(cn=*)", "now() - 1h"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a Date attribute");
        assertThatThrownBy(() -> connector().prepare(notListed, timeRangeQuery("(cn=*)", "now() - 1h"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not an attribute");
    }

    @Test
    void rowLimitStopsTheSearch() throws Exception {
        final List<QueryRunSummary> summaries = new ArrayList<>();
        final DataConnectionDoc doc = doc().copy().maxRows(3).build();
        final List<Val[]> rows = run(connector().prepare(doc, query("(objectClass=person)"), null),
                summaries, "cn");

        assertThat(rows).hasSize(3);
        assertThat(summaries.getFirst().limitReached()).isTrue();
    }

    @Test
    void referralsAreReportedNotFollowed() throws Exception {
        final List<QueryRunSummary> summaries = new ArrayList<>();
        final DataConnectionDoc partners = doc(settings -> settings.baseDn(PARTNERS));

        final List<Val[]> rows = run(connector().prepare(partners, query("(objectClass=*)"), null), summaries, "dn");

        assertThat(texts(rows, 0)).containsExactly(PARTNERS);
        assertThat(summaries.getFirst().warnings())
                .singleElement()
                .asString()
                .startsWith("1 referral was not followed.");
    }

    @Test
    void directorySizeLimitEndsTheSearchWithAWarning() throws Exception {
        // A directory of its own that returns at most two entries for any search.
        final InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE);
        config.addAdditionalBindCredentials(BIND_DN, BIND_PASSWORD);
        config.setMaxSizeLimit(2);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("plain", InetAddress.getLoopbackAddress(), 0,
                null));
        final InMemoryDirectoryServer limited = new InMemoryDirectoryServer(config);
        try {
            limited.add("dn: " + BASE, "objectClass: domain", "dc: example");
            for (final String uid : List.of("a", "b", "c")) {
                limited.add("dn: uid=" + uid + "," + BASE,
                        "objectClass: account",
                        "uid: " + uid);
            }
            limited.startListening();
            final String url = "ldap://" + HOST + ":" + limited.getListenPort("plain");
            final DataConnectionDoc doc = doc(settings -> settings.url(url).baseDn(BASE).pageSize(10));
            final List<QueryRunSummary> summaries = new ArrayList<>();

            final List<Val[]> rows = run(connector().prepare(doc, query("(objectClass=account)"), null),
                    summaries, "dn");

            assertThat(rows).hasSize(2);
            assertThat(summaries.getFirst().limitReached()).as("Stroom's own limit was not reached").isFalse();
            assertThat(summaries.getFirst().warnings())
                    .singleElement()
                    .asString()
                    .startsWith("The directory's own size limit ended the search after 2 entries.");
        } finally {
            limited.shutDown(true);
        }
    }

    @Test
    void scopeLimitsHowFarTheSearchReaches() throws Exception {
        final DataConnectionDoc base = doc(settings -> settings.baseDn(BASE).scope(LdapSearchScope.ONE_LEVEL));

        assertThat(run(connector().prepare(base, query("(objectClass=*)"), null), new ArrayList<>(), "dn"))
                .extracting(row -> row[0].toString())
                .as("the entries one level down, not the people below them")
                .containsExactlyInAnyOrder(PEOPLE, PARTNERS, MANY);
    }

    @Test
    void listingTheSameAttributeTwiceIsRefused() {
        final DataConnectionDoc doc = doc(settings -> settings.attributes(List.of(
                new DataColumn("cn", DataColumnType.TEXT),
                new DataColumn("CN", DataColumnType.TEXT))));

        assertThatThrownBy(() -> connector().prepare(doc, query("(cn=*)"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than once");
    }

    @Test
    // In a thread of its own, so a hang fails the test rather than stopping the build.
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    void rowLimitPartWayThroughAFullPageDoesNotHang() throws Exception {
        // Regression: closing the entry source with its queue full waited forever.
        final DataConnectionDoc many = doc(settings -> settings.baseDn(MANY).pageSize(null)
                .attributes(List.of(new DataColumn("uid", DataColumnType.TEXT))))
                .copy()
                .maxRows(3)
                .build();
        final List<Val[]> rows = new ArrayList<>();

        final QueryRunSummary summary = runSlowly(connector().prepare(many, query("(objectClass=account)"), null),
                new QueryCancellation(), rows::add);

        assertThat(rows).hasSize(3);
        assertThat(summary.limitReached()).isTrue();
    }

    @Test
    // In a thread of its own, so a hang fails the test rather than stopping the build.
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    void cancellingPartWayThroughAFullPageStopsTheSearch() throws Exception {
        final DataConnectionDoc many = doc(settings -> settings.baseDn(MANY).pageSize(null)
                .attributes(List.of(new DataColumn("uid", DataColumnType.TEXT))));
        final QueryCancellation cancellation = new QueryCancellation();
        final List<Val[]> rows = new ArrayList<>();

        final QueryRunSummary summary = runSlowly(connector().prepare(many, query("(objectClass=account)"), null),
                cancellation, row -> {
                    rows.add(row);
                    cancellation.cancel();
                });

        assertThat(rows).hasSize(1);
        assertThat(summary.rowsRead()).isEqualTo(1);
    }

    // Runs a query of uid, pausing on the first row so the directory fills the entry source's queue, as a
    // slow consumer or a large directory would.
    private static QueryRunSummary runSlowly(final PreparedQuery prepared,
                                             final QueryCancellation cancellation,
                                             final Consumer<Val[]> consumer) throws Exception {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("uid");
        final AtomicBoolean paused = new AtomicBoolean();
        return prepared.run().run(
                columns -> SqlRowConsumer.create(
                        columns,
                        fieldIndex,
                        prepared.expression(),
                        DateTimeSettings.builder().build(),
                        new ExpressionPredicateFactory(),
                        row -> {
                            if (!paused.getAndSet(true)) {
                                ThreadUtil.sleep(500);
                            }
                            consumer.accept(row);
                        }),
                cancellation,
                message -> {
                });
    }

    @Test
    void cancelledSearchStopsQuietly() throws Exception {
        final QueryCancellation cancellation = new QueryCancellation();
        cancellation.cancel();
        final List<QueryRunSummary> summaries = new ArrayList<>();

        final List<Val[]> rows = run(connector().prepare(doc(), query("(objectClass=person)"), null),
                summaries, new ArrayList<>(), cancellation, "cn");

        assertThat(rows).isEmpty();
        assertThat(summaries.getFirst().rowsRead()).isZero();
    }

    @Test
    void plainLdapIsRefusedUnlessAllowed() {
        assertThatThrownBy(() -> connector(config(false), trustingFactory())
                .prepare(doc(), query("(cn=*)"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataConnection.allowPlainLdap");
    }

    @Test
    void startTlsAndLdapsConnect() {
        final LdapConnector connector = connector(config(false), trustingFactory());

        assertThat(connector.test(doc(settings -> settings.startTls(true))).getMessage())
                .startsWith("Connected to " + plainUrl() + " with StartTLS");
        assertThat(connector.test(doc(settings -> settings.url(secureUrl()))).getMessage())
                .startsWith("Connected to " + secureUrl());
    }

    @Test
    void certificateMustNameTheHost() {
        // Trusting every certificate, so only the host name check can refuse it.
        final LdapConnector connector = connector(config(false), new LdapConnectionFactory(new TrustAllTrustManager()));
        final String misnamed = "ldaps://" + HOST + ":" + server.getListenPort("misnamed");

        final DataConnectionTestResponse response = connector.test(doc(settings -> settings.url(misnamed)));

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("Hostname verification failed");
    }

    @Test
    void untrustedCertificateIsRefused() {
        final LdapConnector connector = connector(config(false),
                new LdapConnectionFactory(JVMDefaultTrustManager.getInstance()));

        final DataConnectionTestResponse response = connector.test(doc(settings -> settings.url(secureUrl())));

        assertThat(response.isOk()).isFalse();
    }

    @Test
    void testReportsWhatItConnectedTo() {
        final DataConnectionTestResponse response = connector().test(doc());

        assertThat(response.isOk()).isTrue();
        assertThat(response.getMessage())
                .contains("Connected to " + plainUrl())
                .contains("Bound as: " + BIND_DN)
                .contains("Base DN: " + PEOPLE + ", found")
                .contains("Paged results: supported, 2 entries a page");
    }

    @Test
    void testReportsAMissingBaseDn() {
        final DataConnectionTestResponse response = connector().test(doc(settings -> settings.baseDn(
                "ou=Nobody," + BASE)));

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("was not found");
    }

    @Test
    void wrongPasswordIsReported() {
        final LdapConnector connector = connector();
        Mockito.when(storedSecrets.get(CREDENTIAL_NAME)).thenReturn(new StoredSecret(
                Mockito.mock(Credential.class),
                new UsernamePasswordSecret(BIND_DN, "wrong"),
                null));

        final DataConnectionTestResponse response = connector.test(doc());

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).startsWith("LDAP invalid credentials");
    }

    @Test
    void emptyPasswordIsRefusedAsItWouldBindAnonymously() {
        final LdapConnector connector = connector();
        Mockito.when(storedSecrets.get(CREDENTIAL_NAME)).thenReturn(new StoredSecret(
                Mockito.mock(Credential.class),
                new UsernamePasswordSecret(BIND_DN, ""),
                null));

        assertThatThrownBy(() -> connector.prepare(doc(), query("(cn=*)"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must hold both a bind DN");
    }

    @Test
    void credentialTheUserMayNotUseIsRefused() {
        final LdapConnector connector = connector();
        Mockito.when(storedSecrets.get(CREDENTIAL_NAME)).thenThrow(new PermissionException(null, "no"));

        assertThatThrownBy(() -> connector.getFields(doc(), "(cn=*)"))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("Unable to use credential '" + CREDENTIAL_NAME + "'");
    }

    @Test
    void detectAttributesListsTextAttributesAndKeepsChosenTypes() {
        final DataConnectionDoc doc = doc(settings -> settings.attributes(List.of(
                new DataColumn("uid", DataColumnType.DOUBLE))));

        final List<DataColumn> detected = connector().detectAttributes(doc);

        assertThat(detected)
                .extracting(DataColumn::getName)
                .contains("objectClass", "uid", "cn", "sn", "mail", "employeeNumber")
                .doesNotContain("jpegPhoto");
        assertThat(detected)
                .filteredOn(column -> column.getName().equals("uid"))
                .extracting(DataColumn::getType)
                .as("a type already chosen is kept")
                .containsExactly(DataColumnType.DOUBLE);
        assertThat(detected)
                .filteredOn(column -> column.getName().equals("mail"))
                .extracting(DataColumn::getType)
                .containsExactly(DataColumnType.TEXT);
    }

    @Test
    void detectAttributesKeepsTheAttributesAlreadyListed() {
        // pwdChangedTime is on no entry, as if added by name; CN differs in case from the entries' cn.
        final DataConnectionDoc doc = doc(settings -> settings.attributes(List.of(
                new DataColumn("pwdChangedTime", DataColumnType.DATE),
                new DataColumn("CN", DataColumnType.TEXT))));

        final List<DataColumn> detected = connector().detectAttributes(doc);

        assertThat(detected.subList(0, 2))
                .as("the listed attributes stay first, in their order, with their types")
                .containsExactly(
                        new DataColumn("pwdChangedTime", DataColumnType.DATE),
                        new DataColumn("CN", DataColumnType.TEXT));
        assertThat(detected)
                .extracting(column -> column.getName().toLowerCase())
                .as("cn is not added again in another case")
                .containsOnlyOnce("cn")
                .contains("uid", "mail");
    }

    private static Query timeRangeQuery(final String filter, final String from) {
        return Query.builder()
                .nativeQuery(filter)
                .timeRange(new TimeRange(null, from, "now() + 2d"))
                .build();
    }
}
