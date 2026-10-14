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

package stroom.query.language;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.token.TokenException;
import stroom.query.language.functions.ExpressionContext;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@code from} clause resolving a {@code param('key')} reference.
 *
 * <p>A {@code from} clause used to accept only a string literal, so a caller wanting to choose its
 * data source at run time had to substitute the value into the query <em>text</em> before sending
 * it. The Floor Map did exactly that, in two places, with a plain {@code String.replace} — which
 * also rewrote matches inside quoted literals and comments, needed the value escaped for the
 * literal it landed in, and shifted every error offset the query editor highlights. Resolving the
 * reference here instead lets the value travel as an ordinary {@link Param} and leaves the text
 * alone.</p>
 *
 * <p>{@link MockDataSourceResolver} resolves any name to a {@code DocRef} carrying that name, so
 * asserting on the resolved data source's name is asserting on the name the clause produced.</p>
 */
class TestSearchRequestFactoryDataSourceParam {

    private static final String EVENT_STORE = "EventStore";

    @Test
    void resolvesAParamToItsValue() {
        final SearchRequest request = create(
                "from param('EventStore')\nselect Key",
                new Param(EVENT_STORE, "floor_map_events"));

        assertThat(request.getQuery().getDataSource().getName())
                .isEqualTo("floor_map_events");
    }

    /**
     * The value is used as a name, not injected into query text, so nothing about it needs
     * escaping. Under the substitution this replaced, a value containing a double quote would
     * terminate the literal it was placed in early and produce malformed query text.
     */
    @Test
    void valueNeedingQuotingIsUsedVerbatim() {
        final String awkward = "a store \"with\" quotes and spaces";
        final SearchRequest request = create(
                "from param('EventStore')\nselect Key",
                new Param(EVENT_STORE, awkward));

        assertThat(request.getQuery().getDataSource().getName()).isEqualTo(awkward);
    }

    @Test
    void anUnboundParamIsRejectedByName() {
        assertThatThrownBy(() -> create("from param('EventStore')\nselect Key"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("No value supplied for the parameter");
    }

    @Test
    void paramBoundUnderADifferentKeyIsStillUnbound() {
        assertThatThrownBy(() -> create(
                "from param('EventStore')\nselect Key",
                new Param("FactStore", "floor_map_facts")))
                .isInstanceOf(TokenException.class)
                .isInstanceOf(TokenException.class);
    }

    @Test
    void literalDataSourceIsUnaffected() {
        final SearchRequest request = create("from \"floor_map_events\"\nselect Key");

        assertThat(request.getQuery().getDataSource().getName()).isEqualTo("floor_map_events");
    }

    @Test
    void anUnquotedLiteralDataSourceIsUnaffected() {
        final SearchRequest request = create("from index_view\nselect Key");

        assertThat(request.getQuery().getDataSource().getName()).isEqualTo("index_view");
    }

    /**
     * Pins a pre-existing behaviour that this change deliberately did <b>not</b> alter.
     *
     * <p>{@code ${key}} is tokenised as {@link stroom.query.api.token.TokenType#PARAM}, which
     * {@code TokenType.isString} accepts — so the clause takes it without complaint. But
     * {@code ParamToken} sets its unescaped text to the key rather than the value, and nothing
     * substitutes {@code ${…}} into the query text on the search path. So the data source resolves
     * to the <em>key name</em>, silently.</p>
     *
     * <p>This test exists so the behaviour is recorded rather than rediscovered. If it starts
     * failing, {@code ${…}} has been made to resolve properly and this test should be rewritten to
     * assert the value — not deleted. See
     * {@code docs/task-query-dollar-param-in-from-clause-resolves-key.md}.</p>
     */
    @Test
    void dollarBraceParamStillResolvesToTheKeyNameNotItsValue() {
        final SearchRequest request = create(
                "from ${EventStore}\nselect Key",
                new Param(EVENT_STORE, "floor_map_events"));

        assertThat(request.getQuery().getDataSource().getName())
                .describedAs("known gap: ${…} in a from clause is not substituted")
                .isEqualTo(EVENT_STORE);
    }

    private SearchRequest create(final String query, final Param... params) {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(0L).build();
        final SearchRequest in = new SearchRequest(
                null,
                new QueryKey("test"),
                Query.builder().params(params.length == 0 ? null : List.of(params)).build(),
                new ArrayList<ResultRequest>(0),
                dateTimeSettings,
                false);
        final ExpressionContext expressionContext = ExpressionContext
                .builder()
                .dateTimeSettings(dateTimeSettings)
                .maxStringLength(100)
                .build();
        return new SearchRequestFactory(
                (keywordGroup, parentTableSettings) -> null,
                MockDataSourceResolver.getInstance(),
                () -> criteria -> null,
                MockSecurityContext.getInstance())
                .create(query, in, expressionContext);
    }
}
