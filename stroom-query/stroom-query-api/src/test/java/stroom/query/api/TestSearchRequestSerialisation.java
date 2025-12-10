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

package stroom.query.api;

import stroom.test.common.ProjectPathUtil;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class TestSearchRequestSerialisation {

    @TestFactory
    Stream<DynamicTest> test() {
        final Path dir = getDir();
        try (final Stream<Path> stream = Files.list(dir)) {
            final List<Path> list = stream.toList();
            return list.stream().map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
                try {
                    final String json = Files.readString(path);
                    JsonUtil.readValue(json, SearchRequest.class);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path getDir() {
        return ProjectPathUtil
                .resolveDir("stroom-query-api")
                .resolve("src")
                .resolve("test")
                .resolve("resources")
                .resolve("TestSearchRequestSerialisation");
    }
}
