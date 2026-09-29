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

package stroom.floormap.client.value;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the {@link AbstractValueAccessorContractTest} against the XML double, which
 * stands in for the production GWT XML accessor in every JVM test.
 */
class TestDomValueAccessorContract extends AbstractValueAccessorContractTest {

    @Override
    protected ValueAccessor accessor() {
        return DomValueAccessor.INSTANCE;
    }

    @Override
    protected String docWithNumericArray(final String field, final double... values) {
        final StringBuilder sb = new StringBuilder("<entry><").append(field).append(">");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(values[i]);
        }
        return sb.append("</").append(field).append("></entry>").toString();
    }

    @Override
    protected String docWithMalformedNumericArray(final String field) {
        return "<entry><" + field + ">1,nope,3</" + field + "></entry>";
    }

    @Override
    protected String docWithEmptyNumericArray(final String field) {
        return "<entry><" + field + "></" + field + "></entry>";
    }

    @Override
    protected String docWithString(final String field, final String value) {
        return "<entry><" + field + ">" + value + "</" + field + "></entry>";
    }

    @Override
    protected String emptyDoc() {
        return "<entry/>";
    }

    @Override
    protected String path(final String field) {
        return "/entry/" + field;
    }

    @Override
    protected String unparseableRaw() {
        return "<entry><unclosed>";
    }
    // -----------------------------------------------------------------------
    // XML-specific behaviour
    // -----------------------------------------------------------------------

    /**
     * XML element text is untyped, so a numeric-looking value <em>is</em> a string.
     *
     * <p>Asserted explicitly because it is the one place the two formats are
     * legitimately allowed to differ, and the difference looks like a bug when you
     * meet it cold: the same field reads as {@code null} through the JSON accessor
     * and as {@code "5"} through this one. Pinning it here records that the
     * asymmetry is intended, so nobody "fixes" one side into agreement with the
     * other.</p>
     */
    @Test
    void testGetString_numericTextIsStillText() {
        final ValueAccessor a = accessor();
        assertThat(a.getString(a.parse("<entry><n>5</n></entry>"), "/entry/n"))
                .isEqualTo("5");
    }

    /**
     * CDATA is character data, so it reads exactly like plain text.
     */
    @Test
    void testGetString_cdataReadsAsText() {
        final ValueAccessor a = accessor();
        assertThat(a.getString(
                a.parse("<entry><t><![CDATA[gate & co]]></t></entry>"), "/entry/t"))
                .isEqualTo("gate & co");
    }

}
