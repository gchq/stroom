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

package stroom.db.migration.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.migration.EntityReferenceReplacer;
import stroom.entity.server.ObjectMarshaller;
import stroom.query.api.DocRef;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class V5_0_0_10__Pipeline implements JdbcMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V5_0_0_10__Pipeline.class);

//    private static final String[] TABLES = {"FOLDER","TXT_CONV","XSLT","XML_SCHEMA","PIPE","FD","IDX","STAT_DAT_SRC","ANAL_OUT_DAT_SRC","DASH","SCRIPT","VIS","DICT","QUERY"};

    @Override
    public void migrate(Connection connection) throws Exception {
//        for (final String table : TABLES) {
//            final List<Long> idList = new ArrayList<>();
//            try (final Statement statement = connection.createStatement(); final ResultSet resultSet = statement.executeQuery("SELECT ID FROM " + table)) {
//                while (resultSet.next()) {
//                    idList.add(resultSet.getLong(1));
//                }
//            }
//
//            for (final Long id : idList) {
//                try (final Statement statement = connection.createStatement()) {
//                    final String sql = "UPDATE + " + table + " SET (UUID) ('" + UUID.randomUUID().toString() + "') WHERE ID = " + id;
//                    statement.executeUpdate(sql);
//                }
//            }
//        }
//
//
//        // TEST DATA
//        for (int i = 1; i < 11; i++) {
//            Long parentId = null;
//            try (final Statement statement = connection.createStatement()) {
//                try (final ResultSet resultSet = statement.executeQuery("SELECT ID FROM PIPE ORDER BY ID DESC;")) {
//                    while (resultSet.next() && parentId == null) {
//                        parentId = resultSet.getLong(1);
//                    }
//                }
//            }
//
//            try (final Statement statement = connection.createStatement()) {
//                statement.executeUpdate("INSERT INTO PIPE (ID,VER,NAME,UUID,DESCRIP,FK_PIPE_ID) VALUES (" + i + ",1,'test" + i + "',uuid(),'test'," + parentId + ");");
//            }
//        }


        // Change parent pipeline references to be document references.
        makeParentReferenceDocRef(connection);

        // Update pipeline data.
        updatePipelineData(connection);
    }

    private void makeParentReferenceDocRef(final Connection connection) throws Exception {
        final ObjectMarshaller<DocRef> objectMarshaller = new ObjectMarshaller<>(DocRef.class);

        try (final Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE PIPE ADD COLUMN PARNT_PIPE longtext;");
        }
        try (final Statement statement = connection.createStatement()) {
            try (final ResultSet resultSet = statement.executeQuery("SELECT p.ID, pp.UUID, pp.NAME FROM PIPE p JOIN PIPE pp ON (p.FK_PIPE_ID = pp.ID);")) {
                while (resultSet.next()) {
                    final long id = resultSet.getLong(1);
                    final String parentUUID = resultSet.getString(2);
                    final String parentName = resultSet.getString(3);
                    final DocRef docRef = new DocRef("Pipeline", parentUUID, parentName);
                    final String refXML = objectMarshaller.marshal(docRef);

                    try (final PreparedStatement preparedStatement = connection.prepareStatement("UPDATE PIPE SET PARNT_PIPE = ? WHERE ID = ?")) {
                        preparedStatement.setString(1, refXML);
                        preparedStatement.setLong(2, id);
                        preparedStatement.executeUpdate();
                    }
                }
            }
        }
        try (final Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE PIPE DROP FOREIGN KEY PIPE_FK_PIPE_ID");
        }
        try (final Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE PIPE DROP COLUMN FK_PIPE_ID;");
        }
    }

    private void updatePipelineData(final Connection connection) throws Exception {
        final EntityReferenceReplacer entityReferenceReplacer = new EntityReferenceReplacer();

        try (final Statement statement = connection.createStatement()) {
            try (final ResultSet resultSet = statement.executeQuery("SELECT ID, NAME, DAT FROM PIPE;")) {
                while (resultSet.next()) {
                    final long id = resultSet.getLong(1);
                    final String name = resultSet.getString(2);
                    final String data = resultSet.getString(3);

                    if (data != null) {
                        String newData = data;

                        // Add CombinedParser element if it is missing.
                        if (!newData.contains("CombinedParser")) {
                            final String replacement = ""
                                    + "<elements>\n"
                                    + "      <add>\n"
                                    + "         <element>\n"
                                    + "            <id>parser</id>\n"
                                    + "            <type>CombinedParser</type>\n"
                                    + "         </element>";

                            newData = newData.replaceAll("<elements>\\s*<add>", replacement);
                        }

                        // Replace any instances of Parser with CombinedParser.
                        newData = newData.replaceAll(">Parser<", ">CombinedParser<");

                        newData = newData.replaceAll(">StreamStoreOutputStreamProvider<", ">StreamAppender<");
                        newData = newData.replaceAll(">streamStoreOutputStreamProvider<", ">streamAppender<");

                        newData = newData.replaceAll(">FileSystemOutputStreamProvider<", ">FileAppender<");
                        newData = newData.replaceAll(">fileSystemOutputStreamProvider<", ">fileAppender<");

                        newData = newData.replaceAll(">StatisticsFilter<", ">OldStatisticsFilter<");
                        newData = newData.replaceAll(">statisticsFilter<", ">oldStatisticsFilter<");

                        newData = newData.replaceAll(">NewStatisticsFilter<", ">StatisticsFilter<");
                        newData = newData.replaceAll(">newStatisticsFilter<", ">statisticsFilter<");

                        newData = newData.replaceAll(">StatisticsDataSource<", ">StatisticStore<");
                        newData = newData.replaceAll(">StatisticDataSource<", ">StatisticStore<");

                        newData = entityReferenceReplacer.replaceEntityReferences(connection, newData);

                        if (!newData.equals(data)) {
                            LOGGER.info("Upgrading pipeline: " + name);

                            try (final PreparedStatement preparedStatement = connection.prepareStatement("UPDATE PIPE SET DAT = ? WHERE ID = ?")) {
                                preparedStatement.setString(1, newData);
                                preparedStatement.setLong(2, id);
                                preparedStatement.executeUpdate();
                            }
                        }
                    }
                }
            }
        }
    }
}
