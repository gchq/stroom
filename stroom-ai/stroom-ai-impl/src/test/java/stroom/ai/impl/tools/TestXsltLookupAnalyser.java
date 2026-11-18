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

import stroom.ai.impl.tools.XsltLookupAnalyser.Lookup;
import stroom.ai.impl.tools.XsltLookupAnalyser.MapWrite;
import stroom.ai.impl.tools.XsltLookupAnalyser.Stylesheet;
import stroom.ai.impl.workspace.WorkspaceException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestXsltLookupAnalyser {

    private static final String START = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:stroom="stroom"
                            xmlns="event-logging:3" version="2.0">
            """;
    private static final String END = "</xsl:stylesheet>\n";

    @Test
    void aLiteralMapIsResolved() {
        final List<Lookup> lookups = lookups("""
                <xsl:template match="Event">
                  <User><xsl:value-of select="stroom:lookup('USER_TO_EMAIL', @user)"/></User>
                </xsl:template>
                """);

        assertThat(lookups).hasSize(1);
        final Lookup lookup = lookups.getFirst();
        assertThat(lookup.function()).isEqualTo("lookup");
        assertThat(lookup.map().isResolved()).isTrue();
        assertThat(lookup.map().values()).containsExactly("USER_TO_EMAIL");
        assertThat(lookup.keyExpression()).isEqualTo("@user");
        assertThat(lookup.where()).isEqualTo("template match=\"Event\"");
        assertThat(lookup.line()).isEqualTo(5);
        assertThat(lookup.map().describe()).isEqualTo("'USER_TO_EMAIL' (resolved)");
    }

    @Test
    void aLocalVariableIsFollowed() {
        final Lookup lookup = lookups("""
                <xsl:template match="Event">
                  <xsl:variable name="map" select="'HOST_TO_IP'"/>
                  <xsl:if test="true()">
                    <xsl:value-of select="stroom:lookup($map, Host)"/>
                  </xsl:if>
                </xsl:template>
                """).getFirst();

        assertThat(lookup.map().values()).containsExactly("HOST_TO_IP");
        assertThat(lookup.map().isResolved()).isTrue();
        assertThat(lookup.chain()).singleElement().asString()
                .contains("$map: local xsl:variable at line 5");
    }

    @Test
    void aGlobalVariableIsFollowedAndALocalOneOfTheSameNameHidesIt() {
        final List<Lookup> lookups = lookups("""
                <xsl:variable name="map">GLOBAL_MAP</xsl:variable>
                <xsl:template match="A">
                  <xsl:value-of select="stroom:lookup($map, .)"/>
                </xsl:template>
                <xsl:template match="B">
                  <xsl:variable name="map" select="'LOCAL_MAP'"/>
                  <xsl:value-of select="stroom:lookup($map, .)"/>
                </xsl:template>
                """);

        assertThat(lookups.get(0).map().values()).containsExactly("GLOBAL_MAP");
        assertThat(lookups.get(0).chain().getFirst()).contains("global xsl:variable");
        assertThat(lookups.get(1).map().values()).containsExactly("LOCAL_MAP");
    }

    @Test
    void aParameterGivesItsDefaultAndEachValuePassedToIt() {
        final Lookup lookup = lookups("""
                <xsl:template match="Event">
                  <xsl:call-template name="enrich">
                    <xsl:with-param name="map" select="'MAP_FROM_CALL'"/>
                  </xsl:call-template>
                  <xsl:call-template name="enrich">
                    <xsl:with-param name="map">MAP_FROM_CONTENT</xsl:with-param>
                  </xsl:call-template>
                </xsl:template>
                <xsl:template name="enrich">
                  <xsl:param name="map" select="'DEFAULT_MAP'"/>
                  <xsl:value-of select="stroom:lookup($map, @id)"/>
                </xsl:template>
                """).getFirst();

        assertThat(lookup.map().dynamic()).isFalse();
        assertThat(lookup.map().values()).containsExactly("DEFAULT_MAP", "MAP_FROM_CALL", "MAP_FROM_CONTENT");
        assertThat(lookup.map().describe()).startsWith("one of 'DEFAULT_MAP', 'MAP_FROM_CALL'");
        assertThat(lookup.where()).isEqualTo("template name=\"enrich\"");
    }

    @Test
    void aVariableFromAnIncludedXsltIsFollowed() {
        final Stylesheet common = XsltLookupAnalyser.parse("common", "common-uuid", START + """
                <xsl:variable name="userMap" select="'USER_MAP'"/>
                """ + END);
        final XsltLookupAnalyser analyser = new XsltLookupAnalyser(href -> "common".equals(href)
                ? Optional.of(common)
                : Optional.empty());

        final List<Lookup> lookups = analyser.lookups(XsltLookupAnalyser.parse("main", "main-uuid", START + """
                <xsl:include href="common"/>
                <xsl:include href="missing"/>
                <xsl:template match="Event">
                  <xsl:value-of select="stroom:lookup($userMap, .)"/>
                </xsl:template>
                """ + END));

        assertThat(lookups.getFirst().map().values()).containsExactly("USER_MAP");
        assertThat(lookups.getFirst().chain().getFirst()).contains("of included XSLT 'common'");
        assertThat(analyser.unreadableIncludes()).containsExactly("missing");
        assertThat(analyser.includedStylesheets()).extracting(Stylesheet::name).containsExactly("common");
    }

    @Test
    void anXslChooseGivesSeveralValues() {
        final Lookup lookup = lookups("""
                <xsl:template match="Event">
                  <xsl:variable name="map">
                    <xsl:choose>
                      <xsl:when test="@type = 'a'">MAP_A</xsl:when>
                      <xsl:otherwise><xsl:text>MAP_B</xsl:text></xsl:otherwise>
                    </xsl:choose>
                  </xsl:variable>
                  <xsl:value-of select="stroom:lookup($map, .)"/>
                </xsl:template>
                """).getFirst();

        assertThat(lookup.map().dynamic()).isFalse();
        assertThat(lookup.map().values()).containsExactly("MAP_A", "MAP_B");
        assertThat(lookup.chain()).anyMatch(step -> step.contains("xsl:choose"));
    }

    @Test
    void anIfExpressionGivesBothBranches() {
        final Lookup lookup = lookups("""
                <xsl:template match="Event">
                  <xsl:value-of select="stroom:lookup(if (@x) then 'MAP_X' else 'MAP_Y', .)"/>
                </xsl:template>
                """).getFirst();

        assertThat(lookup.map().values()).containsExactly("MAP_X", "MAP_Y");
    }

    @Test
    void concatWithAValueFromTheInputIsDynamicWithItsConstantParts() {
        final Lookup lookup = lookups("""
                <xsl:template match="Event">
                  <xsl:variable name="prefix" select="'USER_'"/>
                  <xsl:value-of select="stroom:lookup(concat($prefix, @domain, '_MAP'), @user)"/>
                </xsl:template>
                """).getFirst();

        assertThat(lookup.map().dynamic()).isTrue();
        assertThat(lookup.map().pattern()).isEqualTo("USER_…_MAP");
        assertThat(lookup.map().describe()).startsWith("dynamic: USER_…_MAP");
        assertThat(lookup.map().mayBe("USER_CORP_MAP")).isTrue();
        assertThat(lookup.map().mayBe("HOST_MAP")).isFalse();
    }

    @Test
    void concatOfConstantsIsResolved() {
        final Lookup lookup = lookups("""
                <xsl:template match="Event">
                  <xsl:value-of select="stroom:lookup(concat('USER', '_', 'MAP') , .)"/>
                  <xsl:value-of select="stroom:lookup('A' || 'B', .)"/>
                </xsl:template>
                """).getFirst();

        assertThat(lookup.map().values()).containsExactly("USER_MAP");
        assertThat(lookup.map().isResolved()).isTrue();
    }

    @Test
    void aValueFromTheInputIsDynamicAndNeverGuessed() {
        final Lookup lookup = lookups("""
                <xsl:template match="Event">
                  <xsl:value-of select="stroom:lookup(Data[@Name='Map']/@Value, .)"/>
                  <xsl:value-of select="stroom:lookup($undefined, .)"/>
                </xsl:template>
                """).getFirst();

        assertThat(lookup.map().dynamic()).isTrue();
        assertThat(lookup.map().values()).isEmpty();
        assertThat(lookup.map().pattern()).isNull();
        assertThat(lookup.map().describe()).isEqualTo(
                "dynamic: worked out at run time from Data[@Name='Map']/@Value");
        assertThat(lookup.map().mayBe("ANY_MAP")).isTrue();
    }

    @Test
    void anUndefinedVariableIsDynamic() {
        final Lookup lookup = lookups("""
                <xsl:template match="Event">
                  <xsl:value-of select="stroom:lookup($undefined, .)"/>
                </xsl:template>
                """).getFirst();

        assertThat(lookup.map().dynamic()).isTrue();
        assertThat(lookup.chain().getFirst()).contains("not defined");
    }

    @Test
    void aVariableThatRefersToItselfIsDynamic() {
        final Lookup lookup = lookups("""
                <xsl:variable name="a" select="$b"/>
                <xsl:variable name="b" select="$a"/>
                <xsl:template match="Event">
                  <xsl:value-of select="stroom:lookup($a, .)"/>
                </xsl:template>
                """).getFirst();

        assertThat(lookup.map().dynamic()).isTrue();
        assertThat(lookup.chain()).anyMatch(step -> step.contains("refers to itself"));
    }

    @Test
    void bitmapLookupsAndLookupsInAttributeValueTemplatesAndOtherPrefixesAreFound() {
        final XsltLookupAnalyser analyser = new XsltLookupAnalyser(href -> Optional.empty());
        final List<Lookup> lookups = analyser.lookups(XsltLookupAnalyser.parse("x", "x-uuid", """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:s="stroom"
                                xmlns:other="not-stroom" version="2.0">
                  <xsl:template match="Event">
                    <Out flags="{s:bitmap-lookup('FLAGS', @bits)}" note="{{literal}}"/>
                    <xsl:if test="s:lookup('IN_TEST', 'k') = 'x'">x</xsl:if>
                    <xsl:value-of select="other:lookup('NOT_STROOM', .), 'stroom:lookup(''IN_STRING'', .)'"/>
                    <xsl:value-of select="(: s:lookup('IN_COMMENT', .) :) 1"/>
                  </xsl:template>
                </xsl:stylesheet>
                """));

        assertThat(lookups).extracting(Lookup::function).containsExactly("bitmap-lookup", "lookup");
        assertThat(lookups.get(0).map().values()).containsExactly("FLAGS");
        assertThat(lookups.get(1).map().values()).containsExactly("IN_TEST");
    }

    @Test
    void aLoaderWritesLiteralAndComputedMapNames() {
        final XsltLookupAnalyser analyser = new XsltLookupAnalyser(href -> Optional.empty());
        final List<MapWrite> writes = analyser.mapWrites(XsltLookupAnalyser.parse("loader", "loader-uuid", """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                xmlns="reference-data:2" version="2.0">
                  <xsl:variable name="suffix" select="'_TO_EMAIL'"/>
                  <xsl:template match="/">
                    <referenceData>
                      <xsl:apply-templates/>
                    </referenceData>
                  </xsl:template>
                  <xsl:template match="record">
                    <reference>
                      <map>USER_TO_EMAIL</map>
                      <key><xsl:value-of select="user"/></key>
                    </reference>
                    <reference>
                      <map><xsl:value-of select="concat('USER', $suffix)"/></map>
                    </reference>
                    <reference>
                      <map><xsl:value-of select="@mapName"/></map>
                    </reference>
                  </xsl:template>
                </xsl:stylesheet>
                """));

        assertThat(writes).hasSize(3);
        assertThat(writes.get(0).map().values()).containsExactly("USER_TO_EMAIL");
        assertThat(writes.get(1).map().values()).containsExactly("USER_TO_EMAIL");
        assertThat(writes.get(1).chain()).anyMatch(step -> step.contains("$suffix"));
        assertThat(writes.get(2).map().dynamic()).isTrue();
        assertThat(writes.get(0).where()).isEqualTo("template match=\"record\"");
    }

    @Test
    void aDoctypeIsRefused() {
        assertThatThrownBy(() -> XsltLookupAnalyser.parse("evil", "evil-uuid", """
                <!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">&e;</xsl:stylesheet>
                """))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("DOCTYPE");
    }

    @Test
    void textThatIsNotXmlIsRefused() {
        assertThatThrownBy(() -> XsltLookupAnalyser.parse("bad", "bad-uuid", "<xsl:stylesheet"))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("bad");
    }

    @Test
    void splittingAndAttributeValueTemplatesRespectStringsAndBrackets() {
        assertThat(XsltLookupAnalyser.splitTopLevel("'a,b', f(c, d), [e,f]", ','))
                .containsExactly("'a,b'", " f(c, d)", " [e,f]");
        assertThat(XsltLookupAnalyser.avt("x{$a}y{{z}}{'}'}")).containsExactly("$a", "'}'");
        assertThat(Map.of("k", XsltLookupAnalyser.avt("plain"))).containsEntry("k", List.of());
    }

    private static List<Lookup> lookups(final String body) {
        return new XsltLookupAnalyser(href -> Optional.empty())
                .lookups(XsltLookupAnalyser.parse("test", "test-uuid", START + body + END));
    }
}
