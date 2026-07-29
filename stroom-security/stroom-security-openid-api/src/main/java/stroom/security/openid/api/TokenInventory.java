/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.openid.api;

import java.util.Map;

/**
 * Reads the internal IdP's inventory of live tokens, for the administrative view.
 * <p>
 * The third and last part of the seam, alongside {@link RevokedTokenChecker} (verify) and {@link TokenRevoker}
 * (act). It exists because the admin list <b>cannot</b> be driven from sessions: a subject may hold live tokens
 * while having no session at all - a closed browser, a {@code client_credentials} grant, or any API client that
 * never had one. Those subjects would be invisible on a session-keyed list, and so impossible to find in order
 * to revoke, which is the whole point of the screen.
 * </p>
 * <p>
 * Returns every such subject rather than answering per-subject queries, so the caller can outer-join it against
 * the session list in one pass.
 * </p>
 */
public interface TokenInventory {

    /**
     * Summarise the usable tokens held by every subject that has any.
     * <p>
     * Empty when the internal IdP is not in use - nothing has been minted here, so no subject holds anything.
     * </p>
     *
     * @return subject id to summary. Subjects with no usable tokens are absent rather than mapped to an empty
     * summary, so the map size is the number of subjects holding live token-based access.
     */
    Map<String, TokenSummary> summariseUsableTokensBySubject();
}
