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

package stroom.processor.impl;


import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorTaskService;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessorTaskCreator extends AbstractCoreIntegrationTest {

    public static final Logger LOGGER = LoggerFactory.getLogger(TestProcessorTaskCreator.class);

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private ProcessorTaskCreatorImpl processorTaskCreator;
    @Inject
    private ProcessorTaskService processorTaskService;
    @Inject
    private ProcessorTaskDeleteExecutor streamTaskDeleteExecutor;
    @Inject
    private MetaService metaService;

    @Test
    void testBasic() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        assertThat(processorTaskService.find(new ExpressionCriteria()).size()).isZero();
        final List<Meta> streams = metaService.find(new FindMetaCriteria()).getValues();
        assertThat(streams.size()).isEqualTo(1);

        ExpressionOperator expression = ExpressionOperator.builder().build();
        assertThat(processorTaskCreator.runSelectMetaQuery(expression,
                0,
                null,
                null,
                null,
                false,
                100).size()).isEqualTo(1);

        expression = ExpressionOperator.builder().addTextTerm(MetaFields.FEED, Condition.EQUALS, feedName).build();
        assertThat(processorTaskCreator.runSelectMetaQuery(expression,
                0,
                null,
                null,
                null,
                false,
                100).size()).isEqualTo(1);

        expression = ExpressionOperator.builder().addTextTerm(MetaFields.FEED, Condition.EQUALS, "otherFed").build();
        assertThat(processorTaskCreator.runSelectMetaQuery(expression,
                0,
                null,
                null,
                null,
                false,
                100).size()).isEqualTo(0);

        expression = ExpressionOperator.builder().addDocRefTerm(MetaFields.PIPELINE,
                Condition.IS_DOC_REF,
                new DocRef(PipelineDoc.TYPE, "1234")).build();
        assertThat(processorTaskCreator.runSelectMetaQuery(expression,
                0,
                null,
                null,
                null,
                false,
                100).size()).isEqualTo(0);

        // Check DB cleanup.
        expression = ExpressionOperator.builder().build();
        assertThat(processorTaskCreator.runSelectMetaQuery(expression,
                0,
                null,
                null,
                null,
                false,
                100).size()).isEqualTo(1);
        streamTaskDeleteExecutor.delete(Instant.EPOCH);
        assertThat(processorTaskCreator.runSelectMetaQuery(expression,
                0,
                null,
                null,
                null,
                false,
                100).size()).isEqualTo(1);
    }

