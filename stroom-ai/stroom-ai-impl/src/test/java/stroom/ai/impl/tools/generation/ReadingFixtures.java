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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.sample.ToolJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// The fixtures of the reader's tests: the generator's golden files (generated XSLTs, with their mappings), and
/// parsed expressions written as the fixtures write them.
final class ReadingFixtures {

    private static final String GOLDEN = "/stroom/ai/impl/golden/generation";

    private ReadingFixtures() {
        // Static only.
    }

    /// @param suffix A file name's ending, e.g. `.xslt`.
    /// @return The golden files with it, in name order.
    static List<Path> golden(final String suffix) {
        try {
            final URL url = ReadingFixtures.class.getResource(GOLDEN);
            if (url == null) {
                throw new IllegalStateException("No " + GOLDEN);
            }
            try (final Stream<Path> listed = Files.list(Path.of(url.toURI()))) {
                return listed.filter(path -> path.toString().endsWith(suffix)).sorted().toList();
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /// @param path A file.
    /// @return Its text.
    static String read(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// @param name A resource next to the generation tests.
    /// @param <T>  What it holds.
    /// @return Its JSON, read.
    @SuppressWarnings("unchecked")
    static <T> T json(final String name) {
        return (T) ToolJson.loads(GenerationFixtures.resource(name));
    }

    /// @param node A parsed expression.
    /// @return It as the fixtures write a tree: `[symbol, value, start, end, children...]`, numbers as
    /// `{"int": "1"}`, `{"decimal": "1.5"}` or `{"float": "1000.0"}`.
    static List<Object> tree(final XPathNode node) {
        final List<Object> out = new ArrayList<>();
        out.add(node.symbol());
        out.add(valueOf(node.value()));
        out.add((long) node.start());
        out.add((long) node.end());
        node.children().forEach(child -> out.add(tree(child)));
        return out;
    }

    private static Object valueOf(final Object value) {
        return switch (value) {
            case final BigInteger number -> Map.of("int", number.toString());
            case final BigDecimal number -> Map.of("decimal", number.toPlainString());
            case final Double number -> Map.of("float", ToolJson.dumps(number));
            case null, default -> value;
        };
    }

    /// @param value A JSON value.
    /// @return The first 16 hex digits of the SHA-256 of its `json.dumps`.
    static String digest(final Object value) {
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(ToolJson.dumps(value).getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
