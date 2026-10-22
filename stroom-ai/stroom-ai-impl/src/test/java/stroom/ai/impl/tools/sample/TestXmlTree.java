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

package stroom.ai.impl.tools.sample;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestXmlTree {

    @Test
    void anElementIsWrittenAsLxmlWritesItWithTheNamespacesItInherits() {
        final XmlTree.Element root = XmlTree.parse("<a:r xmlns:a=\"urn:a\" xmlns=\"urn:d\">"
                                                   + "<e x=\"1 &amp; &quot;2&quot;\">t &lt; <![CDATA[<c>]]></e>"
                                                   + "<empty></empty><!--note--></a:r>");

        final XmlTree.Element first = root.elements().getFirst();

        assertThat(XmlTree.toText(first)).isEqualTo("<e xmlns:a=\"urn:a\" xmlns=\"urn:d\" x=\"1 &amp; &quot;2&quot;\">"
                                                    + "t &lt; &lt;c&gt;</e>");
        assertThat(XmlTree.toText(root.elements().get(1))).isEqualTo("<empty xmlns:a=\"urn:a\" xmlns=\"urn:d\"/>");
        assertThat(first.text()).isEqualTo("t < <c>");
        assertThat(root.isLeaf()).isFalse();
        assertThat(first.namespace).isEqualTo("urn:d");
    }

    @Test
    void aTextThatIsCutOffIsNoSyntaxError() {
        final XmlTree.Pull cut = XmlTree.pull("<r><a>1</a><a>2</a><a>3", Integer.MAX_VALUE);
        final XmlTree.Pull broken = XmlTree.pull("<r><a>1</a><b x=1><a>2</a></r>", Integer.MAX_VALUE);

        assertThat(cut.records()).hasSize(2);
        assertThat(cut.syntaxError()).isFalse();
        assertThat(cut.closed()).isFalse();
        assertThat(broken.records()).hasSize(1);
        assertThat(broken.syntaxError()).isTrue();
        assertThatThrownBy(() -> XmlTree.parse("<a/><b/>")).isInstanceOf(XmlTree.XmlException.class);
        assertThatThrownBy(() -> XmlTree.parse("<r><a>")).isInstanceOf(XmlTree.XmlException.class);
    }
}
