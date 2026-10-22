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
import stroom.ai.impl.AiSampleUploadsConfig;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/// The sample files upload_sample has given commands for, waiting to be posted to `/mcp/upload` (in place of the
/// Stroom MCP server's signed upload tickets, `utils/uploads.py` `UploadTickets`). The post is made as the user,
/// signed in as for any other request, so it needs no ticket; what the ticket bound is kept here instead, so a post
/// lands only where upload_sample said: for the user it was given to, the feed, its stream type and receipt
/// headers, the build, each file named once, until it runs out (`stroom.ai.sampleUploads.commandsLast`).
///
/// Kept in this node's memory, so a restart forgets them (the agent asks for fresh commands) and, behind a load
/// balancer, a post must reach the node that gave the commands, as an MCP session's requests do.
@Singleton
public class SampleUploads {

    /// Where the files are posted: `/mcp/upload/<id>?file=<the file's name>`.
    public static final String PATH = ResourcePaths.MCP_PATH + "/upload";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Provider<AiConfig> aiConfigProvider;
    private final LongSupplier clock;
    private final Map<String, Waiting> waiting = new HashMap<>();

    /// @param aiConfigProvider Says how long the commands last, and how large a file may be.
    @Inject
    public SampleUploads(final Provider<AiConfig> aiConfigProvider) {
        this(aiConfigProvider, System::currentTimeMillis);
    }

    /// @param aiConfigProvider Says how long the commands last, and how large a file may be.
    /// @param clock            The time now, in milliseconds.
    SampleUploads(final Provider<AiConfig> aiConfigProvider, final LongSupplier clock) {
        this.aiConfigProvider = aiConfigProvider;
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
    ///                     (`stroom.ai.sampleUploads.maxPendingPerUser`).
    public synchronized Pending issue(final String user,
                                      final String feed,
                                      final String feedUuid,
                                      final String streamType,
                                      final Map<String, String> headers,
                                      final String build,
                                      final List<String> files) {
        sweep();
        final int max = maxPendingPerUser();
        final long waitingForUser = waiting.values().stream()
                .filter(found -> found.pending().user().equals(user) && !found.files().isEmpty())
                .count();
        if (waitingForUser >= max) {
            throw new ToolRefusal("You have " + waitingForUser + " sets of upload commands waiting to be run "
                                  + "already, as many as you may. Run those (or let them run out, in "
                                  + lasts().getDuration().toMinutes() + " minutes at most) before asking for more.");
        }
        final byte[] bytes = new byte[9];
        RANDOM.nextBytes(bytes);
        // 12 characters, nothing to quote.
        final String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        final Pending pending = new Pending(id, user, feed, feedUuid, streamType, Map.copyOf(NullSafe.map(headers)),
                build, clock.getAsLong() + lasts().toMillis());
        waiting.put(id, new Waiting(pending, new LinkedHashSet<>(files)));
        return pending;
    }

    /// Takes one file's upload, so it is posted only once.
    ///
    /// @param id   The id in the post's URL.
    /// @param user The user posting (their user UUID).
    /// @param file The file's name, as the command gives it.
    /// @return What the file was given for; empty when nothing waits for this user and file under the id (not
    /// given, given to someone else, run out, or already posted).
    public synchronized Optional<Pending> claim(final String id, final String user, final String file) {
        sweep();
        final Waiting found = id == null
                ? null
                : waiting.get(id);
        if (found == null || !found.pending().user().equals(user) || !found.files().remove(file)) {
            return Optional.empty();
        }
        return Optional.of(found.pending());
    }

    /// Lets a file be posted again, when storing it failed.
    ///
    /// @param pending What it was given for.
    /// @param file    The file's name.
    public synchronized void release(final Pending pending, final String file) {
        final Waiting found = waiting.get(pending.id());
        if (found != null) {
            found.files().add(file);
        }
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

    private void sweep() {
        final long now = clock.getAsLong();
        waiting.values().removeIf(found -> found.pending().expiresMs() <= now);
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

    private record Waiting(Pending pending, Set<String> files) {

    }
}
