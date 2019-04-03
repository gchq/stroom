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

package stroom.storedquery.impl.db;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.db.util.DbUtil;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Query;
import stroom.security.api.SecurityContext;
import stroom.storedquery.impl.StoredQueryDao;
import stroom.storedquery.impl.StoredQueryHistoryCleanExecutor;
import stroom.storedquery.impl.StoredQueryHistoryConfig;
import stroom.task.api.SimpleTaskContext;
import stroom.util.AuditUtil;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.Sort.Direction;

import java.sql.SQLException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestStoredQueryDao {
    private static final String QUERY_COMPONENT = "Test Component";
    private static Logger LOGGER = LoggerFactory.getLogger(TestStoredQueryDao.class);

    @Mock
    private SecurityContext securityContext;
    private StoredQueryDao storedQueryDao;
    private StoredQueryHistoryCleanExecutor queryHistoryCleanExecutor;

    private StoredQuery testQuery;
    private StoredQuery refQuery;

    private DocRef dashboardRef;
    private DocRef indexRef;

    @BeforeEach
    void beforeEach() throws SQLException {
        Mockito.when(securityContext.getUserId()).thenReturn("testuser");

        // need an explicit teardown and setup of the DB before each test method
        final ConnectionProvider connectionProvider = new StoredQueryDbModule().getConnectionProvider(StoredQueryConfig::new);
        DbUtil.clearAllTables(connectionProvider.getConnection());

        storedQueryDao = new StoredQueryDaoImpl(connectionProvider);
        queryHistoryCleanExecutor = new StoredQueryHistoryCleanExecutor(new SimpleTaskContext(), storedQueryDao, new StoredQueryHistoryConfig());

        dashboardRef = new DocRef("Dashboard", "8c1bc23c-f65c-413f-ba72-7538abf90b91", "Test Dashboard");
        indexRef = new DocRef("Index", "4a085071-1d1b-4c96-8567-82f6954584a4", "Test Index");

        refQuery = new StoredQuery();
        refQuery.setName("Ref query");
        refQuery.setDashboardUuid(dashboardRef.getUuid());
        refQuery.setComponentId(QUERY_COMPONENT);
        refQuery.setQuery(new Query(indexRef, new ExpressionOperator(null, Op.AND, Collections.emptyList())));
        AuditUtil.stamp(securityContext.getUserId(), refQuery);
        storedQueryDao.create(refQuery);

        final ExpressionOperator.Builder root = new ExpressionOperator.Builder(Op.OR);
        root.addTerm("Some field", Condition.CONTAINS, "Some value");

        LOGGER.info(root.toString());

        testQuery = new StoredQuery();
        testQuery.setName("Test query");
        testQuery.setDashboardUuid(dashboardRef.getUuid());
        testQuery.setComponentId(QUERY_COMPONENT);
        testQuery.setQuery(new Query(indexRef, root.build()));
        AuditUtil.stamp(securityContext.getUserId(), testQuery);
        testQuery = storedQueryDao.create(testQuery);

        LOGGER.info(testQuery.getQuery().toString());
    }

    @Test
    void testQueryRetrieval() {
        final FindStoredQueryCriteria criteria = new FindStoredQueryCriteria();
        criteria.setDashboardUuid(dashboardRef.getUuid());
        criteria.setComponentId(QUERY_COMPONENT);
        criteria.setSort(FindStoredQueryCriteria.FIELD_TIME, Direction.DESCENDING, false);

        final BaseResultList<StoredQuery> list = storedQueryDao.find(criteria);

        assertThat(list.size()).isEqualTo(2);

        final StoredQuery query = list.get(0);

        assertThat(query).isNotNull();
        assertThat(query.getName()).isEqualTo("Test query");
        assertThat(query.getData()).isNotNull();

        final ExpressionOperator root = query.getQuery().getExpression();

        assertThat(root.getChildren().size()).isEqualTo(1);

        final String actual = query.getData();
        final String expected = "{\n" +
                "  \"dataSource\" : {\n" +
                "    \"type\" : \"Index\",\n" +
                "    \"uuid\" : \"4a085071-1d1b-4c96-8567-82f6954584a4\",\n" +
                "    \"name\" : \"Test Index\"\n" +
                "  },\n" +
                "  \"expression\" : {\n" +
                "    \"type\" : \"operator\",\n" +
                "    \"op\" : \"OR\",\n" +
                "    \"children\" : [ {\n" +
                "      \"type\" : \"term\",\n" +
                "      \"field\" : \"Some field\",\n" +
                "      \"condition\" : \"CONTAINS\",\n" +
                "      \"value\" : \"Some value\",\n" +
                "      \"enabled\" : true\n" +
                "    } ],\n" +
                "    \"enabled\" : true\n" +
                "  }\n" +
                "}";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testOldHistoryDeletion() {
        final FindStoredQueryCriteria criteria = new FindStoredQueryCriteria();
        criteria.setDashboardUuid(dashboardRef.getUuid());
        criteria.setComponentId(QUERY_COMPONENT);
        criteria.setSort(FindStoredQueryCriteria.FIELD_TIME, Direction.DESCENDING, false);

        BaseResultList<StoredQuery> list = storedQueryDao.find(criteria);
        assertThat(list.size()).isEqualTo(2);

        StoredQuery query = list.get(0);

        // Now insert the same query over 100 times.
        for (int i = 0; i < 120; i++) {
            final StoredQuery newQuery = new StoredQuery();
            newQuery.setName("History");
            newQuery.setDashboardUuid(query.getDashboardUuid());
            newQuery.setComponentId(query.getComponentId());
            newQuery.setFavourite(false);
            newQuery.setQuery(query.getQuery());
            newQuery.setData(query.getData());
            AuditUtil.stamp(securityContext.getUserId(), newQuery);
            storedQueryDao.create(newQuery);
        }

        // Clean the history.
        queryHistoryCleanExecutor.clean(false);

        list = storedQueryDao.find(criteria);
        assertThat(list.size()).isEqualTo(100);
    }

    @Test
    void testLoad() {
        StoredQuery query = storedQueryDao.fetch(testQuery.getId()).orElse(null);

        assertThat(query).isNotNull();
        assertThat(query.getName()).isEqualTo("Test query");
        assertThat(query.getData()).isNotNull();
        final ExpressionOperator root = query.getQuery().getExpression();
        assertThat(root.getChildren().size()).isEqualTo(1);
    }

//    @Test
//    public void testLoadById() {
//        final QueryEntity query = queryService.loadById(testQuery.getId());
//
//        assertThat(query).isNotNull();
//        assertThat(query.getName()).isEqualTo("Test query");
//        assertThat(query.getMeta()).isNotNull();
//        final ExpressionOperator root = query.getQuery().getExpression();
//        assertThat(root.getChildren().size()).isEqualTo(1);
//    }

//    @Test
//    void testClientSideStuff1() {
//        StoredQuery query = storedQueryDao.loadByUuid(refQuery.getUuid());
//        query = ((StoredQuery) new BaseEntityDeProxyProcessor(true).process(query));
//        queryService.save(query);
//    }
//
//    @Test
//    void testClientSideStuff2() {
//        StoredQuery query = queryService.loadByUuid(testQuery.getUuid());
//        query = ((StoredQuery) new BaseEntityDeProxyProcessor(true).process(query));
//        queryService.save(query);
//    }

//    @Test
//    public void testDeleteKids() {
//        QueryEntity query = queryService.loadByUuid(testQuery.getUuid());
//        ExpressionOperator root = query.getQuery().getExpression();
//        root.remove(0);
//        queryService.save(query);
//
//        query = queryService.loadByUuid(testQuery.getUuid());
//
//        assertThat(query.getName()).isEqualTo("Test query");
//        root = query.getQuery().getExpression();
//        assertThat(root.getChildren()).isNull();
//    }
}
