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

package stroom.planb.impl.db;

import stroom.planb.impl.db.PlanBEnv.Usage;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import java.nio.file.Path;

/**
 * Thrown in place of {@link org.lmdbjava.Env.MapFullException} so the failure names the store that
 * ran out of space and how much it was allowed, rather than surfacing a raw LMDB result code.
 *
 * <p>An LMDB map is fixed at env open time from the doc's {@code maxStoreSize}, and pages freed by
 * deletes are never returned to the OS, so a full store stays full until data is deleted and the
 * file is compacted, or {@code maxStoreSize} is raised.</p>
 */
public class PlanBStoreFullException extends RuntimeException {

    private final transient Usage usage;

    public PlanBStoreFullException(final Path path, final Usage usage, final Throwable cause) {
        super(buildMessage(path, usage), cause);
        this.usage = usage;
    }

    private static String buildMessage(final Path path, final Usage usage) {
        return LogUtil.message(
                "Plan B store {} is full, using {} of a max store size of {} ({}%). Raise the max " +
                "store size for this doc, or reduce the data it holds via retention or archival.",
                path,
                ModelStringUtil.formatIECByteSizeString(usage.usedBytes()),
                ModelStringUtil.formatIECByteSizeString(usage.mapSize()),
                Math.round(usage.fraction() * 100));
    }

    public Usage getUsage() {
        return usage;
    }
}
