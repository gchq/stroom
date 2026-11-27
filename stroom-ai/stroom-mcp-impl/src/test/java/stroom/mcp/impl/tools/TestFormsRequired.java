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

import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// What a call is told when the user can't be asked to agree in a form: never an id to pass back.
class TestFormsRequired {

    private static final ToolSession MCP = new ToolSession(ToolSession.Kind.MCP, "s1",
            new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client"));

    @Test
    void aWriteFromAClientWithoutFormsIsRefusedSayingWhatTheUserNeeds() {
        assertThat(FormsRequired.noForms("create_feed"))
                .startsWith("Not run: create_feed changes something in Stroom")
                .contains("MCP elicitation", "nothing was changed", "The read-only tools still work",
                        "declares the elicitation capability", "Ask Stroom AI", "Don't look for another way")
                .doesNotContain("_id");
    }

    @Test
    void aQuestionAClientWithoutFormsCantShowIsRefusedNamingWhatWasAsked() {
        assertThat(FormsRequired.cannotAsk(MCP, "survey_feed", " Survey feed 'A' only 3 stream(s) a call "))
                .startsWith("Not done: Survey feed 'A' only 3 stream(s) a call. This needs the user's agreement in a "
                            + "form that their MCP client shows (MCP elicitation)")
                .contains("nothing was done", "Calls that need no agreement still work")
                .doesNotContain("_id");
        // No summary: the tool is named instead; no session is taken as a client's.
        assertThat(FormsRequired.cannotAsk(null, "survey_feed", " ")).startsWith("Not done: survey_feed. This "
                                                                                 + "needs the user's agreement in a "
                                                                                 + "form that their MCP client");
    }

    @Test
    void inAChatTheUserIsAskedWhenTheyAreBack() {
        assertThat(FormsRequired.cannotAsk(ToolSession.chat(3), "create_feed", "Create feed A"))
                .startsWith("Not done: Create feed A. This needs the user's agreement in a form, and they are away "
                            + "from the chat")
                .endsWith("call create_feed again when they are back: they are asked then.")
                .doesNotContain("elicitation");
    }
}
