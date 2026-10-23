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

package stroom.mcp.impl;

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiRetention;
import stroom.ai.impl.AiSampleUploadDao;
import stroom.ai.impl.AiSampleUploadsConfig;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.shared.AiToolAccess;
import stroom.db.util.JooqUtil;
import stroom.job.api.ScheduledJob;
import stroom.mcp.impl.tools.FeedUploads;
import stroom.mcp.impl.tools.SampleUploads;
import stroom.mcp.impl.tools.SampleUploadsOnNode;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.test.AbstractAiIntegrationTest;
import stroom.test.AppConfigTestModule;
import stroom.test.ConfigMapperSpy;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.ai.impl.db.jooq.tables.AiSampleUploadFile.AI_SAMPLE_UPLOAD_FILE;

/// The sample files upload_sample gives commands for, as a cluster of nodes takes their posts to `/mcp/upload`:
/// the REAL tool gives the commands on one node, and the posts reach the REAL upload servlet on that node or on
/// others (each another instance of the servlet and [SampleUploads], named as its node, with a clock of its own,
/// sharing only the test database). Ports what `dev/e2e_*.py` checked of the upload tickets: a file is stored once,
/// only for the user, feed and file it was given for, until the commands run out.
class TestSampleUploadsAcrossNodes extends AbstractAiIntegrationTest {

    private static final String NODE_1 = AppConfigTestModule.NODE1;
    private static final String NODE_2 = "node2a";
    private static final String NODE_3 = "node3a";
    private static final String CSV = "time,user\n2026-10-23T10:00:00,alice\n";
    private static final String NO_UPLOAD_WAITS = "No upload waits for this file";
    private static final Pattern UPLOAD_ID = Pattern.compile(Pattern.quote(SampleUploads.PATH) + "/([^?']+)\\?file=");
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    @Inject
    private McpTools mcpTools;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private AiSampleUploadDao dao;
    @Inject
    private Provider<AiConfig> aiConfigProvider;
    @Inject
    private Provider<FeedUploads> feedUploadsProvider;
    @Inject
    private ConfigMapperSpy configMapperSpy;
    @Inject
    private Map<ScheduledJob, Provider<Runnable>> scheduledJobs;
    @Inject
    private AiDbConnProvider aiDbConnProvider;
    @Inject
    private Injector injector;

    private String suffix;
    private UserRef uploader;
    private McpTestClient client;
    private String feed;
    private String feedUuid;

    @BeforeEach
    void setUp() {
        // Names of this test's own, as what earlier tests made may still be there.
        suffix = UUID.randomUUID().toString().substring(0, 8);
        uploader = user("uploader-" + suffix, Set.of(AppPermission.USE_MCP_TOOLS, AppPermission.ADMINISTRATOR));
        toolPolicy("Uploads " + suffix, AiToolAccess.READ_WRITE, uploader);
        client = new McpTestClient(mcpTools, securityContext, uploader, true);
        feed = "ACME-UPLOAD-" + suffix.toUpperCase();
        final Map<String, Object> created = client.json("create_feed", Map.of("build", "acme-upload-" + suffix,
                "name", feed));
        feedUuid = (String) created.get("uuid");
    }

    @AfterEach
    void tearDown() {
        configMapperSpy.setConfigValueMappers(Map.of());
    }

