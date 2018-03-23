/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.search;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.DashboardService;
import stroom.dashboard.QueryHistoryCleanExecutor;
import stroom.dashboard.QueryService;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.FindQueryCriteria;
import stroom.dashboard.shared.QueryEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.Sort.Direction;
import stroom.entity.util.BaseEntityDeProxyProcessor;
import stroom.index.IndexService;
import stroom.index.shared.Index;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Query;
import stroom.security.UserService;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.util.Collections;

public class TestQueryServiceImpl extends AbstractCoreIntegrationTest {
    private static final String QUERY_COMPONENT = "Test Component";
    private static Logger LOGGER = LoggerFactory.getLogger(TestQueryServiceImpl.class);
    @Inject
    private DashboardService dashboardService;
    @Inject
    private QueryService queryService;
    @Inject
    private IndexService indexService;
    @Inject
    private UserService userService;
    @Inject
    private QueryHistoryCleanExecutor queryHistoryCleanExecutor;

    private Dashboard dashboard;
    private QueryEntity testQuery;
    private QueryEntity refQuery;

    @Override
    protected void onBefore() {
        // need an explicit teardown and setup of the DB before each test method
        clean();

        dashboard = dashboardService.create("Test");

        final Index index = indexService.create("Test index");
        final DocRef dataSourceRef = DocRefUtil.create(index);

        refQuery = queryService.create("Ref query");
        refQuery.setDashboardId(dashboard.getId());
        refQuery.setQueryId(QUERY_COMPONENT);
        refQuery.setQuery(new Query(dataSourceRef, new ExpressionOperator(null, Op.AND, Collections.emptyList())));
        queryService.save(refQuery);

        final ExpressionOperator.Builder root = new ExpressionOperator.Builder(Op.OR);
        root.addTerm("Some field", Condition.CONTAINS, "Some value");

        LOGGER.info(root.toString());

        testQuery = queryService.create("Test query");
        testQuery.setDashboardId(dashboard.getId());
        testQuery.setQueryId(QUERY_COMPONENT);
        testQuery.setQuery(new Query(dataSourceRef, root.build()));
        testQuery = queryService.save(testQuery);

        LOGGER.info(testQuery.getQuery().toString());
    }

    @Test
    public void testQueryRetrieval() {
        final FindQueryCriteria criteria = new FindQueryCriteria();
        criteria.setDashboardId(dashboard.getId());
        criteria.setQueryId(QUERY_COMPONENT);
        criteria.setSort(FindQueryCriteria.FIELD_TIME, Direction.DESCENDING, false);

        final BaseResultList<QueryEntity> list = queryService.find(criteria);

        Assert.assertEquals(2, list.size());

        final QueryEntity query = list.get(0);

        Assert.assertNotNull(query);
        Assert.assertEquals("Test query", query.getName());
        Assert.assertNotNull(query.getData());

        final ExpressionOperator root = query.getQuery().getExpression();

        Assert.assertEquals(1, root.getChildren().size());

        final StringBuilder sb = new StringBuilder();
        sb.append("<expression>\n");
        sb.append("    <op>OR</op>\n");
        sb.append("    <children>\n");
        sb.append("        <term>\n");
        sb.append("            <field>Some field</field>\n");
        sb.append("            <condition>CONTAINS</condition>\n");
        sb.append("            <value>Some value</value>\n");
        sb.append("        </term>\n");
        sb.append("    </children>\n");
        sb.append("</expression>\n");

        String actual = query.getData();
        actual = actual.replaceAll("\\s*", "");
        String expected = sb.toString();
        expected = expected.replaceAll("\\s*", "");
        Assert.assertTrue(actual.contains(expected));
    }

    @Test
    public void testOldHistoryDeletion() {
        final FindQueryCriteria criteria = new FindQueryCriteria();
        criteria.setDashboardId(dashboard.getId());
        criteria.setQueryId(QUERY_COMPONENT);
        criteria.setSort(FindQueryCriteria.FIELD_TIME, Direction.DESCENDING, false);

        BaseResultList<QueryEntity> list = queryService.find(criteria);
        Assert.assertEquals(2, list.size());

        QueryEntity query = list.get(0);

        // Now insert the same query over 100 times.
        for (int i = 0; i < 120; i++) {
            final QueryEntity newQuery = queryService.create("History");
            newQuery.setDashboardId(query.getDashboardId());
            newQuery.setQueryId(query.getQueryId());
            newQuery.setFavourite(false);
            newQuery.setQuery(query.getQuery());
            newQuery.setData(query.getData());
            queryService.save(newQuery);
        }

        // Clean the history.
        queryHistoryCleanExecutor.clean(null, false);

        list = queryService.find(criteria);
        Assert.assertEquals(100, list.size());
    }

    @Test
    public void testLoad() {
        QueryEntity query = new QueryEntity();
        query.setId(testQuery.getId());
        query = queryService.load(query);

        Assert.assertNotNull(query);
        Assert.assertEquals("Test query", query.getName());
        Assert.assertNotNull(query.getData());
        final ExpressionOperator root = query.getQuery().getExpression();
        Assert.assertEquals(1, root.getChildren().size());
    }

//    @Test
//    public void testLoadById() {
//        final QueryEntity query = queryService.loadById(testQuery.getId());
//
//        Assert.assertNotNull(query);
//        Assert.assertEquals("Test query", query.getName());
//        Assert.assertNotNull(query.getData());
//        final ExpressionOperator root = query.getQuery().getExpression();
//        Assert.assertEquals(1, root.getChildren().size());
//    }

    @Test
    public void testClientSideStuff1() {
        QueryEntity query = queryService.loadByUuid(refQuery.getUuid());
        query = ((QueryEntity) new BaseEntityDeProxyProcessor(true).process(query));
        queryService.save(query);
    }

    @Test
    public void testClientSideStuff2() {
        QueryEntity query = queryService.loadByUuid(testQuery.getUuid());
        query = ((QueryEntity) new BaseEntityDeProxyProcessor(true).process(query));
        queryService.save(query);
    }

//    @Test
//    public void testDeleteKids() {
//        QueryEntity query = queryService.loadByUuid(testQuery.getUuid());
//        ExpressionOperator root = query.getQuery().getExpression();
//        root.remove(0);
//        queryService.save(query);
//
//        query = queryService.loadByUuid(testQuery.getUuid());
//
//        Assert.assertEquals("Test query", query.getName());
//        root = query.getQuery().getExpression();
//        Assert.assertNull(root.getChildren());
//    }
}
