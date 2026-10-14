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

package stroom.openai.shared;

import stroom.docref.HasDisplayValue;

/**
 * How Stroom authenticates to an {@link McpToolServerConfig MCP tool server} when connecting.
 */
public enum McpAuthMode implements HasDisplayValue {

    /**
     * No authentication header is sent.
     */
    NONE("None"),

    /**
     * Sends {@code Authorization: Bearer <token>} where the token is the access token held by the
     * named credential (an Access Token credential).
     */
    BEARER_TOKEN("Bearer token"),

    /**
     * Sends a configurable header (default {@code Authorization}) whose value is an optional prefix
     * followed by the access token held by the named credential (an Access Token credential).
     */
    API_KEY("Custom API key header"),

    /**
     * Sends {@code Authorization: Basic base64(username:password)} from the named credential
     * (a Username / Password credential).
     */
    BASIC("Basic auth"),

    /**
     * Passes the current Stroom user's OAuth2 access token straight through to the MCP server as
     * {@code Authorization: Bearer <token>}. No credential is configured; the token comes from the
     * user's session at the time the question is asked.
     */
    STROOM_SESSION_TOKEN("OAuth2 passthrough");

    private final String displayValue;

    McpAuthMode(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
