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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.SampleTexts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/// A sample sent with context data: each part's raw data and its context, packed as Stroom's receipt takes data with
/// child streams, a zip of `NNN.dat` (the part's data) and `NNN.ctx` (its Context child) pairs, one part a pair. The
/// stream Stroom stores holds the parts in order, each with its own Context child, which a pipeline reference of
/// stream type `Context` reads for that part's lookups.
final class ContextSamples {

    /// The name the zip is sent under: Stroom's upload reads a `.zip` as entries.
    static final String FILE_NAME = "sample-with-context.zip";
    /// The most parts one upload holds.
    static final int MAX_PARTS = 100;

    private ContextSamples() {
        // Static only.
    }

    /// The parts a call gives: `parts` (each `{sample, context}`), or one part from `sample` and `context`.
    ///
    /// @param sample  The call's sample, or null.
    /// @param context The call's context, or null.
    /// @param parts   The call's parts, or null.
    /// @return The parts, or empty when the call gives no context data at all (a plain upload).
    /// @throws ToolRefusal if the parts are given wrongly.
    static List<Part> parts(final String sample, final String context, final Object parts) {
        if (parts == null) {
            if (context == null) {
                return List.of();
            }
            return List.of(checked(new Part(sample, context), 0));
        }
        if (sample != null || context != null) {
            throw new ToolRefusal("Give parts (each {sample, context}) or sample with context, not both");
        }
        if (!(parts instanceof final List<?> list) || list.isEmpty()) {
            throw new ToolRefusal("parts is a list of {sample, context}: each part's raw data and its context data");
        }
        if (list.size() > MAX_PARTS) {
            throw new ToolRefusal("At most " + MAX_PARTS + " parts in one upload");
        }
        final List<Part> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof final Map<?, ?> map)) {
                throw new ToolRefusal("parts[" + i + "] is {sample, context}");
            }
            out.add(checked(new Part(text(map.get("sample")), text(map.get("context"))), i));
        }
        return List.copyOf(out);
    }

    /// @param parts The parts.
    /// @return The zip Stroom's receipt reads them from: `001.dat` and `001.ctx`, then `002...`, in order.
    static byte[] zip(final List<Part> parts) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (final ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (int i = 0; i < parts.size(); i++) {
                final String base = String.format("%03d", i + 1);
                entry(zip, base + ".dat", parts.get(i).sample());
                entry(zip, base + ".ctx", parts.get(i).context());
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static void entry(final ZipOutputStream zip, final String name, final String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static Part checked(final Part part, final int index) {
        final String where = index == 0
                ? ""
                : " (part " + index + ")";
        if (part.sample() == null || part.sample().isEmpty()) {
            throw new ToolRefusal("Give the part's raw data as sample" + where + ": the context goes with it");
        }
        if (part.context() == null || part.context().isBlank()) {
            throw new ToolRefusal("Give the part's context data as context" + where + ", exactly as the source "
                                  + "sends it");
        }
        SampleTexts.checkSample(part.sample(), "sample");
        SampleTexts.checkSample(part.context(), "context");
        return part;
    }

    private static String text(final Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }


    // --------------------------------------------------------------------------------


    /// One part of a sample: its raw data and its context data.
    ///
    /// @param sample  The raw data, as the source writes it.
    /// @param context The context data sent with it.
    record Part(String sample, String context) {

    }
}
