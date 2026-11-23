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

package stroom.index.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/// Checks that a volume marked as shared really is one mount that every node sees.
///
/// Stroom cannot prove that a path is a shared mount, so each node writes a marker file for itself in a
/// directory at the top of the volume and looks for the markers of the other nodes. Each node rewrites its
/// marker with the time on every check, so a marker that has not been rewritten for a while is as bad as one
/// that is missing. A node that cannot see a recent marker from another running node, after allowing that
/// node time to write it, is looking at a different disk. That is what happens when a local disk is marked
/// shared by mistake, or when a node's mount fails and leaves a local directory in its place.
class SharedVolumeChecker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedVolumeChecker.class);

    static final String MARKER_DIR_NAME = ".stroom_shared_volume";
    private static final String MARKER_FILE_SUFFIX = ".marker";

    // How long another node is given to write its marker before its absence is reported. The check runs with
    // the volume status job, so this allows a node that has just joined a few runs of that job.
    static final Duration DEFAULT_GRACE_PERIOD = Duration.ofMinutes(15);
    // How old a marker may be before it counts as missing. The owner rewrites it on every run of the volume
    // status job, so this allows it to miss a couple of runs.
    static final Duration DEFAULT_STALE_AFTER = Duration.ofMinutes(15);

    private final Duration gracePeriod;
    private final Duration staleAfter;
    // When each missing marker was first found to be missing, so the grace period can be applied.
    private final Map<MarkerKey, Long> firstMissingTimes = new ConcurrentHashMap<>();
    // The nodes last reported for each volume, so a problem is logged when it changes and not on every check.
    private final Map<Integer, List<String>> reportedProblems = new ConcurrentHashMap<>();

    /// Creates a checker that uses the default grace period and marker age.
    SharedVolumeChecker() {
        this(DEFAULT_GRACE_PERIOD, DEFAULT_STALE_AFTER);
    }

    /// Creates a checker.
    ///
    /// @param gracePeriod How long another node's marker may be missing before that is reported.
    /// @param staleAfter  How old a marker may be before it counts as missing.
    SharedVolumeChecker(final Duration gracePeriod, final Duration staleAfter) {
        this.gracePeriod = Objects.requireNonNull(gracePeriod);
        this.staleAfter = Objects.requireNonNull(staleAfter);
    }

    /// Writes this node's marker in a shared volume and looks for the markers of the other nodes.
    ///
    /// @param volumeId     The id of the shared volume.
    /// @param volumePath   The absolute path of the shared volume on this node.
    /// @param thisNodeName The name of this node.
    /// @param otherNodes   The names of the other nodes that are expected to see the volume. These should be
    ///                     the nodes that are running, as a node that is down cannot keep its marker fresh.
    /// @param nowMs        The current time.
    /// @return The other nodes whose markers have been missing or out of date for longer than the grace
    /// period, in name order. An empty list means that nothing shows the volume is not shared.
    List<String> check(final int volumeId,
                       final Path volumePath,
                       final String thisNodeName,
                       final Collection<String> otherNodes,
                       final long nowMs) {
        final Path markerDir = volumePath.resolve(MARKER_DIR_NAME);
        writeMarker(markerDir, thisNodeName, nowMs);

        // Sorted by node name so that the result and the logging are stable.
        final Map<String, Long> missingNodes = new TreeMap<>();
        for (final String otherNode : otherNodes) {
            final MarkerKey key = new MarkerKey(volumeId, otherNode);
            if (Objects.equals(otherNode, thisNodeName) || isMarkerFresh(markerDir, otherNode, nowMs)) {
                firstMissingTimes.remove(key);
            } else {
                missingNodes.put(otherNode, firstMissingTimes.computeIfAbsent(key, k -> nowMs));
            }
        }
        // Forget nodes that are no longer expected, such as ones that have been disabled or removed.
        firstMissingTimes.keySet().removeIf(key ->
                key.volumeId == volumeId && !missingNodes.containsKey(key.nodeName));

        final List<String> problemNodes = missingNodes.entrySet()
                .stream()
                .filter(entry -> nowMs - entry.getValue() >= gracePeriod.toMillis())
                .map(Map.Entry::getKey)
                .toList();
        report(volumeId, volumePath, thisNodeName, problemNodes);
        return problemNodes;
    }

    /// Forgets everything known about a volume, for use when it is no longer a shared volume of this node.
    ///
    /// @param volumeId The id of the volume.
    void forget(final int volumeId) {
        firstMissingTimes.keySet().removeIf(key -> key.volumeId == volumeId);
        reportedProblems.remove(volumeId);
    }

    /// Forgets everything known about volumes other than the ones given, such as volumes that have been
    /// deleted, including any that are still inside the grace period.
    ///
    /// @param volumeIds The ids of the volumes that are still shared volumes of this node.
    void retain(final Collection<Integer> volumeIds) {
        firstMissingTimes.keySet().removeIf(key -> !volumeIds.contains(key.volumeId));
        reportedProblems.keySet().removeIf(volumeId -> !volumeIds.contains(volumeId));
    }

    /// Gets the problems found by the most recent checks.
    ///
    /// @return For each shared volume that has a problem, the nodes whose markers this node cannot see.
    Map<Integer, List<String>> getProblems() {
        return Map.copyOf(reportedProblems);
    }

    private void report(final int volumeId,
                        final Path volumePath,
                        final String thisNodeName,
                        final List<String> problemNodes) {
        final List<String> previous = problemNodes.isEmpty()
                ? reportedProblems.remove(volumeId)
                : reportedProblems.put(volumeId, problemNodes);
        if (!problemNodes.isEmpty() && !problemNodes.equals(previous)) {
            LOGGER.error(() -> LogUtil.message(
                    "Index volume {} with path '{}' is marked as shared but node '{}' cannot see recent marker " +
                    "files of nodes {} in '{}'. The path is probably not the same shared mount on every node. " +
                    "Shards written to a volume that is not really shared cannot be read by other nodes.",
                    volumeId,
                    volumePath,
                    thisNodeName,
                    problemNodes,
                    volumePath.resolve(MARKER_DIR_NAME)));
        } else if (problemNodes.isEmpty() && previous != null) {
            LOGGER.info(() -> LogUtil.message(
                    "Index volume {} with path '{}' is now seen as shared by node '{}'.",
                    volumeId,
                    volumePath,
                    thisNodeName));
        }
    }

    private void writeMarker(final Path markerDir, final String nodeName, final long nowMs) {
        try {
            Files.createDirectories(markerDir);
            // Rewritten on every check so that other nodes can tell this node still sees the volume.
            Files.writeString(getMarkerFile(markerDir, nodeName), String.valueOf(nowMs));
        } catch (final IOException e) {
            LOGGER.error(() -> LogUtil.message(
                    "Unable to write the shared volume marker file for node '{}' in '{}': {}",
                    nodeName,
                    markerDir,
                    LogUtil.exceptionMessage(e)), e);
        }
    }

    private boolean isMarkerFresh(final Path markerDir, final String nodeName, final long nowMs) {
        final Path markerFile = getMarkerFile(markerDir, nodeName);
        try {
            if (Files.isRegularFile(markerFile)) {
                final long writtenMs = Long.parseLong(Files.readString(markerFile).trim());
                return nowMs - writtenMs < staleAfter.toMillis();
            }
        } catch (final IOException | RuntimeException e) {
            // The owner may be part way through rewriting it, in which case the next check will read it.
            LOGGER.debug(() -> LogUtil.message("Unable to read shared volume marker file '{}': {}",
                    markerFile, LogUtil.exceptionMessage(e)), e);
        }
        return false;
    }

    /// Gets the marker file of a node.
    ///
    /// @param markerDir The marker directory of a shared volume.
    /// @param nodeName  The name of the node.
    /// @return The path of the node's marker file, which is always directly inside the marker directory.
    static Path getMarkerFile(final Path markerDir, final String nodeName) {
        // Encoded so that no node name can name a file outside the marker directory.
        return markerDir.resolve(URLEncoder.encode(nodeName, StandardCharsets.UTF_8) + MARKER_FILE_SUFFIX);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private record MarkerKey(int volumeId, String nodeName) {

    }
}
