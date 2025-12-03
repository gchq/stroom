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

package stroom.query.language.token;

import stroom.query.api.token.Token;
import stroom.query.api.token.TokenGroup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class TestStructureBuilder extends AbstractQueryTest {

    @Override
    protected Path getTestDir() {
        final Path dir = Paths.get("../stroom-query-language/src/test/resources/TestStructureBuilder");
        if (!Files.isDirectory(dir)) {
            throw new RuntimeException("Test data directory not found: " + dir.toAbsolutePath());
        }
        return dir;
    }

    @Override
    protected String convert(final String input) {
        final List<Token> tokens = Tokeniser.parse(input);
        final TokenGroup tokenGroup = StructureBuilder.create(tokens);
        return tokenGroup.toTokenString(true);
    }
}
