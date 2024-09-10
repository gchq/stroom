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


import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Query;
import stroom.security.api.SecurityContext;
import stroom.security.user.api.UserRefLookup;
import stroom.storedquery.impl.StoredQueryConfig;
import stroom.storedquery.impl.StoredQueryConfig.StoredQueryDbConfig;
import stroom.storedquery.impl.StoredQueryDao;
import stroom.storedquery.impl.StoredQueryHistoryCleanExecutor;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.AuditUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestStoredQueryDao {

    private static final String QUERY_COMPONENT = "Test Component";
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStoredQueryDao.class);

    @Mock
    UserRefLookup userRefLookup;
    @Mock
    private SecurityContext securityContext;
    private StoredQueryDao storedQueryDao;
    private StoredQueryHistoryCleanExecutor queryHistoryCleanExecutor;

    private StoredQuery testQuery;
    private StoredQuery refQuery;

    private DocRef dashboardRef;
    private DocRef indexRef;

    @BeforeEach
    void beforeEach() {
        Mockito.when(securityContext.getUserIdentityForAudit())
                .thenReturn("testuser");

        // need an explicit teardown and setup of the DB before each test method
        final StoredQueryDbConnProvider storedQueryDbConnProvider = DbTestUtil.getTestDbDatasource(
                new StoredQueryDbModule(), new StoredQueryDbConfig());

        // Clear the current DB.
        DbTestUtil.clear();

        final UserRef owner = UserRef.builder().uuid(UUID.randomUUID().toString()).build();
        storedQueryDao = new StoredQueryDaoImpl(storedQueryDbConnProvider,
                new QueryJsonSerialiser(),
                () -> uuid -> Optional.of(owner));

        queryHistoryCleanExecutor = new StoredQueryHistoryCleanExecutor(
                storedQueryDao,
                new StoredQueryConfig(),
                new SimpleTaskContextFactory(), userRefLookup);

        dashboardRef = new DocRef("Dashboard", "8c1bc23c-f65c-413f-ba72-7538abf90b91", "Test Dashboard");
        indexRef = new DocRef("Index", "4a085071-1d1b-4c96-8567-82f6954584a4", "Test Index");

        refQuery = new StoredQuery();
        refQuery.setName("Ref query");
        refQuery.setDashboardUuid(dashboardRef.getUuid());
        refQuery.setComponentId(QUERY_COMPONENT);
        refQuery.setQuery(Query.builder()
                .dataSource(indexRef)
                .expression(ExpressionOperator.builder().build())
                .build());
        refQuery.setOwner(owner);
        AuditUtil.stamp(securityContext, refQuery);
        storedQueryDao.create(refQuery);

        final ExpressionOperator.Builder root = ExpressionOperator.builder().op(Op.OR);
        root.addTerm("Some field", Condition.EQUALS, "Some value");

        LOGGER.info(root.toString());

        testQuery = new StoredQuery();
        testQuery.setName("Test query");
        testQuery.setDashboardUuid(dashboardRef.getUuid());
        testQuery.setComponentId(QUERY_COMPONENT);
        testQuery.setQuery(Query.builder()
                .dataSource(indexRef)
                .expression(root.build())
                .build());
        testQuery.setOwner(owner);
        AuditUtil.stamp(securityContext, testQuery);
        testQuery = storedQueryDao.create(testQuery);

        LOGGER.info(testQuery.getQuery().toString());
    }

    @Test
    void testQueryRetrieval() {
        final FindStoredQueryCriteria criteria = new FindStoredQueryCriteria();
        criteria.setDashboardUuid(dashboardRef.getUuid());
        criteria.setComponentId(QUERY_COMPONENT);
        criteria.setSort(FindStoredQueryCriteria.FIELD_TIME, true, false);

        final ResultPage<StoredQuery> list = storedQueryDao.find(criteria);

        assertThat(list.size()).isEqualTo(2);

        final StoredQuery query = list.getFirst();

        assertThat(query).isNotNull();
        assertThat(query.getName()).isEqualTo("Test query");
        assertThat(query.getQuery()).isNotNull();

        final ExpressionOperator root = query.getQuery().getExpression();

        assertThat(root.getChildren().size()).isEqualTo(1);

        final String actual = new QueryJsonSerialiser().serialise(query.getQuery());
        final String expected = """
                {
                  "dataSource" : {
                    "type" : "Index",
                    "uuid" : "4a085071-1d1b-4c96-8567-82f6954584a4",
                    "name" : "Test Index"
                  },
                  "expression" : {
                    "type" : "operator",
                    "op" : "OR",
                    "children" : [ {
                      "type" : "term",
                      "field" : "Some field",
                      "condition" : "EQUALS",
                      "value" : "Some value"
                    } ]
                  }
                }""";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testOldHistoryDeletion() {
        Mockito.when(userRefLookup.getByUuid(Mockito.anyString()))
                .thenReturn(Optional.of(UserRef
                        .builder()
                        .uuid("dummy")
                        .subjectId("dummy")
                        .displayName("dummy")
                        .build()));

        final FindStoredQueryCriteria criteria = new FindStoredQueryCriteria();
        criteria.setDashboardUuid(dashboardRef.getUuid());
        criteria.setComponentId(QUERY_COMPONENT);
        criteria.setSort(FindStoredQueryCriteria.FIELD_TIME, true, false);

        ResultPage<StoredQuery> list = storedQueryDao.find(criteria);
        assertThat(list.size()).isEqualTo(2);

        StoredQuery query = list.getFirst();

        // Now insert the same query over 100 times.
        for (int i = 0; i < 120; i++) {
            final StoredQuery newQuery = new StoredQuery();
            newQuery.setName("History");
            newQuery.setDashboardUuid(query.getDashboardUuid());
            newQuery.setComponentId(query.getComponentId());
            newQuery.setFavourite(false);
            newQuery.setQuery(query.getQuery());
            newQuery.setOwner(query.getOwner());
            AuditUtil.stamp(securityContext, newQuery);
            storedQueryDao.create(newQuery);
        }

        // Clean the history.
        queryHistoryCleanExecutor.exec();

        list = storedQueryDao.find(criteria);
        assertThat(list.size()).isEqualTo(100);
    }

    @Test
    void testLoad() {
        StoredQuery query = storedQueryDao.fetch(testQuery.getId()).orElse(null);

        assertThat(query).isNotNull();
        assertThat(query.getName()).isEqualTo("Test query");
        assertThat(query.getQuery()).isNotNull();
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
