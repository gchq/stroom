/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.sql;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import stroom.statistics.sql.search.FilterOperationMode;
import stroom.statistics.sql.search.FilterTermsTree;
import stroom.statistics.sql.search.FilterTermsTree.OperatorNode;
import stroom.statistics.sql.search.FilterTermsTree.TermNode;
import stroom.statistics.sql.search.PrintableNode;
import stroom.util.test.StroomUnitTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

public class TestSQLTagValueWhereClauseConverter extends StroomUnitTest {
    private static class Result {
        private final String whereClause;
        private final List<String> bindVariables;

        public Result(final String whereClause, final List<String> bindVariables) {
            this.whereClause = whereClause;
            this.bindVariables = bindVariables;
        }

        public String getWhereClause() {
            return whereClause;
        }

        public List<String> getBindVariables() {
            return bindVariables;
        }
    }

    @Rule
    public TestName testName = new TestName();

    @Before
    public void before() {
        System.out.println("\nTest: " + testName.getMethodName());
        System.out.println("----------------------------------");
    }

    @Test
    public void testOneTermNode() {
        final TermNode termNode = new TermNode("Tag1", "Tag1Val1");

        final FilterTermsTree tree = new FilterTermsTree(termNode);

        final Result result = convertAndDump(tree);

        checkExpectedBinds(1, result);
    }

    @Test
    public void testTwoTermsInAnd() {
        final PrintableNode termNode1 = new TermNode("Tag1", "Tag1Val1");
        final PrintableNode termNode2 = new TermNode("Tag2", "Tag2Val1");
        final OperatorNode opNode = new OperatorNode(FilterOperationMode.AND, Arrays.asList(termNode1, termNode2));

        final FilterTermsTree tree = new FilterTermsTree(opNode);

        final Result result = convertAndDump(tree);

        checkExpectedBinds(2, result);
        checkExpectedOperatorCount(1, "AND", result);
    }

    @Test
    public void testOneTermAndNotTwoTermsInAnd() {
        final PrintableNode termNode1 = new TermNode("Tag1", "Tag1Val1");
        final PrintableNode termNode2 = new TermNode("Tag2", "Tag2Val1");
        final PrintableNode termNode3 = new TermNode("Tag3", "Tag3Val1");

        final PrintableNode andNode = new OperatorNode(FilterOperationMode.AND, Arrays.asList(termNode1, termNode2));
        final PrintableNode notNode = new OperatorNode(FilterOperationMode.NOT, Arrays.asList(andNode));

        final PrintableNode rootOpNode = new OperatorNode(FilterOperationMode.AND, Arrays.asList(termNode3, notNode));

        final FilterTermsTree tree = new FilterTermsTree(rootOpNode);

        final Result result = convertAndDump(tree);

        checkExpectedBinds(3, result);
        checkExpectedOperatorCount(2, "AND", result);
        checkExpectedOperatorCount(1, "NOT", result);
    }

    private void checkExpectedBinds(final int expectedCount, final Result result) {
        Assert.assertEquals(expectedCount, result.getBindVariables().size());
        Assert.assertEquals(expectedCount, getNumOfOccurrences(result.getWhereClause(), "?"));
    }

    private void checkExpectedOperatorCount(final int expectedCount, final String operator, final Result result) {
        Assert.assertEquals(expectedCount, getNumOfOccurrences(result.getWhereClause(), operator));
    }

    private Result convertAndDump(final FilterTermsTree tree) {
        final List<String> bindVariables = new ArrayList<>();

        final String whereClause = SQLTagValueWhereClauseConverter.buildTagValueWhereClause(tree, bindVariables);
        String sql = whereClause;

        System.out.println(whereClause);

        int i = 1;
        for (final String bindVariable : bindVariables) {
            System.out.println("bind " + i + ": " + bindVariable);
            i++;

            final String replacement = Matcher.quoteReplacement("'" + bindVariable + "'");

            sql = sql.replaceFirst("\\?", replacement);
        }

        System.out.println("SQL: " + sql);

        return new Result(whereClause, bindVariables);

    }

    private int getNumOfOccurrences(final String source, final String search) {
        int count = 0;
        int prevIndex = 0, curIndex = 0;
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
