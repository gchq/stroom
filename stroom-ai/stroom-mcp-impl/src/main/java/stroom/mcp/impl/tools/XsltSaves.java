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

import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolSession;
import stroom.docref.DocRef;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The one save path for the XSLTs the tools write (the Stroom MCP server's `tools/translation.py` `create_xslt`,
/// `update_xslt`, `lost_mapping`, `xslt_role` and `hand_edit_gate`, and `tools/generation.py` `_required_for`), for
/// the tools outside the translation tools that save an XSLT (draft_cef_mapping, the index tools). It is the
/// translation tools' own path ([TranslationDocs], [RequiredImports], [HandEditGate]): the version check, the
/// kept mapping or plan and whether the code is what it generates, the version history and the hand-edit check.
public class XsltSaves {

    /// The kinds of what an XSLT is generated from, as kept with it ([stroom.ai.impl.tools.KeptMapping#KINDS]).
    public static final String TRANSLATION = "translation";
    /// An index's field plan.
    public static final String INDEX = "index";
    /// A CEF plan.
    public static final String CEF = "cef";

    private final TranslationDocs translationDocs;
    private final RequiredImports requiredImports;

    /// @param translationDocs Creates and saves the XSLTs (with the hand-edit check, [HandEditGate]).
    /// @param requiredImports The imports standing instructions require.
    @Inject
    XsltSaves(final TranslationDocs translationDocs,
              final RequiredImports requiredImports) {
        this.translationDocs = translationDocs;
        this.requiredImports = requiredImports;
    }

    /// Creates an XSLT in the build ([TranslationDocs#createXslt]).
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param name    The document's name.
    /// @param code    The complete XSLT.
    /// @param source  The mapping or plan it was generated from (`TranslationDocs.Source.of(...)`), or
    ///                [TranslationDocs.Source#NONE].
    /// @param change  What this change is, for the version history, or null.
    /// @return The document (`type`, `uuid`, `name`, `version`, and `matches_mapping`, `warning` or `ecs_check`),
    /// with the build's next step.
    public Map<String, Object> create(final ToolCallContext context,
                                      final String build,
                                      final String name,
                                      final String code,
                                      final TranslationDocs.Source source,
                                      final String change) {
        return translationDocs.createXslt(context, build, name, code, source, change);
    }

    /// Replaces an XSLT's code ([TranslationDocs#updateXslt]).
    ///
    /// @param call            The call, whose id tells its forms apart.
    /// @param context         The call's context.
    /// @param uuid            The XSLT's UUID.
    /// @param code            The complete new XSLT.
    /// @param version         The version from the last read, or null.
    /// @param source          The mapping or plan the new code was generated from, or [TranslationDocs.Source#NONE].
    /// @param change          What this change is, or null.
    /// @param discardHandEdit True when the user said to drop their hand edit.
    /// @param choices         The user's answers about fields edited by hand, or null.
    /// @return The document with the build's next step, or the question about a hand edit (no `uuid`).
    public Map<String, Object> update(final ToolCall call,
                                      final ToolCallContext context,
                                      final String uuid,
                                      final String code,
                                      final String version,
                                      final TranslationDocs.Source source,
                                      final String change,
                                      final boolean discardHandEdit,
                                      final Map<String, String> choices) {
        return translationDocs.updateXslt(call, context, uuid, code, version, source, change, discardHandEdit,
                choices);
    }

    /// @param ref         An XSLT.
    /// @param description Its description.
    /// @return Why it keeps no mapping when it should, or empty.
    public Optional<String> lostMapping(final DocRef ref, final String description) {
        return translationDocs.lostMapping(ref, description);
    }

    /// @param session The session.
    /// @param stage   The stage, e.g. `cef`.
    /// @param build   The build, or null.
    /// @param feeds   The feeds the work is for, or none.
    /// @return The imports standing instructions require of the stage's XSLTs.
    public List<RequiredImport> requiredFor(final ToolSession session,
                                            final String stage,
                                            final String build,
                                            final List<String> feeds) {
        return requiredImports.requiredFor(session, stage, build, feeds);
    }

    /// @param code An XSLT.
    /// @return The build role it belongs in, or null.
    public static String xsltRole(final String code) {
        return RequiredImports.xsltRole(code);
    }
}
