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

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.SQLNameConstants;
import stroom.explorer.shared.ExplorerConstants;
import stroom.query.api.v2.DocRef;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class V6_0_0_11__Explorer implements JdbcMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V6_0_0_11__Explorer.class);

    private Map<Long, List<Long>> folderIdToAncestorIDMap = new HashMap<>();

    @Override
    public void migrate(final Connection connection) throws Exception {
        // Create explorer tree data from existing child/parent node relationships.
        createExplorerTree(connection);
    }

    private void createExplorerTree(final Connection connection) throws Exception {
        // Create a map of document references for all folders.
        final Map<Long, Set<Ref>> docRefMap = createDocRefMap(connection, SQLNameConstants.FOLDER, ExplorerConstants.FOLDER);

        // Insert System root node.
        final DocRef root = ExplorerConstants.ROOT_DOC_REF;

        Long nodeId = getExplorerTreeNodeId(connection, root);
        List<Long> ancestorIdList;

        if (nodeId == null) {
            createExplorerTreeNode(connection, root, null);
            nodeId = getExplorerTreeNodeId(connection, root);
            ancestorIdList = Collections.singletonList(nodeId);

            // Insert paths.
            insertPaths(connection, nodeId, ancestorIdList);
        } else {
            ancestorIdList = Collections.singletonList(nodeId);
        }

        addAnnotationsNode(connection, nodeId);

        // Store the mapping of folder id to explorer node ancestors.
        folderIdToAncestorIDMap.put(0L, ancestorIdList);

        // Add child nodes
        addFolderNodes(connection, 0L, ancestorIdList, docRefMap);


        // Migrate other document types.
        addOtherNodes(connection, "STAT_DAT_SRC", "StatisticStore", "DataSource");
        addOtherNodes(connection, "IDX", "Index", "DataSource");
        addOtherNodes(connection, "FD", "Feed");
        addOtherNodes(connection, "XML_SCHEMA", "XMLSchema");
        addOtherNodes(connection, "VIS", "Visualisation");
        addOtherNodes(connection, "TXT_CONV", "TextConverter");
        addOtherNodes(connection, "SCRIPT", "Script");
        addOtherNodes(connection, "PIPE", "Pipeline");
        addOtherNodes(connection, "DASH", "Dashboard");
        addOtherNodes(connection, "DICT", "Dictionary");
        addOtherNodes(connection, "XSLT", "XSLT");
    }

    private void addAnnotationsNode(final Connection connection,
                                    final Long rootNodeId) throws Exception {
        final DocRef root = ExplorerConstants.ANNOTATIONS_DOC_REF;

        Long annotationsNodeId = getExplorerTreeNodeId(connection, root);

        if (annotationsNodeId == null) {
            createExplorerTreeNode(connection, root, "DataSource");
            annotationsNodeId = getExplorerTreeNodeId(connection, root);

            // Insert paths.
            insertPaths(connection, annotationsNodeId, Arrays.asList(annotationsNodeId, rootNodeId));
        }
    }

    private void addOtherNodes(final Connection connection, final String tableName, final String type) throws SQLException {
        addOtherNodes(connection, tableName, type, null);
    }

    private void addOtherNodes(final Connection connection, final String tableName, final String type, final String tags) throws SQLException {
        final Map<Long, Set<Ref>> feedMap = createDocRefMap(connection, tableName, type);
        for (final Map.Entry<Long, Set<Ref>> entry : feedMap.entrySet()) {
            final long folderId = entry.getKey();
            final Set<Ref> refs = entry.getValue();
            final List<Long> parentAncestorIdList = folderIdToAncestorIDMap.get(folderId);

            for (final Ref ref : refs) {
                Long nodeId = getExplorerTreeNodeId(connection, ref.getDocRef());

                if (nodeId == null) {
                    createExplorerTreeNode(connection, ref.getDocRef(), tags);
                    nodeId = getExplorerTreeNodeId(connection, ref.getDocRef());

                    final List<Long> ancestorIdList = new ArrayList<>(parentAncestorIdList);
                    ancestorIdList.add(0, nodeId);

                    // Insert paths.
                    insertPaths(connection, nodeId, ancestorIdList);
                }
            }
        }
    }

    private String makeTagString(final String... tags) {
        String tagString = null;
        if (tags != null && tags.length > 0) {
            final StringBuilder sb = new StringBuilder();
            for (final String tag : tags) {
                sb.append(tag);
                sb.append(":");
            }
            sb.setLength(sb.length() - 1);
            tagString = sb.toString();
        }
        return tagString;
    }

    private void addFolderNodes(final Connection connection, final Long parentId, final List<Long> parentAncestorIdList, final Map<Long, Set<Ref>> docRefMap) throws SQLException {
        final Set<Ref> childNodes = docRefMap.get(parentId);

        if (childNodes != null && childNodes.size() > 0) {
            // Add nodes
            for (final Ref ref : childNodes) {
                final List<Long> ancestorIdList = new ArrayList<>(parentAncestorIdList);

                Long nodeId = getExplorerTreeNodeId(connection, ref.getDocRef());
                if (nodeId == null) {
                    createExplorerTreeNode(connection, ref.getDocRef(), null);
                    nodeId = getExplorerTreeNodeId(connection, ref.getDocRef());
                    ancestorIdList.add(0, nodeId);

                    // Insert paths.
                    insertPaths(connection, nodeId, ancestorIdList);

                } else {
                    ancestorIdList.add(0, nodeId);
                }

                // Store the mapping of folder id to explorer node ancestors.
                folderIdToAncestorIDMap.put(ref.id, ancestorIdList);

                // Recurse to insert child nodes.
                addFolderNodes(connection, ref.id, ancestorIdList, docRefMap);
            }
        }
    }

    private Map<Long, Set<Ref>> createDocRefMap(final Connection connection, final String tableName, final String type) throws SQLException {
        final Map<Long, Set<Ref>> refMap = new HashMap<>();

        try (final Statement statement = connection.createStatement()) {
            try (final ResultSet resultSet = statement.executeQuery("SELECT ID, UUID, NAME, FK_FOLDER_ID FROM " + tableName)) {
                while (resultSet.next()) {
                    final long id = resultSet.getLong(1);
                    final String uuid = resultSet.getString(2);
                    final String name = resultSet.getString(3);
                    Long parentId = resultSet.getLong(4);
                    if (parentId == null) {
                        parentId = 0L;
                    }

                    final Ref ref = new Ref(id, type, uuid, name);
                    refMap.computeIfAbsent(parentId, k -> new HashSet<>()).add(ref);
                }
            }
        }
        return refMap;
    }

    private void createExplorerTreeNode(final Connection connection, final DocRef docRef, final String tags) throws SQLException {
        // Insert node entry.
        try (final PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO explorerTreeNode (type, uuid, name, tags) VALUES (?, ?, ?, ?)")) {
            preparedStatement.setString(1, docRef.getType());
            preparedStatement.setString(2, docRef.getUuid());
            preparedStatement.setString(3, docRef.getName());
            preparedStatement.setString(4, tags);
            preparedStatement.executeUpdate();
        }
    }

    private Long getExplorerTreeNodeId(final Connection connection, final DocRef docRef) throws SQLException {
        Long nodeId = null;

        // Fetch id for newly inserted node entry.
        try (final PreparedStatement preparedStatement = connection.prepareStatement("SELECT id FROM explorerTreeNode WHERE type = ? AND uuid = ?;")) {
            preparedStatement.setString(1, docRef.getType());
            preparedStatement.setString(2, docRef.getUuid());

            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    nodeId = resultSet.getLong(1);
                }
            }
        }

        return nodeId;
    }

    private void insertPaths(final Connection connection, final Long id, final List<Long> ancestorIdList) throws SQLException {
        // Insert ancestor references.
        for (int i = 0; i < ancestorIdList.size(); i++) {
            final Long ancestorId = ancestorIdList.get(i);
            insertReference(connection, ancestorId, id, (long) i);
        }
    }

    private void insertReference(final Connection connection, final Long ancestor, final Long descendant, final Long depth) throws SQLException {
        // Insert self reference.
        try (final PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO explorerTreePath (ancestor, descendant, depth, orderIndex) VALUES (?, ?, ?, ?)")) {
            preparedStatement.setLong(1, ancestor);
            preparedStatement.setLong(2, descendant);
            preparedStatement.setLong(3, depth);
            preparedStatement.setLong(4, -1);
            preparedStatement.executeUpdate();
        }
    }

    private static class Ref {
        private final long id;
        private final String type;
        private final String uuid;
        private final String name;

        public Ref(final long id, final String type, final String uuid, final String name) {
            this.id = id;
            this.type = type;
            this.uuid = uuid;
            this.name = name;
        }

        public DocRef getDocRef() {
            return new DocRef(type, uuid, name);
        }
    }
}
