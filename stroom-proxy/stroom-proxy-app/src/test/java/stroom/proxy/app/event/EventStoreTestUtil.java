/*
 * Copyright 2016-2025 Crown Copyright
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class EventStoreTestUtil {

    public static String read(final Path dir,
                              final FeedKey feedKey) throws IOException {
        try (final Stream<Path> stream = Files.list(dir)) {
            final List<Path> list = stream.toList();
            assertThat(list.size()).isOne();
            final Path path = list.get(0);
            final String fileName = path.getFileName().toString();
            assertThat(fileName).endsWith(EventStoreFile.LOG_EXTENSION);
            final String prefix = feedKey.encodeKey();
            assertThat(fileName).startsWith(prefix);
            return Files.readString(path);
        }
    }
}
