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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.validation.XmlTrees.NotWellFormedException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Where XML that is not well-formed is at fault, said so it can be found.
class TestWellFormedFaults {

    @Test
    void theEventTheColumnAndTheTextAroundAreGiven() {
        final String xml = "<Events xmlns=\"event-logging:3\"><Event><D>a</D></Event><Event><D>b&#xc;c</D></Event>"
                           + "</Events>";

        assertThatThrownBy(() -> XsltChecks.parse(xml, "Events XML"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("Events XML is not well-formed XML: Character reference \"&#xc\" is an "
                                        + "invalid XML character. (line 1, column 72, in Event 2; near \"...")
                .hasMessageContaining("<Event><D>a</D></Event><Event><D>b&#xc;c</D></Event></Events>\")")
                .hasMessageEndingWith(". &#xc; (U+000C, a control character) is a character reference XML 1.0 "
                                      + "forbids and XML 1.1 allows. Stroom writes Events streams as XML 1.1 "
                                      + "(their header declares <?xml version=\"1.1\"?>), and its schema filter "
                                      + "passes them: give the document that declaration to check it as Stroom "
                                      + "reads it. The character is in the source's value; transform strip_control "
                                      + "in the mapping takes such characters out, needed only if the output must "
                                      + "be XML 1.0");
    }

    @Test
    void aRawControlCharacterIsShownEscapedWithNoHint() {
        final String xml = "<Events>\n<Event>\n<D>b\u000cc</D></Event></Events>";

        assertThatThrownBy(() -> XsltChecks.parse(xml, "Events XML"))
                .hasMessage("Events XML is not well-formed XML: An invalid XML character (Unicode: 0xc) was found "
                            + "in the element content of the document. (line 3, column 5, in Event 1; near "
                            + "\"<Events>\\u000a<Event>\\u000a<D>b\\u000cc</D></Event></Events>\")");
    }

    @Test
    void anXsltsLiteralEventsAreNotCounted() {
        final String xslt = "<?xml version=\"1.1\"?>\n<!-- a comment -->\n<stylesheet><template>"
                            + "<Event><D>b&#xc;</D></Event></template></stylesheet>";

        assertThatThrownBy(() -> XsltChecks.parse(xslt.replace("1.1", "1.0"), "XSLT"))
                .hasMessageStartingWith("XSLT is not well-formed XML: Character reference \"&#xc\" is an invalid "
                                        + "XML character. (line 3, column ")
                .hasMessageNotContaining("in Event");
        assertThat(WellFormedFaults.isEvents("<?xml version=\"1.1\"?>\n<!-- c -->\n<evt:Events xmlns:evt=\"x\">"))
                .isTrue();
        assertThat(WellFormedFaults.isEvents("<Event/>")).isTrue();
        assertThat(WellFormedFaults.isEvents("<EventsList/>")).isFalse();
        assertThat(WellFormedFaults.isEvents(null)).isFalse();
    }

    @Test
    void anOffsetIsFoundByLineAndColumnWhateverTheNewLines() {
        assertThat(WellFormedFaults.offset("ab\ncd\r\nef\rgh", 3, 2)).isEqualTo(8);
        assertThat(WellFormedFaults.offset("ab\ncd\r\nef\rgh", 4, 1)).isEqualTo(10);
        // Past the end, the end.
        assertThat(WellFormedFaults.offset("ab", 9, 9)).isEqualTo(2);
    }

    @Test
    void eventsAreCountedWithOrWithoutAPrefixButNotOtherNames() {
        final String xml = "<evt:Events><evt:Event/><EventTime/><Event a=\"1\"><EventSource/>";

        assertThat(WellFormedFaults.eventNumber(xml, xml.length())).isEqualTo(2);
        assertThat(WellFormedFaults.eventNumber(xml, 5)).isZero();
    }

    @Test
    void aLongTextIsCutEitherSide() {
        final String text = "x".repeat(100) + "!" + "y".repeat(100);

        assertThat(WellFormedFaults.excerpt(text, 101))
                .isEqualTo("..." + "x".repeat(50 - 1) + "!" + "y".repeat(30) + "...");
    }

    @Test
    void noHintForAReferenceNoXmlAllowsOrOneXml10Allows() {
        assertThat(WellFormedFaults.hint("Character reference \"&#0\" is an invalid XML character.")).isEmpty();
        assertThat(WellFormedFaults.hint("Character reference \"&#x85\" is an invalid XML character.")).isEmpty();
        assertThat(WellFormedFaults.hint("Character reference \"&#27\" is an invalid XML character."))
                .contains("&#27; (U+001B, a control character)");
        assertThat(WellFormedFaults.hint(null)).isEmpty();
    }

    @Test
    void noLineSaysNothingAndNoColumnOnlyTheLine() {
        assertThat(WellFormedFaults.where("<a>", new NotWellFormedException(-1, "x"), true)).isEmpty();
        assertThat(WellFormedFaults.where("<a>", new NotWellFormedException(2, "x"), true)).isEqualTo(" (line 2)");
    }
}
