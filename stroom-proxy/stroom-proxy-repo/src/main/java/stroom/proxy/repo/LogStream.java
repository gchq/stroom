/*
 * Copyright 2024 Crown Copyright
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

package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.util.NullSafe;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class LogStream {

    private final Provider<LogStreamConfig> logStreamConfigProvider;

    @Inject
    public LogStream(final Provider<LogStreamConfig> logStreamConfigProvider) {
        this.logStreamConfigProvider = logStreamConfigProvider;
    }

    public void log(final Logger logger,
                    final AttributeMap attributeMap,
                    final String type,
                    final String url,
                    final int responseCode,
                    final long bytes,
                    final long duration) {
        log(logger, attributeMap, type, url, responseCode, bytes, duration, null);
    }

    public void log(final Logger logger,
                    final AttributeMap attributeMap,
                    final String type,
                    final String url,
                    final int responseCode,
                    final long bytes,
                    final long duration,
                    final String message) {

        if (logger.isInfoEnabled()) {
            final Set<CIKey> metaKeys = NullSafe.set(logStreamConfigProvider.get().getMetaKeys())
                    .stream()
                    .map(CIKey::ofIgnoringCase)
                    .collect(Collectors.toSet());

            if (NullSafe.hasItems(metaKeys)) {
                final AttributeMap filteredMap = attributeMap.filterIncluding(metaKeys);
                final String kvPairs = CSVFormatter.format(filteredMap);
                final String logLine = CSVFormatter.escape(type) +
                        "," +
                        CSVFormatter.escape(url) +
                        "," +
                        responseCode +
                        "," +
                        bytes +
                        "," +
                        duration +
                        "," +
                        CSVFormatter.escape(message) +
                        "," +
                        kvPairs;
                logger.info(logLine);
            }
        }
    }
}
