/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.search.server;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.AbstractCoreIntegrationTest;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.DashboardService;
import stroom.dashboard.shared.FindQueryCriteria;
import stroom.dashboard.shared.QueryEntity;
import stroom.dashboard.shared.QueryService;
import stroom.entity.server.util.BaseEntityDeProxyProcessor;
import stroom.entity.shared.BaseCriteria.OrderByDirection;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.FolderService;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.query.api.v1.DocRef;
import stroom.query.api.v1.ExpressionBuilder;
import stroom.query.api.v1.ExpressionOperator;
import stroom.query.api.v1.ExpressionOperator.Op;
import stroom.query.api.v1.ExpressionTerm.Condition;
import stroom.query.api.v1.Query;
import stroom.security.shared.User;
import stroom.security.shared.UserService;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;
import java.util.Arrays;

public class TestQueryServiceImpl extends AbstractCoreIntegrationTest {
    private static Logger LOGGER = LoggerFactory.getLogger(TestQueryServiceImpl.class);

    @Resource
    private DashboardService dashboardService;
    @Resource
    private QueryService queryService;
    @Resource
    private IndexService indexService;
    @Resource
    private UserService userService;
    @Resource
    private FolderService folderService;

    private Dashboard dashboard;
    private User user;
    private QueryEntity testQuery;
    private QueryEntity refQuery;

    @Override
    protected void onBefore() {
        // need an explicit teardown and setup of the DB before each test method
        clean();

        user = userService.createUser("testuser");

        final DocRef testFolder = DocRefUtil.create(folderService.create(null, "Test Folder"));

        dashboard = dashboardService.create(testFolder, "Test");

        final Index index = indexService.create(testFolder, "Test index");
        final DocRef dataSourceRef = DocRefUtil.create(index);

        refQuery = queryService.create(null, "Ref query");
        refQuery.setDashboard(dashboard);
        refQuery.setQuery(new Query(dataSourceRef, new ExpressionOperator(null, Op.AND, Arrays.asList())));
        queryService.save(refQuery);

        // Ensure the two query creation times are separated by one second so that ordering by time works correctly in
        // the test.
        ThreadUtil.sleep(1000);

        final ExpressionBuilder root = new ExpressionBuilder(Op.OR);
        root.addTerm("Some field", Condition.CONTAINS, "Some value");

        LOGGER.info(root.toString());

        testQuery = queryService.create(null, "Test query");
        testQuery.setDashboard(dashboard);
        testQuery.setQuery(new Query(dataSourceRef, root.build()));
        testQuery = queryService.save(testQuery);

        LOGGER.info(testQuery.getQuery().toString());
    }

    @Test
    public void testQueryRetrieval() {
        final FindQueryCriteria criteria = new FindQueryCriteria();
        criteria.obtainDashboardIdSet().add(dashboard.getId());
        criteria.setOrderBy(FindQueryCriteria.ORDER_BY_TIME, OrderByDirection.DESCENDING);

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

    @Test
    public void testLoadById() {
        final QueryEntity query = queryService.loadById(testQuery.getId());

        Assert.assertNotNull(query);
        Assert.assertEquals("Test query", query.getName());
        Assert.assertNotNull(query.getData());
        final ExpressionOperator root = query.getQuery().getExpression();
        Assert.assertEquals(1, root.getChildren().size());
    }

    @Test
    public void testClientSideStuff1() {
        QueryEntity query = queryService.loadById(refQuery.getId());
        query = ((QueryEntity) new BaseEntityDeProxyProcessor(true).process(query));
        queryService.save(query);
    }

    @Test
    public void testClientSideStuff2() {
        QueryEntity query = queryService.loadById(testQuery.getId());
        query = ((QueryEntity) new BaseEntityDeProxyProcessor(true).process(query));
        queryService.save(query);
    }

//    @Test
//    public void testDeleteKids() {
//        QueryEntity query = queryService.loadById(testQuery.getId());
//        ExpressionOperator root = query.getQuery().getExpression();
//        root.remove(0);
//        queryService.save(query);
//
//        query = queryService.loadById(testQuery.getId());
//
//        Assert.assertEquals("Test query", query.getName());
//        root = query.getQuery().getExpression();
//        Assert.assertNull(root.getChildren());
//    }
}
