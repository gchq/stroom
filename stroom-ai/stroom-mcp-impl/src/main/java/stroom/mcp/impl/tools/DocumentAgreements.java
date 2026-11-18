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
import stroom.ai.impl.tools.ToolSession;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/// The user's confirmations of what a tool does to one document, remembered for the session: the same request for the
/// same target (the build, the document and where it goes, as the caller keys it) is asked once, and a changed target
/// asks again.
///
/// Seen in testing (chat 177): write_documentation asked "Document the existing Elastic Index doc ..." four times for
/// the same index doc in one session, each call (a round of descriptions, a call made again after a form) asking
/// afresh.
@Singleton
class DocumentAgreements {

    /// The most agreements remembered: the least recently used are forgotten first.
    static final int MAX_REMEMBERED = 10_000;

    private final Consent consent;
    // The targets agreed to, by session and target.
    private final Map<String, Boolean> agreed = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_REMEMBERED;
                }
            });

    /// @param consent Asks the user.
    @Inject
    DocumentAgreements(final Consent consent) {
        this.consent = consent;
    }

    /// Asks the user to confirm the request ([Consent#require]) unless they confirmed it for the same target earlier
    /// in the session, and remembers it once they have.
    ///
    /// @param call    The call asking, whose id tells its forms apart.
    /// @param context The call's context: how to ask, and the session.
    /// @param target  What the confirmation is of, e.g. the build, the document's UUID and its folder: any change
    ///                asks again.
    /// @param request What the user is asked.
    /// @throws stroom.ai.impl.tools.ToolRefusal if the user declined, the form went unanswered, or the session can't
    ///                                          show a form.
    /// @throws stroom.ai.impl.AiChatRunManager.WaitingForUserException in a chat, while the form waits for the
    ///                                                                 user: the call is made again with the
    ///                                                                 answer.
    void require(final ToolCall call,
                 final ToolCallContext context,
                 final String target,
                 final Consent.Request request) {
        final String key = key(context.session(), call.name(), target);
        if (agreed.containsKey(key)) {
            return;
        }
        consent.require(call, context, request);
        agreed.put(key, Boolean.TRUE);
    }

    /// @param session A session.
    /// @param tool    The tool asking.
    /// @param target  The target, as [#require] keys it.
    /// @return True if the user confirmed it in the session.
    boolean wasAgreed(final ToolSession session, final String tool, final String target) {
        return agreed.containsKey(key(session, tool, target));
    }

    private static String key(final ToolSession session, final String tool, final String target) {
        return session.kind() + ":" + session.id() + "\n" + tool + "\n" + target;
    }
}