    @Test
    void aFileOfCommandsGivenOnOneNodeIsStoredByAnotherAndOnlyOnce() {
        final String id = issue("a.csv", "b.csv");

        // The commands were given on node 1; the post of a.csv reaches node 2.
        final Posted onNode2 = post(servletOn(NODE_2, System::currentTimeMillis), uploader, id, "a.csv");
        assertThat(onNode2.status()).as(onNode2.toString()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(onNode2.body()).containsEntry("feed", feed).containsEntry("file", "a.csv");
        assertThat(onNode2.body().get("stream_id")).isInstanceOf(Number.class);

        // Stored, so it is never taken again, on either node.
        assertRefused(post(servletOnThisNode(), uploader, id, "a.csv"));
        assertRefused(post(servletOn(NODE_2, System::currentTimeMillis), uploader, id, "a.csv"));
        // Once the claim that stored it would long be stale, too.
        assertRefused(post(servletOn(NODE_3, later(Duration.ofMinutes(11))), uploader, id, "a.csv"));

        // The set's other file is still waiting, and is taken on the node that gave the commands.
        assertStored(post(servletOnThisNode(), uploader, id, "b.csv"), "b.csv");
    }

    @Test
    void twoNodesClaimingAFileAtOnceOneTakesItAndTheOtherIsRefused() throws Exception {
        final List<String> files = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            files.add("sample-" + i + ".csv");
        }
        final String id = issue(files.toArray(String[]::new));
        final SampleUploads node1 = uploadsOn(NODE_1, System::currentTimeMillis);
        final SampleUploads node2 = uploadsOn(NODE_2, System::currentTimeMillis);

        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (final String file : files) {
                final List<Optional<SampleUploads.Claim>> claims = race(executor,
                        () -> node1.claim(id, uploader.getUuid(), file),
                        () -> node2.claim(id, uploader.getUuid(), file));
                assertThat(claims).as(file).filteredOn(Optional::isPresent).hasSize(1);
            }

            // The same through the servlets: the post that takes the file stores it, the other is told no upload
            // waits for it, whether it came while the first was storing it or after.
            final String posted = issue("x.csv", "y.csv", "z.csv");
            for (final String file : List.of("x.csv", "y.csv", "z.csv")) {
                final List<Posted> answers = race(executor,
                        () -> post(servletOnThisNode(), uploader, posted, file),
                        () -> post(servletOn(NODE_2, System::currentTimeMillis), uploader, posted, file));
                assertThat(answers).as(file).extracting(Posted::status)
                        .containsExactlyInAnyOrder(HttpServletResponse.SC_OK, HttpServletResponse.SC_FORBIDDEN);
                assertThat(answers).filteredOn(answer -> answer.status() == HttpServletResponse.SC_FORBIDDEN)
                        .singleElement()
                        .satisfies(answer -> assertThat((String) answer.body().get("error"))
                                .startsWith(NO_UPLOAD_WAITS));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aClaimTenMinutesOldIsTakenOverByAnotherNodeAndAFresherOneIsNot() {
        final String id = issue("a.csv");
        final long claimedMs = System.currentTimeMillis();
        // Node 1 takes the post, then stops part way through it, never storing the file.
        final SampleUploads node1 = uploadsOn(NODE_1, () -> claimedMs);
        final SampleUploads.Claim stopped = node1.claim(id, uploader.getUuid(), "a.csv").orElseThrow();

        // The file is posted again on node 2 while node 1 might still be storing it: refused.
        assertRefused(post(servletOn(NODE_2, at(claimedMs, Duration.ofMinutes(9))), uploader, id, "a.csv"));
        assertRefused(post(servletOn(NODE_2, at(claimedMs, TEN_MINUTES)), uploader, id, "a.csv"));
        // Once the claim is more than ten minutes old, node 2 takes the file over and stores it.
        assertStored(post(servletOn(NODE_2, at(claimedMs, TEN_MINUTES.plusSeconds(1))), uploader, id, "a.csv"),
                "a.csv");

        // Node 1's claim no longer holds the file: it can't store it, nor free it to be sent again.
        node1.stored(stopped, 1L);
        node1.release(stopped);
        assertRefused(post(servletOn(NODE_3, at(claimedMs, Duration.ofMinutes(11))), uploader, id, "a.csv"));
    }

    @Test
    void aPostNoCommandsWereGivenForIsRefused() {
        final String id = issue("a.csv");
        final UserRef other = user("other-" + suffix, Set.of(AppPermission.USE_MCP_TOOLS,
                AppPermission.IMPORT_DATA_PERMISSION));
        final McpUploadServlet node2 = servletOn(NODE_2, System::currentTimeMillis);

        // Another user, with the commands given to the uploader.
        assertRefused(post(node2, other, id, "a.csv"));
        // A file the commands weren't given for, and commands never given.
        assertRefused(post(node2, uploader, id, "c.csv"));
        assertRefused(post(node2, uploader, "NoSuchUpload", "a.csv"));
        // The commands have run out (they last 15 minutes).
        assertRefused(post(servletOn(NODE_2, later(Duration.ofMinutes(16))), uploader, id, "a.csv"));

        // None of them took the file: it is still waiting for the uploader.
        assertStored(post(node2, uploader, id, "a.csv"), "a.csv");
    }

    @Test
    void aUserMayHaveOnlyAsManySetsOfCommandsWaitingAsTheConfigAllows() {
        configMapperSpy.setConfigValueMapper(AiConfig.class, config -> AiConfigChanges.withSampleUploads(config,
                new AiSampleUploadsConfig(null, null, 2)));
        final String first = issue("a.csv");
        issue("b.csv");

        final String refused = McpTestClient.text(client.call("upload_sample", Map.of("feed", feed,
                "files", List.of("c.csv"))));
        assertThat(refused).contains("You have 2 sets of upload commands waiting to be run already");

        // Once a set's files have all been sent, it no longer counts.
        assertStored(post(servletOn(NODE_2, System::currentTimeMillis), uploader, first, "a.csv"), "a.csv");
        assertThat(issue("c.csv")).isNotBlank();
    }

    @Test
    void theRetentionJobDeletesSetsThatHaveRunOutWithTheirFilesAndKeepsTheRest() {
        final String live = issue("a.csv");
        // Commands given an hour ago on node 2 (so they ran out 45 minutes ago), one of their files claimed.
        final long anHourAgo = System.currentTimeMillis() - Duration.ofHours(1).toMillis();
        final SampleUploads node2 = uploadsOn(NODE_2, () -> anHourAgo);
        final String expired = node2.issue(uploader.getUuid(), feed, feedUuid, "Raw Events", Map.of(), null,
                List.of("x.csv", "y.csv")).id();
        assertThat(node2.claim(expired, uploader.getUuid(), "x.csv")).isPresent();
        final int expiredSetId = dao.find(expired, 0L).orElseThrow().id();
        final int liveSetId = dao.find(live, 0L).orElseThrow().id();
        assertThat(fileCount(expiredSetId)).isEqualTo(2);

        runJob(AiRetention.JOB_NAME);

        assertThat(dao.find(expired, 0L)).isEmpty();
        assertThat(fileCount(expiredSetId)).isZero();
        // The commands that haven't run out are kept, and still work.
        assertThat(dao.find(live, System.currentTimeMillis())).isPresent();
        assertThat(fileCount(liveSetId)).isOne();
        assertStored(post(servletOn(NODE_2, System::currentTimeMillis), uploader, live, "a.csv"), "a.csv");
    }

    // upload_sample with files=, on this node (node 1), as the uploader: the id in its commands' URL.
    @SuppressWarnings("unchecked")
    private String issue(final String... files) {
        final Map<String, Object> result = client.json("upload_sample", Map.of("feed", feed,
                "files", List.of(files)));
        final List<Map<String, String>> commands = (List<Map<String, String>>) result.get("commands");
        assertThat(commands).extracting(command -> command.get("file")).containsExactly(files);
        final Matcher matcher = UPLOAD_ID.matcher(commands.getFirst().get("bash"));
        assertThat(matcher.find()).as(commands.getFirst().get("bash")).isTrue();
        return matcher.group(1);
    }

    // The upload servlet of this node, as Stroom has it.
    private McpUploadServlet servletOnThisNode() {
        return injector.getInstance(McpUploadServlet.class);
    }

    // The upload servlet of another node: its own servlet and sample uploads, sharing only the database.
    private McpUploadServlet servletOn(final String node, final LongSupplier clock) {
        final SampleUploads uploads = uploadsOn(node, clock);
        return new McpUploadServlet(() -> uploads, feedUploadsProvider, securityContext, aiConfigProvider);
    }

    private SampleUploads uploadsOn(final String node, final LongSupplier clock) {
        return SampleUploadsOnNode.create(aiConfigProvider, dao, node, clock);
    }

    // A post of a file to /mcp/upload/<id>?file=<file>, as the user (signed in, as the security filter does).
    private Posted post(final McpUploadServlet servlet, final UserRef user, final String id, final String file) {
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        final HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        final StringWriter body = new StringWriter();
        final int[] status = new int[]{HttpServletResponse.SC_OK};
        try {
            Mockito.when(request.getMethod()).thenReturn("POST");
            Mockito.when(request.getPathInfo()).thenReturn("/" + id);
            Mockito.when(request.getQueryString()).thenReturn(McpUploadServlet.FILE_PARAMETER + "="
                                                              + URLEncoder.encode(file, StandardCharsets.UTF_8));
            Mockito.when(request.getContentType()).thenReturn("application/octet-stream");
            Mockito.when(request.getInputStream()).thenReturn(new BodyStream(CSV));
            Mockito.when(response.getWriter()).thenReturn(new PrintWriter(body, true));
            Mockito.doAnswer(invocation -> {
                status[0] = invocation.getArgument(0);
                return null;
            }).when(response).setStatus(Mockito.anyInt());
            asUser(user, () -> {
                try {
                    servlet.service(request, response);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        @SuppressWarnings("unchecked") final Map<String, Object> json = JsonUtil.readValue(body.toString(),
                Map.class);
        return new Posted(status[0], json);
    }

    // Runs two pieces of work at once, as two nodes would, and gives what each gave.
    private static <T> List<T> race(final ExecutorService executor,
                                    final Supplier<T> first,
                                    final Supplier<T> second) {
        final CyclicBarrier start = new CyclicBarrier(2);
        final List<Future<T>> futures = new ArrayList<>();
        for (final Supplier<T> work : List.of(first, second)) {
            futures.add(executor.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                return work.get();
            }));
        }
        final List<T> results = new ArrayList<>();
        for (final Future<T> future : futures) {
            try {
                results.add(future.get(60, TimeUnit.SECONDS));
            } catch (final Exception e) {
                throw new IllegalStateException("A racing request failed: " + e.getMessage(), e);
            }
        }
        return results;
    }

    private void runJob(final String name) {
        final Runnable job = scheduledJobs.entrySet().stream()
                .filter(entry -> name.equals(entry.getKey().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No job named " + name))
                .getValue()
                .get();
        securityContext.asProcessingUser(job);
    }

    private int fileCount(final int setId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context.fetchCount(AI_SAMPLE_UPLOAD_FILE,
                AI_SAMPLE_UPLOAD_FILE.FK_AI_SAMPLE_UPLOAD_ID.eq(setId)));
    }

    private static LongSupplier later(final Duration after) {
        final long now = System.currentTimeMillis();
        return at(now, after);
    }

    private static LongSupplier at(final long fromMs, final Duration after) {
        final long timeMs = fromMs + after.toMillis();
        return () -> timeMs;
    }

    private static void assertRefused(final Posted posted) {
        assertThat(posted.status()).as(posted.toString()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat((String) posted.body().get("error")).startsWith(NO_UPLOAD_WAITS);
    }

    private void assertStored(final Posted posted, final String file) {
        assertThat(posted.status()).as(posted.toString()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(posted.body()).containsEntry("feed", feed).containsEntry("file", file);
        assertThat(posted.body().get("stream_id")).isInstanceOf(Number.class);
    }


    // --------------------------------------------------------------------------------


    /// What the upload servlet answered a post with.
    ///
    /// @param status The HTTP status.
    /// @param body   The JSON body.
    private record Posted(int status, Map<String, Object> body) {

    }

    // A post's body, as the servlet container gives it.
    private static final class BodyStream extends ServletInputStream {

        private final ByteArrayInputStream body;

        private BodyStream(final String body) {
            this.body = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public boolean isFinished() {
            return body.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
            throw new UnsupportedOperationException("Not read asynchronously");
        }

        @Override
        public int read() {
            return body.read();
        }

        @Override
        public int read(final byte[] bytes, final int offset, final int length) {
            return body.read(bytes, offset, length);
        }
    }
}
