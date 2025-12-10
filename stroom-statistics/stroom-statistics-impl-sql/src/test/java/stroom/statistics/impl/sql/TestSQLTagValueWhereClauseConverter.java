/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.statistics.impl.sql;

import stroom.statistics.impl.sql.search.FilterOperationMode;
import stroom.statistics.impl.sql.search.FilterTermsTree;
import stroom.statistics.impl.sql.search.PrintableNode;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.Arrays;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

class TestSQLTagValueWhereClauseConverter extends StroomUnitTest {

    @BeforeEach
    void before(final TestInfo testInfo) {
        System.out.println("\nTest: " + testInfo.getDisplayName());
        System.out.println("----------------------------------");
    }

    @Test
    void testOneTermNode() {
        final FilterTermsTree.TermNode termNode = new FilterTermsTree.TermNode("Tag1", "Tag1Val1");

        final FilterTermsTree tree = new FilterTermsTree(termNode);

        final SqlBuilder sqlBuilder = convertAndDump(tree);

        checkExpectedBinds(1, sqlBuilder);
    }

    @Test
    void testTwoTermsInAnd() {
        final PrintableNode termNode1 = new FilterTermsTree.TermNode("Tag1", "Tag1Val1");
        final PrintableNode termNode2 = new FilterTermsTree.TermNode("Tag2", "Tag2Val1");
        final FilterTermsTree.OperatorNode opNode = new FilterTermsTree.OperatorNode(
                FilterOperationMode.AND, Arrays.asList(termNode1, termNode2));

        final FilterTermsTree tree = new FilterTermsTree(opNode);

        final SqlBuilder sqlBuilder = convertAndDump(tree);

        checkExpectedBinds(2, sqlBuilder);
        checkExpectedOperatorCount(2, "AND", sqlBuilder);
    }

    @Test
    void testOneTermAndNotTwoTermsInAnd() {
        final PrintableNode termNode1 = new FilterTermsTree.TermNode("Tag1", "Tag1Val1");
        final PrintableNode termNode2 = new FilterTermsTree.TermNode("Tag2", "Tag2Val1");
        final PrintableNode termNode3 = new FilterTermsTree.TermNode("Tag3", "Tag3Val1");

        final PrintableNode andNode = new FilterTermsTree.OperatorNode(
                FilterOperationMode.AND, Arrays.asList(termNode1, termNode2));
        final PrintableNode notNode = new FilterTermsTree.OperatorNode(
                FilterOperationMode.NOT, Arrays.asList(andNode));

        final PrintableNode rootOpNode = new FilterTermsTree.OperatorNode(
                FilterOperationMode.AND, Arrays.asList(termNode3, notNode));

        final FilterTermsTree tree = new FilterTermsTree(rootOpNode);

        final SqlBuilder sqlBuilder = convertAndDump(tree);

        checkExpectedBinds(3, sqlBuilder);
        checkExpectedOperatorCount(3, "AND", sqlBuilder);
        checkExpectedOperatorCount(1, "NOT", sqlBuilder);
    }

    private void checkExpectedBinds(final int expectedCount, final SqlBuilder sqlBuilder) {
        assertThat(sqlBuilder.getArgCount())
                .isEqualTo(expectedCount);
        assertThat(getNumOfOccurrences(sqlBuilder.toString(), "?"))
                .isEqualTo(expectedCount);
    }

    private void checkExpectedOperatorCount(final int expectedCount,
                                            final String operator,
                                            final SqlBuilder sqlBuilder) {
        assertThat(getNumOfOccurrences(sqlBuilder.toString(), operator))
                .isEqualTo(expectedCount);
    }

    private SqlBuilder convertAndDump(final FilterTermsTree tree) {
        final SqlBuilder sqlBuilder = new SqlBuilder();

        SQLTagValueWhereClauseConverter.buildTagValueWhereClause(tree, sqlBuilder);

        String sql = sqlBuilder.toString();

        System.out.println(sql);

        int i = 1;
        for (final Object bindVariable : sqlBuilder.getArgs()) {
            System.out.println("bind " + i + ": " + bindVariable);
            i++;

            final String replacement = Matcher.quoteReplacement("'" + bindVariable + "'");

            sql = sql.replaceFirst("\\?", replacement);
        }

        System.out.println("SQL: " + sql);

        return sqlBuilder;
    }

    private int getNumOfOccurrences(final String source, final String search) {
        int count = 0;
        int prevIndex = 0;
        int curIndex = 0;
        if (source.length() > 0 && search.length() > 0) {
            count = -1;
            while (curIndex >= 0) {
                curIndex = source.indexOf(search, prevIndex);
                // System.out.println("curIndex: " + curIndex);
                prevIndex = curIndex + 1;
                count++;
            }
        }
        return count;
    }
}
