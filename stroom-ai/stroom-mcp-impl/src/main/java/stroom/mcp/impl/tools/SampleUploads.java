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

package stroom.mcp.impl.tools;

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiSampleUploadDao;
import stroom.ai.impl.AiSampleUploadDao.UploadSet;
import stroom.ai.impl.AiSampleUploadsConfig;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.node.api.NodeInfo;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/// The sample files upload_sample has given commands for, waiting to be posted to `/mcp/upload` (in place of the
/// Stroom MCP server's signed upload tickets, `utils/uploads.py` `UploadTickets`). The post is made as the user,
/// signed in as for any other request, so it needs no ticket; what the ticket bound is kept here instead, so a post
/// lands only where upload_sample said: for the user it was given to, the feed, its stream type and receipt
/// headers, the build, each file named once, until it runs out (`stroom.ai.sampleUploads.commandsLast`).
///
/// Held in the database ([AiSampleUploadDao]), so a post may reach any node, not only the one that gave the
/// commands, and a restart doesn't forget them. A post claims its file first, so two posts of one file (on one
/// node or two) don't both store it; a claim left by a node that stopped part way through a post may be taken
/// again once it is [#CLAIM_GOES_STALE] old. Sets that have run out are deleted by the 'AI Chat Retention' job.
@Singleton
public class SampleUploads {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SampleUploads.class);

    /// Where the files are posted: `/mcp/upload/<id>?file=<the file's name>`.
    public static final String PATH = ResourcePaths.MCP_PATH + "/upload";
    /// How long a post's claim on its file keeps other posts of the file away. Long enough for the largest file
    /// over a slow link (100 MB at under 200 KB/s), so a post still being stored isn't stored twice, and short
    /// enough that a file whose post was cut off by its node stopping can be sent again within the commands'
    /// default 15 minutes.
    public static final Duration CLAIM_GOES_STALE = Duration.ofMinutes(10);
    /// The longest file name (path) a command may carry, as the database holds it.
    static final int MAX_FILE_NAME_CHARS = 700;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Provider<AiConfig> aiConfigProvider;
    private final AiSampleUploadDao dao;
    private final String node;
    private final LongSupplier clock;

    /// @param aiConfigProvider Says how long the commands last, and how large a file may be.
    /// @param dao              Holds the waiting files.
    /// @param nodeInfo         Names this node, for its claims.
    @Inject
    public SampleUploads(final Provider<AiConfig> aiConfigProvider,
                         final AiSampleUploadDao dao,
                         final NodeInfo nodeInfo) {
        this(aiConfigProvider, dao, nodeInfo.getThisNodeName(), System::currentTimeMillis);
    }

    /// @param aiConfigProvider Says how long the commands last, and how large a file may be.
    /// @param dao              Holds the waiting files.
    /// @param node             This node's name, for its claims.
    /// @param clock            The time now, in milliseconds.
    SampleUploads(final Provider<AiConfig> aiConfigProvider,
                  final AiSampleUploadDao dao,
                  final String node,
                  final LongSupplier clock) {
        this.aiConfigProvider = aiConfigProvider;
        this.dao = dao;
        this.node = node;
        this.clock = clock;
    }

    /// Keeps the files upload_sample gave commands for.
    ///
    /// @param user       The user they were given to (their user UUID).
    /// @param feed       The feed's name.
    /// @param feedUuid   Its UUID.
    /// @param streamType The stream type to store them as.
    /// @param headers    Their receipt headers.
    /// @param build      The feed's build, or null.
    /// @param files      The files, by the names the commands give.
    /// @return What is waiting, with its id for the commands' URL and when it runs out.
    /// @throws ToolRefusal if the user has as many sets of commands waiting as they may
    ///                     (`stroom.ai.sampleUploads.maxPendingPerUser`), or a file's name is too long to hold.
    public Pending issue(final String user,
                         final String feed,
                         final String feedUuid,
                         final String streamType,
                         final Map<String, String> headers,
                         final String build,
                         final List<String> files) {
        final List<String> names = new ArrayList<>(new LinkedHashSet<>(NullSafe.list(files)));
        checkNameLengths(names);
        final long now = clock.getAsLong();
        final int max = maxPendingPerUser();
        // Counted, then stored: two calls at once (on one node or two) may each find room for one more.
        final int waitingForUser = dao.countWaiting(user, now, staleBefore(now));
        if (waitingForUser >= max) {
            throw new ToolRefusal("You have " + waitingForUser + " sets of upload commands waiting to be run "
                                  + "already, as many as you may. Run those (or let them run out, in "
                                  + lasts().getDuration().toMinutes() + " minutes at most) before asking for more.");
        }
        final byte[] bytes = new byte[9];
        RANDOM.nextBytes(bytes);
        // 12 characters, nothing to quote.
        final String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        final Pending pending = new Pending(id, user, feed, feedUuid, streamType,
                Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(headers))), build,
                now + lasts().toMillis());
        dao.create(new UploadSet(0, id, user, feed, feedUuid, streamType, pending.headers(), build,
                pending.expiresMs()), names, now);
        return pending;
    }

    /// Claims one file's upload for a post, so it is posted only once. The claim is then either [#stored] or
    /// [#release]d.
    ///
    /// @param id   The id in the post's URL.
    /// @param user The user posting (their user UUID).
    /// @param file The file's name, as the command gives it.
    /// @return The claim, with what the file was given for; empty when nothing waits for this user and file under
    /// the id (not given, given to someone else, run out, being posted already, or posted already).
    public Optional<Claim> claim(final String id, final String user, final String file) {
        if (id == null || id.isEmpty() || user == null || file == null) {
            return Optional.empty();
        }
        final long now = clock.getAsLong();
        final Optional<UploadSet> found = dao.find(id, now)
                .filter(set -> set.userUuid().equals(user));
        if (found.isEmpty() || !dao.claim(found.get().id(), file, node, now, staleBefore(now))) {
            return Optional.empty();
        }
        return Optional.of(new Claim(toPending(found.get()), found.get().id(), file, now));
    }

    /// Marks a claimed file stored, so it is never posted again.
    ///
    /// @param claim    The claim.
    /// @param streamId The stream it was stored as, or null if it isn't visible yet.
    public void stored(final Claim claim, final Long streamId) {
        if (!dao.markStored(claim.setId(), claim.file(), node, claim.claimTimeMs(), streamId, clock.getAsLong())) {
            // Only when the post took longer than CLAIM_GOES_STALE and another post took the file over.
            LOGGER.warn(() -> "The claim on sample upload " + claim.pending().id() + " was taken over before it "
                              + "was stored: the file may have been stored twice");
        }
    }

    /// Lets a file be posted again, when storing it failed.
    ///
    /// @param claim The claim.
    public void release(final Claim claim) {
        dao.release(claim.setId(), claim.file(), node, claim.claimTimeMs());
    }

    /// @return The largest file that may be posted, in bytes.
    public long maxBytes() {
        return (long) maxMb() * 1024 * 1024;
    }

    /// @return The largest file that may be posted, in MB.
    public int maxMb() {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getSampleUploads,
                AiSampleUploadsConfig::getMaxUploadMb, new AiSampleUploadsConfig().getMaxUploadMb());
    }

    /// @return The most sets of commands one user may have waiting at once.
    public int maxPendingPerUser() {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getSampleUploads,
                AiSampleUploadsConfig::getMaxPendingPerUser, new AiSampleUploadsConfig().getMaxPendingPerUser());
    }

    /// @return How long commands last.
    public StroomDuration lasts() {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getSampleUploads,
                AiSampleUploadsConfig::getCommandsLast, new AiSampleUploadsConfig().getCommandsLast());
    }

    /// @return The time now, in milliseconds.
    public long now() {
        return clock.getAsLong();
    }

    private static long staleBefore(final long now) {
        return now - CLAIM_GOES_STALE.toMillis();
    }

    private static void checkNameLengths(final List<String> names) {
        for (final String name : names) {
            if (NullSafe.string(name).length() > MAX_FILE_NAME_CHARS) {
                throw new ToolRefusal("A file's path is longer than " + MAX_FILE_NAME_CHARS + " characters: give "
                                      + "it relative to a folder nearer the file, and run its command from there.");
            }
        }
    }

    private static Pending toPending(final UploadSet set) {
        return new Pending(set.token(), set.userUuid(), set.feedName(), set.feedUuid(), set.streamType(),
                set.headers(), set.build(), set.expiryTimeMs());
    }


    // --------------------------------------------------------------------------------


    /// Files waiting to be posted, as upload_sample gave their commands.
    ///
    /// @param id         The id in the commands' URL.
    /// @param user       The user they were given to (their user UUID).
    /// @param feed       The feed's name.
    /// @param feedUuid   Its UUID.
    /// @param streamType The stream type to store them as.
    /// @param headers    Their receipt headers.
    /// @param build      The feed's build, or null.
    /// @param expiresMs  When the commands run out.
    public record Pending(String id,
                          String user,
                          String feed,
                          String feedUuid,
                          String streamType,
                          Map<String, String> headers,
                          String build,
                          long expiresMs) {

    }

    /// A post's claim on one file, held while it is stored.
    ///
    /// @param pending     What the file was given for.
    /// @param setId       The set's id in the database.
    /// @param file        The file's name.
    /// @param claimTimeMs When the claim was made, which identifies it.
    public record Claim(Pending pending, int setId, String file, long claimTimeMs) {

    }
}
