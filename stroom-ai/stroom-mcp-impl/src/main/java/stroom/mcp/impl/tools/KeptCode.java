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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.cef.CefPlan;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;

/// What the mapping or plan kept with an XSLT generates now (the Stroom MCP server's `tools/translation.py`
/// `_kept_code`): the code the tools saved, but for their own changes since.
class KeptCode {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KeptCode.class);

    private final EventVersions eventVersions;

    /// @param eventVersions The event-logging schemas a translation is generated against.
    @Inject
    KeptCode(final EventVersions eventVersions) {
        this.eventVersions = eventVersions;
    }

    /// @param kind    `translation`, `index` or `cef`.
    /// @param payload What is kept for it: `{schema_version, mapping}` for a translation, the plan for the others.
    /// @return The code it generates now; empty when it no longer generates (the whole XSLT then counts as the
    /// edit's).
    Optional<String> keptCode(final String kind, final Map<String, Object> payload) {
        try {
            return switch (kind) {
                case KeptMapping.TRANSLATION -> Optional.ofNullable(regenerate(payload, null).xslt());
                case "index" -> Optional.of(FieldPlan.fromJson(payload).xslt());
                case "cef" -> Optional.of(CefPlan.fromJson(payload).xslt());
                default -> Optional.empty();
            };
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The kept {} no longer generates: {}", kind, e.getMessage()));
            return Optional.empty();
        }
    }

    /// @param kept A translation mapping kept with its XSLT.
    /// @return It as the generator writes it now.
    Optional<String> keptCode(final KeptMapping.Kept kept) {
        return keptCode(kept.kind(), kept.payload());
    }

    /// The translation a kept mapping generates now, for the event-logging version kept with it (else the one the
    /// code writes, else the newest held).
    ///
    /// @param payload The kept `{schema_version, mapping}`.
    /// @param code    The XSLT's code, or null.
    /// @return What the generator gives.
    /// @throws RuntimeException if the mapping no longer reads, or the schema can't be read.
    XsltGenerator.Generated regenerate(final Map<String, Object> payload, final String code) {
        final String version = eventVersions.translationVersion(keptVersion(payload), code);
        return XsltGenerator.generate(TranslationMapping.parse(payload.get("mapping")),
                eventVersions.eventSchema(version), version);
    }

    /// @param payload What is kept with a translation XSLT.
    /// @return The event-logging version it was saved for, or null.
    static String keptVersion(final Map<String, Object> payload) {
        final Object version = payload == null
                ? null
                : payload.get("schema_version");
        return version == null || "".equals(version)
                ? null
                : String.valueOf(version);
    }
}
