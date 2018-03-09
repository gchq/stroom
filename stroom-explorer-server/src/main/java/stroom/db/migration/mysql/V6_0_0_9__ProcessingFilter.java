package stroom.db.migration.mysql;

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.entity.server.util.XMLMarshallerUtil;
import stroom.entity.shared.IdRange;
import stroom.entity.shared.Range;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.stream.OldFindStreamCriteria;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.StreamDataSource;
import stroom.util.date.DateUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static stroom.query.api.v2.ExpressionTerm.Condition.IN_CONDITION_DELIMITER;

public class V6_0_0_9__ProcessingFilter implements JdbcMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V6_0_0_9__ProcessingFilter.class);

    private JAXBContext findStreamCriteriaJaxb;
    private JAXBContext queryDataJaxb;
    private JAXBException jaxbException;
    private final boolean writeUpdates;

    private static final String UPGRADE_USER = "upgrade";

    public V6_0_0_9__ProcessingFilter() {
        this(true);
    }

    public V6_0_0_9__ProcessingFilter(final boolean writeUpdates) {
        System.setProperty("javax.xml.transform.TransformerFactory",
                "net.sf.saxon.TransformerFactoryImpl");
        this.writeUpdates = writeUpdates;
        try {
            findStreamCriteriaJaxb = JAXBContext.newInstance(OldFindStreamCriteria.class);
            queryDataJaxb = JAXBContext.newInstance(QueryData.class);
        } catch (JAXBException e) {
            jaxbException = e;
        }
    }

    @Override
    public void migrate(final Connection connection) throws Exception {

        // Get all the existing stream criteria
        final Map<Long, String> findStreamCriteriaStrById = new HashMap<>();
        final Map<Long, OldFindStreamCriteria> findStreamCriteriaById = new HashMap<>();
        try (final PreparedStatement stmt = connection.prepareStatement("SELECT id, DAT FROM STRM_PROC_FILT")) {

            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        final long id = rs.getLong(1);
                        final Blob datBlob = rs.getBlob(2);

                        int blobLength = (int) datBlob.length();
                        byte[] blobAsBytes = datBlob.getBytes(1, blobLength);

                        datBlob.free();

                        final String datAsString = new String(blobAsBytes);

                        final OldFindStreamCriteria streamCriteria = unmarshalCriteria(datAsString);

                        findStreamCriteriaStrById.put(id, datAsString);
                        findStreamCriteriaById.put(id, streamCriteria);
                    } catch (final Exception e) {
                        LOGGER.error("Could not get old stream criteria {}", e.getLocalizedMessage());
                        throw e;
                    }
                }
            }
        }

        // Get all the feed ID's to Names
        final Map<Long, String> feedNamesById = getNamesById(connection, "FD");

        // Get all the stream type ID's to names
        final Map<Long, String> streamTypeNamesById = getNamesById(connection, "STRM_TP");

        // Get all the stream type ID's to names
        final Map<Long, String> pipeNamesById = getNamesById(connection, "PIPE");

        // Get all the feed to folder names
        final Map<Long, Long> folderByFeedId = getFkById(connection, "FD", "FK_FOLDER_ID");

        // Get all the folder parenting information
        final Map<Long, IdTreeNode> folders = getIdentifiedTree(connection, "FOLDER", "FK_FOLDER_ID");

        // Convert the existing criteria to query data/expression based
        final Map<Long, String> queryDataStrById = new HashMap<>();

        // Keep track of the dictionaries created for folder ID sets
        final ConcurrentHashMap<Long, Optional<DocRef>> dictionariesByFolder = new ConcurrentHashMap<>();

        for (final Map.Entry<Long, OldFindStreamCriteria> criteriaEntry : findStreamCriteriaById.entrySet()) {
            try {
                final QueryData queryData = this.convertFindStreamCriteria(connection,
                        criteriaEntry.getValue(),
                        feedNamesById,
                        streamTypeNamesById,
                        pipeNamesById,
                        folderByFeedId,
                        folders,
                        dictionariesByFolder);
                final String queryDataStr = this.marshalQueryData(queryData);
                queryDataStrById.put(criteriaEntry.getKey(), queryDataStr);

            } catch (final Exception e) {
                LOGGER.error("Could not convert stream criteria {}, {}",
                        criteriaEntry.getKey(),
                        e.getLocalizedMessage());
                throw e;
            }
        }

        // Write out the updated data
        if (writeUpdates) {
            for (Map.Entry<Long, String> entry : queryDataStrById.entrySet()) {
                final byte[] queryDataBytes = entry.getValue().getBytes();

                final Blob blob = connection.createBlob();
                blob.setBytes(1, queryDataBytes);

                try (final PreparedStatement stmt = connection.prepareStatement("UPDATE STRM_PROC_FILT SET DAT=? WHERE id=?")) {
                    stmt.setBlob(1, blob);
                    stmt.setLong(2, entry.getKey());
                    int rowsAffected = stmt.executeUpdate();
                    if (rowsAffected != 1) {
                        LOGGER.error(String.format("Could not update filter %s with %s, wrong number rows affected ",
                                entry.getKey(),
                                new String(queryDataBytes)));
                        throw new Exception(String.format("Wrong number of rows affected by update %d", rowsAffected));
                    }
                } catch (final Exception e) {
                    LOGGER.error("Could not update filter: {}", e.getLocalizedMessage());
                    throw e;
                }
            }
        } else {
            // Just log the updates out
            LOGGER.info("Logging all updates instead of applying them");

            findStreamCriteriaStrById.forEach((id, criteriaStr) -> {
                final String queryDataStr = queryDataStrById.get(id);
                LOGGER.info("{}\n{}\n{}\n-----------+", id, criteriaStr, queryDataStr);
            });
        }
    }

    private QueryData convertFindStreamCriteria(final Connection connection,
                                                final OldFindStreamCriteria criteria,
                                                final Map<Long, String> feedNamesById,
                                                final Map<Long, String> streamTypeNamesById,
                                                final Map<Long, String> pipeNamesById,
                                                final Map<Long, Long> folderByFeedId,
                                                final Map<Long, IdTreeNode> folders,
                                                final ConcurrentHashMap<Long, Optional<DocRef>> dictionariesByFolder) {

        final ExpressionOperator.Builder rootAnd = new ExpressionOperator.Builder(ExpressionOperator.Op.AND);

        // Feed Folders
        final List<Optional<DocRef>> feedDictionariesToInclude = new ArrayList<>();

        for (final Long folderId : criteria.obtainFolderIdSet()) {
            final IdTreeNode folderNode = folders.get(folderId);
            final List<Long> folderIds = new ArrayList<>();
            folderNode.recurse(folderIds::add, criteria.obtainFolderIdSet().isDeep());

            final Set<String> feedNames = folderByFeedId.entrySet().stream()
                    .filter(e -> folderIds.contains(e.getValue()))
                    .map(Map.Entry::getKey)
                    .map(feedNamesById::get)
                    .collect(Collectors.toSet());

            final Optional<DocRef> feedIdDict = dictionariesByFolder.computeIfAbsent(folderId,
                    fid -> createDictionary(connection, fid, criteria.obtainFolderIdSet().isDeep(), feedNames)
            );
            feedDictionariesToInclude.add(feedIdDict);
        }

        // Include Feeds
        // Individual ID's AND folders specified, wrap them in an OR
        final Set<Long> includeFeedIds = criteria.obtainFeeds().obtainInclude().getSet();
        if ((includeFeedIds.size() > 0) && (feedDictionariesToInclude.size() > 0)) {
            final ExpressionOperator.Builder or = new ExpressionOperator.Builder(ExpressionOperator.Op.OR);
            applyIncludesTerm(or, includeFeedIds, feedNamesById::get, StreamDataSource.FEED);
            feedDictionariesToInclude.stream()
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(dict ->
                            or.addOperator(new ExpressionTerm.Builder()
                                    .field(StreamDataSource.FEED)
                                    .condition(ExpressionTerm.Condition.IN_DICTIONARY)
                                    .dictionary(dict)
                                    .build()
                            )
                    );
            rootAnd.addOperator(or.build());
        } else if (includeFeedIds.size() > 0) {
            applyIncludesTerm(rootAnd, includeFeedIds, feedNamesById::get, StreamDataSource.FEED);
        } else if (feedDictionariesToInclude.size() > 0) {
            final ExpressionOperator.Builder or = new ExpressionOperator.Builder(ExpressionOperator.Op.OR);
            feedDictionariesToInclude.stream()
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(dict ->
                            or.addOperator(new ExpressionTerm.Builder()
                            .field(StreamDataSource.FEED)
                            .condition(ExpressionTerm.Condition.IN_DICTIONARY)
                            .dictionary(dict)
                            .build()
                            )
                    );
            rootAnd.addOperator(or.build());
        }

        // Exclude Feeds
        final Set<Long> excludeFeedIds = criteria.obtainFeeds().obtainExclude().getSet();
        if (excludeFeedIds.size() > 0) {
            final ExpressionOperator.Builder not = new ExpressionOperator.Builder(ExpressionOperator.Op.NOT);
            applyIncludesTerm(not, excludeFeedIds, feedNamesById::get, StreamDataSource.FEED);
            rootAnd.addOperator(not.build());
        }

        // Stream Types
        final Set<Long> streamTypeIds = criteria.obtainStreamTypeIdSet().getSet();
        applyIncludesTerm(rootAnd, streamTypeIds, streamTypeNamesById::get, StreamDataSource.STREAM_TYPE);

        // Pipeline
        final Set<Long> pipelineIds = criteria.obtainPipelineIdSet().getSet();
        applyIncludesTerm(rootAnd, pipelineIds, pipeNamesById::get, StreamDataSource.PIPELINE);

        // Parent Stream ID
        final Set<Long> parentStreamIds = criteria.obtainParentStreamIdSet().getSet();
        applyIncludesTerm(rootAnd, parentStreamIds, Object::toString, StreamDataSource.PARENT_STREAM_ID);

        // Stream ID, two clauses feed into this, absolute stream ID values and ranges
        final Set<Long> streamIds = criteria.obtainStreamIdSet().getSet();
        final IdRange streamIdRange = criteria.obtainStreamIdRange();
        if ((streamIds.size() > 0) || streamIdRange.isConstrained()) {
            final ExpressionOperator.Builder or = new ExpressionOperator.Builder(ExpressionOperator.Op.OR);

            applyIncludesTerm(or, streamIds, Object::toString, StreamDataSource.STREAM_ID);
            applyBoundedTerm(or, streamIdRange, StreamDataSource.STREAM_ID, Object::toString);

            rootAnd.addOperator(or.build());
        }

        // Dynamic Stream Attributes
        criteria.obtainAttributeConditionList().forEach(c -> {
            rootAnd.addTerm(c.getStreamAttributeKey().getName(), c.getCondition(), c.getFieldValue());
        });

        // Created Period
        applyBoundedTerm(rootAnd, criteria.obtainCreatePeriod(), StreamDataSource.CREATE_TIME, DateUtil::createNormalDateTimeString);

        // Effective Period
        applyBoundedTerm(rootAnd, criteria.obtainEffectivePeriod(), StreamDataSource.EFFECTIVE_TIME, DateUtil::createNormalDateTimeString);

        // Status Time Period
        applyBoundedTerm(rootAnd, criteria.obtainStatusPeriod(), StreamDataSource.STATUS_TIME, DateUtil::createNormalDateTimeString);

        // Build and return
        return new QueryData.Builder()
                .dataSource(StreamDataSource.STREAM_STORE_DOC_REF)
                .expression(rootAnd.build())
                .build();
    }

    private Optional<DocRef> createDictionary(final Connection connection,
                                              final Long folderId,
                                              final boolean isDeep,
                                              final Set<String> feedNames) {
        final DocRef dict = new DocRef.Builder()
                .uuid(UUID.randomUUID().toString())
                .name(String.format("_feeds_folder_%d_%s", folderId, isDeep ? "deep" : "shallow"))
                .type(DictionaryDoc.ENTITY_TYPE)
                .build();

        final long now = System.currentTimeMillis();

        final String dictDataStr = feedNames.stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));

        try {
            final Blob dictDataBlob = connection.createBlob();
            dictDataBlob.setBytes(1, dictDataStr.getBytes());

            if (writeUpdates) {
                final String sql = "INSERT INTO DICT (VER, CRT_MS, CRT_USER, UPD_MS, UPD_USER, NAME, UUID, DAT, FK_FOLDER_ID) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setInt(1, 0);
                    stmt.setLong(2, now);
                    stmt.setString(3, UPGRADE_USER);
                    stmt.setLong(4, now);
                    stmt.setString(5, UPGRADE_USER);
                    stmt.setString(6, dict.getName());
                    stmt.setString(7, dict.getUuid());
                    stmt.setBlob(8, dictDataBlob);
                    stmt.setLong(9, folderId);

                    int rowsAffected = stmt.executeUpdate();
                    if (rowsAffected != 1) {
                        throw new Exception(String.format("Wrong number of rows affected by update %d", rowsAffected));
                    }
                }
            } else {
                LOGGER.info("Creating Dictionary: {}, {}, {}", folderId, dict, dictDataStr);
            }


            return Optional.of(dict);

        } catch (final Exception e) {
            LOGGER.error("Could not create dictionary for folder Id {}", folderId);
            return Optional.empty();
        }
    }

    private <T extends Number> void applyBoundedTerm(final ExpressionOperator.Builder parentTerm,
                                                     final Range<T> range,
                                                     final String fieldName,
                                                     final Function<T, String> toString) {
        if (range.isBounded()) {
            final String boundTerm = String.format("%s,%s", range.getFrom(), range.getTo());
            parentTerm.addTerm(fieldName, ExpressionTerm.Condition.BETWEEN, boundTerm);
        } else if (null != range.getFrom()) {
            parentTerm.addTerm(fieldName, ExpressionTerm.Condition.GREATER_THAN_OR_EQUAL_TO, toString.apply(range.getFrom()));
        } else if (null != range.getTo()) {
            parentTerm.addTerm(fieldName, ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO, toString.apply(range.getTo()));
        }
    }

    private static class IdTreeNode {
        private final long id;

        private final List<IdTreeNode> children = new ArrayList<>();

        IdTreeNode(final long id) {
            this.id = id;
        }

        void addChild(final IdTreeNode child) {
            this.children.add(child);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("IdTreeNode{");
            sb.append("id=").append(id);
            sb.append(", children=").append(children.size()).append(": ");

            children.forEach(child -> sb.append(child.id).append(", "));
            sb.append('}');
            return sb.toString();
        }

        void recurse(final Consumer<Long> consumer,
                            final boolean deep) {
            consumer.accept(this.id);
            if (deep) {
                this.children.forEach(c -> c.recurse(consumer, deep));
            }
        }
    }

    // This function takes a table name and picks all the values of ID, NAME and puts them in a map
    private Map<Long, String> getNamesById(final Connection connection, final String tableName) throws Exception {
        final String sql = String.format("SELECT ID, NAME FROM %s", tableName);
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            try (final ResultSet rs = stmt.executeQuery()) {
                final Map<Long, String> namesById = new HashMap<>();

                while (rs.next()) {
                    final long id = rs.getLong(1);
                    final String name = rs.getString(2);
                    namesById.put(id, name);
                }

                return namesById;
            }
        }
    }

    private Map<Long, Long> getFkById(final Connection connection,
                                      final String tableName,
                                      final String fkFieldName) throws SQLException {
        final Map<Long, Long> results = new HashMap<>();

        final String sql = String.format("SELECT ID, %s FROM %s", fkFieldName, tableName);

        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {

            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.put(rs.getLong(1), rs.getLong(2));
                }
            }
        }

        return results;
    }

    private Map<Long, IdTreeNode> getIdentifiedTree(final Connection connection,
                                                    final String tableName,
                                                    final String parentIdFieldName) throws SQLException {
        final Map<Long, Long> parentsById = getFkById(connection, tableName, parentIdFieldName);

        // First create all the objects
        final Map<Long, IdTreeNode> objectsById = parentsById.keySet().stream()
                .collect(Collectors.toMap(o -> o, IdTreeNode::new));

        // Then hook up all the children nodes
        for (final Map.Entry<Long, IdTreeNode> e : objectsById.entrySet()) {
            final Long parentId = parentsById.get(e.getKey());
            if (parentId > 0) {
                final IdTreeNode parentObj = objectsById.get(parentId);
                parentObj.addChild(e.getValue());
            }
        }

        return objectsById;
    }

    private <T> void applyIncludesTerm(final ExpressionOperator.Builder parentTerm,
                                      final Set<T> rawTerms,
                                      final Function<T, String> toString,
                                      final String fieldName) {
        if (rawTerms.size() > 1) {
            final String values = rawTerms.stream().map(toString).collect(Collectors.joining(IN_CONDITION_DELIMITER));
            parentTerm.addTerm(fieldName, ExpressionTerm.Condition.IN, values);
        } else if (rawTerms.size() == 1) {
            final String value = toString.apply(rawTerms.iterator().next());
            parentTerm.addTerm(fieldName, ExpressionTerm.Condition.EQUALS, value);
        }
    }

    private OldFindStreamCriteria unmarshalCriteria(final String input) throws JAXBException {
        if (null != jaxbException) {
            throw jaxbException;
        }
        return XMLMarshallerUtil.unmarshal(findStreamCriteriaJaxb, OldFindStreamCriteria.class, input);
    }

    private String marshalQueryData(final QueryData queryData) throws JAXBException {
        if (null != jaxbException) {
            throw jaxbException;
        }

        return XMLMarshallerUtil.marshal(queryDataJaxb, queryData);
    }
}
