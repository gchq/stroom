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

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.validation.XsltSafety;
import stroom.ai.impl.tools.validation.XsltSafety.Lookup;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The user's agreement to an XSLT the tools save or step calling Stroom's DNS lookup functions (`stroom:host-name()`,
/// `stroom:host-address()`: [XsltSafety#STROOM_ASKED]).
///
/// A lookup carries the looked-up value (a record's data) to the DNS server, on Stroom's nodes, whenever the
/// pipeline runs, so a function the XSLT being replaced doesn't already call is asked about in a form
/// ([Consent#require], an approval), naming the functions, where they are called and what they do. Stepping draft
/// code runs it on Stroom too, so a step asks the same way. Where the session can't show a form the tool refuses:
/// the agent never holds the answer.
///
/// What the user agreed to is remembered for the session, by document and function: saving or stepping the same
/// XSLT again doesn't ask again, and an agreement given for a step covers saving it (and the other way round).
@Singleton
class LookupAgreements {

    /// The most documents' agreements remembered: the least recently used are forgotten first.
    static final int MAX_REMEMBERED = 10_000;

    private final Consent consent;
    // The functions agreed to, by session and document.
    private final Map<String, Set<String>> agreed = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Set<String>> eldest) {
                    return size() > MAX_REMEMBERED;
                }
            });

    /// @param consent Asks the user.
    @Inject
    LookupAgreements(final Consent consent) {
        this.consent = consent;
    }

    /// Asks the user to agree to the DNS lookups an XSLT calls that the XSLT it replaces doesn't
    /// ([XsltSafety#newLookups]), unless they agreed to them for the document earlier in the session.
    ///
    /// @param call     The call asking, whose id tells its forms apart.
    /// @param context  The call's context: how to ask, and the session.
    /// @param document What the agreement is for: the XSLT's UUID, or for a new one its build and name.
    /// @param doing    What the tool does with the XSLT, as the user is asked, e.g. `Save XSLT 'ACME-Events'`.
    /// @param refusal  How a refusal starts, e.g. `XSLT not saved`.
    /// @param code     The XSLT's text.
    /// @param previous The text of the XSLT it replaces, or null.
    /// @throws ToolRefusal if the user declined, the form went unanswered, or the session can't show a form.
    /// @throws stroom.ai.impl.AiChatRunManager.WaitingForUserException in a chat, while the form waits for the
    ///                                                                 user: the call is made again with the
    ///                                                                 answer.
    void require(final ToolCall call,
                 final ToolCallContext context,
                 final String document,
                 final String doing,
                 final String refusal,
                 final String code,
                 final String previous) {
        final String key = key(context.session(), document);
        final Set<String> already = agreed.getOrDefault(key, Set.of());
        final List<Lookup> asked = XsltSafety.newLookups(code, previous).stream()
                .filter(lookup -> !already.contains(lookup.function()))
                .toList();
        if (asked.isEmpty()) {
            return;
        }
        final List<String> functions = asked.stream().map(Lookup::function).distinct().toList();
        final String calls = String.join(" and ", functions);
        if (!context.userQuestions().canAsk()) {
            throw new ToolRefusal(noForm(refusal, calls));
        }
        final String summary = doing + ", which calls " + calls + ": it " + XsltSafety.LOOKUP_EFFECT;
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("calls", calls);
        details.put("used in", String.join("; ", asked.stream().map(lookup -> lookup.function() + " in "
                                                                              + lookup.said()).toList()));
        try {
            consent.require(call, context, Consent.Request.approval(call.name(), summary, details));
        } catch (final ToolRefusal e) {
            if (Consent.notAgreed(summary, call.name(), false).equals(e.getMessage())) {
                throw new ToolRefusal(refusal + ": the user declined " + calls + " (it "
                                      + XsltSafety.LOOKUP_EFFECT + "). Leave it out unless the user asks for it: "
                                      + "write the record's own values instead.");
            }
            throw new ToolRefusal(refusal + ": " + e.getMessage());
        }
        agreed.compute(key, (ignored, kept) -> {
            final Set<String> now = kept == null
                    ? new HashSet<>()
                    : new HashSet<>(kept);
            now.addAll(functions);
            return Set.copyOf(now);
        });
    }

    private static String noForm(final String refusal, final String calls) {
        return refusal + ": it calls " + calls + ", which " + XsltSafety.LOOKUP_EFFECT + ". The user must agree to "
               + "that in a form, and this session can't show one now: call again when it can, or leave the lookup "
               + "out (write the record's own values).";
    }

    private static String key(final ToolSession session, final String document) {
        return session.kind() + ":" + session.id() + "\n" + document;
    }
}
