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

package stroom.ai.impl.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Forms' bullets in sentence case (the user, 2026-11-24): seen across chats, "- build: …", "- pipeline: …",
/// "- event types: …" in lower case.
class TestFormText {

    @Test
    void aBulletsFirstPlainWordIsCapitalised() {
        assertThat(FormText.sentenceCase("Create pipeline 'X'\n\n- build: azure-audit-v1.0\n- pipeline name: X V1.1\n"
                                         + "- event types: the 7 high-confidence ones\n- rule 'ns_message': 117"))
                .isEqualTo("Create pipeline 'X'\n\n- Build: azure-audit-v1.0\n- Pipeline name: X V1.1\n"
                           + "- Event types: the 7 high-confidence ones\n- Rule 'ns_message': 117");
    }

    @Test
    void numberedLinesAndOtherBulletsAreCapitalisedToo() {
        assertThat(FormText.sentenceCase("1. step every record\n  * keep the edit\n+ other"))
                .isEqualTo("1. Step every record\n  * Keep the edit\n+ Other");
    }

    @Test
    void namesAndCodeAreLeftAsWritten() {
        final String text = "- event.action: empty\n- stream_ids: 1, 2\n- e.g. this\n- `jq` query\n"
                            + "- [link](?action=open-doc)\n- iPhone logs\n- 3 records";
        assertThat(FormText.sentenceCase(text)).isEqualTo(text);
    }

    @Test
    void aFencedCodeBlockAndProseAreLeftAsWritten() {
        final String text = "the summary below\n```\n- build: x\n```\nnot a list: - build";
        assertThat(FormText.sentenceCase(text)).isEqualTo(text);
    }

    @Test
    void inTheChatALabelAndItsValueHaveTheLabelInBold() {
        assertThat(FormText.forChat("Create pipeline\n\n- build: azure-audit-v1.0\n- pipeline name: X V1.1\n"
                                    + "- rule 'ns_message': 117 of 2000"))
                .isEqualTo("Create pipeline\n\n- **Build:** azure-audit-v1.0\n- **Pipeline name:** X V1.1\n"
                           + "- **Rule 'ns_message':** 117 of 2000");
    }

    @Test
    void aLineThatIsntALabelAndItsValueIsntBolded() {
        final String text = "- Keep the edit\n- [Doc](?action=open-doc): read it\n- `jq`: a query\n"
                            + "- **Done:** already\n- note:\n- a time 10:30 here";
        assertThat(FormText.forChat(text)).isEqualTo("- Keep the edit\n- [Doc](?action=open-doc): read it\n"
                                                     + "- `jq`: a query\n- **Done:** already\n- Note:\n"
                                                     + "- A time 10:30 here");
    }

    @Test
    void nothingIsNothing() {
        assertThat(FormText.sentenceCase(null)).isNull();
        assertThat(FormText.sentenceCase("")).isEmpty();
    }
}
