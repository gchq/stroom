/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl;

public final class PlanBConstants {

    public static final String WRITER_DIR_NAME = "writer";
    public static final String RECEIVE_DIR_NAME = "receive";
    public static final String STAGING_DIR_NAME = "staging";
    public static final String UNZIP_DIR_NAME = "unzip";
    public static final String MERGING_DIR_NAME = "merging";
    public static final String SHARDS_DIR_NAME = "shards";
    public static final String SNAPSHOTS_DIR_NAME = "snapshots";
    public static final String PROCESSING_DIR_NAME = "processing";
    public static final String TRASH_DIR_NAME = "trash";

    public static final String TMP_DIR_SUFFIX = ".tmp";
    public static final String TMP_DIR_PREFIX = ".tmp_";
    public static final String OLD_DIR_PREFIX = ".old_";

    public static final String SNAPSHOT_TMP_DIR_NAME = "snapshot.tmp";
    public static final String SNAPSHOT_ZIP_FILE_NAME = "snapshot.zip";
    public static final String SNAPSHOT_INFO_FILE_NAME = "snapshot.txt";

    public static final String DATA_FILE_NAME = "data.mdb";
    public static final String LOCK_FILE_NAME = "lock.mdb";
    public static final String COMPLETE_FILE_NAME = ".complete";
    public static final String MERGED_FILE_NAME = ".merged";
    public static final String VERSION_FILE_NAME = ".version";
    public static final String RETENTION_LAST_FILE_NAME = ".retention.last";
    public static final String ARCHIVE_DIR_NAME          = "archive";
    public static final String ARCHIVAL_LAST_FILE_NAME   = ".archival.last";

    private PlanBConstants() {
        // Utility class
    }

    public static String getMergeLockName(final String docUuid, final int shardIndex) {
        return "planb-merge-" + docUuid + "-" + shardIndex;
    }

    public static String getMergeLockPrefix(final String docUuid) {
        return "planb-merge-" + docUuid + "-";
    }

    /**
     * Formats a shard index as a zero-padded four-digit string.
     * Example: {@code formatShardIndex(3)} returns {@code "0003"}.
     */
    public static String formatShardIndex(final int shardIndex) {
        return String.format("%04d", shardIndex);
    }
}
