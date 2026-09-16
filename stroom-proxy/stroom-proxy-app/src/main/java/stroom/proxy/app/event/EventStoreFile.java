/*
 * Copyright 2022 Crown Copyright
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

package stroom.proxy.app.event;

import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.FeedKey;

import java.nio.file.Path;
import java.time.Instant;

public class EventStoreFile {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventStoreFile.class);

    public static final String TIME_DELIMITER = "=";
    public static final String LOG_EXTENSION = ".log";

    public static Path createNew(final Path dir,
                                 final FeedKey feedKey,
                                 final Instant instant) {

        final Path path = dir.resolve(FeedKeyEncoder.encodeKey(feedKey) +
                                      TIME_DELIMITER +
                                      DateUtil.createFileDateTimeString(instant.toEpochMilli()) +
                                      LOG_EXTENSION);
        LOGGER.debug("createNew() - dir: {}, feedKey: {}, instant: {}, path: {}", dir, feedKey, instant, path);
        return path;
    }
}