//    @Test
//    void testDeleteQuery() {
//        streamTaskDeleteExecutor.delete(0);
//    }
//
//    @Disabled //performance test to compare time
//    @Test
//    void testMultiInsertPerformance() throws SQLException {
////
////        /*
////        create table insert_test (
////            id INT,
////            col2 varchar(255),
////            col3 varchar(255),
////            col4 varchar(255),
////            col5 varchar(255),
////            col6 varchar(255),
////            col7 varchar(255),
////            col8 varchar(255),
////            col9 varchar(255),
////            col10 varchar(255),
////            col11 varchar(255)
////        );
////         */
////
////        stroomEntityManager.executeNativeUpdate(new SqlBuilder("delete from insert_test"));
////        stroomEntityManager.executeNativeUpdate(new SqlBuilder("delete from insert_test2"));
////        stroomEntityManager.executeNativeUpdate(new SqlBuilder("delete from insert_test3"));
////
////        SqlBuilder singleStmt = new SqlBuilder();
////        singleStmt.append(
// "insert into insert_test (id, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11)")
////                .append(" values (")
////                .arg(1).append(",")
////                .arg("col2 text").append(",")
////                .arg("col3 text").append(",")
////                .arg("col4 text").append(",")
////                .arg("col5 text").append(",")
////                .arg("col6 text").append(",")
////                .arg("col7 text").append(",")
////                .arg("col8 text").append(",")
////                .arg("col9 text").append(",")
////                .arg("col10 text").append(",")
////                .arg("col11 text")
////                .append(")");
////
////        LOGGER.info("Inserting records one by one");
////
////        LOGGER.info("SQL: {}", singleStmt.toString());
////
////        int n = 10_000;
////        int batchSize = 1;
////
////        Instant startTime = Instant.now();
////
////        IntStream.rangeClosed(1, n).forEach(i -> {
////            Object[] args = {i, "x", "x", "x", "x", "x", "x", "x", "x", "x", "x"};
////            SqlBuilder stmt = new SqlBuilder(singleStmt.toString(), args);
//////            dumpSqlBuilder(stmt);
////            stroomEntityManager.executeNativeUpdate(stmt);
////        });
////
////        LOGGER.info("Finished {} inserts in {}", n, Duration.between(startTime, Instant.now()));
////        LOGGER.info("Batch size: {}", batchSize);
////
////        SqlBuilder multiStmt = null;
////        String header =
// "insert into insert_test2 (id, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11) values ";
////
////        int qryCnt = 0;
////        startTime = Instant.now();
////
////        for (int i = 1; i <= n; i++) {
////            if (multiStmt == null) {
////                multiStmt = new SqlBuilder();
////                multiStmt.append(header);
////            }
////            multiStmt
////                    .append("(")
////                    .arg(i).append(",")
////                    .arg("col2 text").append(",")
////                    .arg("col3 text").append(",")
////                    .arg("col4 text").append(",")
////                    .arg("col5 text").append(",")
////                    .arg("col6 text").append(",")
////                    .arg("col7 text").append(",")
////                    .arg("col8 text").append(",")
////                    .arg("col9 text").append(",")
////                    .arg("col10 text").append(",")
////                    .arg("col11 text")
////                    .append(")");
////
////            if (i % batchSize == 0) {
////                try {
////                    stroomEntityManager.executeNativeUpdate(multiStmt);
////                    qryCnt++;
////                } catch (final RuntimeException e) {
////                    dumpSqlBuilder(multiStmt);
////                    throw e;
////                }
////                multiStmt = null;
////            } else {
////                multiStmt.append(",");
////            }
////        }
////
////        String header3 =
// "insert into insert_test3 (id, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11) values ";
////        qryCnt = 0;
////        StringBuilder stringBuilder = null;
////        List<Object> args = null;
////        startTime = Instant.now();
////
////        try (final Connection connection = connectionProvider.getConnection()) {
////            for (int i = 1; i <= n; i++) {
////                if (stringBuilder == null) {
////                    stringBuilder = new StringBuilder();
////                    stringBuilder.append(header3);
////                    args = new ArrayList<>();
////                }
////
////                args.add(i);
////                args.add("col2 text");
////                args.add("col3 text");
////                args.add("col4 text");
////                args.add("col5 text");
////                args.add("col6 text");
////                args.add("col7 text");
////                args.add("col8 text");
////                args.add("col9 text");
////                args.add("col10 text");
////                args.add("col11 text");
////
////                stringBuilder.append("(?,?,?,?,?,?,?,?,?,?,?)");
////
////                if (i % batchSize == 0) {
////                    try {
////                        final int count = ConnectionUtil.executeUpdate(
////                                connection,
////                                stringBuilder.toString(),
////                                args);
////
////                        qryCnt++;
////                    } catch (final RuntimeException e) {
////                        dumpStringBuilder(stringBuilder, args);
////                        throw e;
////                    }
////                    stringBuilder = null;
////                    args = null;
////                } else {
////                    stringBuilder.append(",");
////                }
////            }
////        }
////
////        LOGGER.info("Finished {} direct multi inserts in {}", qryCnt, Duration.between(startTime, Instant.now()));
//    }
//
////    private void dumpSqlBuilder(final SqlBuilder sqlBuilder) {
////
////        String argsStr = StreamSupport.stream(sqlBuilder.getArgs().spliterator(), false)
////                .map(Object::toString)
////                .map(str -> "\"" + str + "\"")
////                .collect(Collectors.joining(","));
////        LOGGER.info("SQL: [{}], args [{}]", sqlBuilder.toString(), argsStr);
////    }
//
//    private void dumpStringBuilder(final StringBuilder stringBuilder, List<Object> args) {
//
//        String argsStr = args.stream()
//                .map(Object::toString)
//                .map(str -> "\"" + str + "\"")
//                .collect(Collectors.joining(","));
//        LOGGER.info("SQL: [{}], args [{}]", stringBuilder.toString(), argsStr);
//    }
}
