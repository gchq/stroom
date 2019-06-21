package stroom.db.migration.mysql;

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.server.util.XMLMarshallerUtil;
import stroom.entity.shared.IdRange;
import stroom.entity.shared.Range;
import stroom.entity.shared.SQLNameConstants;
import stroom.explorer.shared.ExplorerConstants;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static stroom.query.api.v2.ExpressionTerm.Condition.IN_CONDITION_DELIMITER;

public class V6_0_0_9__ProcessingFilter implements JdbcMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V6_0_0_9__ProcessingFilter.class);

    private JAXBContext findStreamCriteriaJaxb;
    private JAXBContext queryDataJaxb;
    private JAXBException jaxbException;
    private final boolean writeUpdates;

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
                        String dat = rs.getString(2);

                        if (dat != null) {
                            dat = dat.replaceAll("<idSet>", "<id>");
                            dat = dat.replaceAll("</idSet>", "</id>");
                        }

                        final OldFindStreamCriteria streamCriteria = unmarshalCriteria(dat);

                        findStreamCriteriaStrById.put(id, dat);
                        findStreamCriteriaById.put(id, streamCriteria);
                    } catch (final Exception e) {
                        LOGGER.error("Could not get old stream criteria {}", e.getLocalizedMessage());
                        throw e;
                    }
                }
            }
        }

        // Get all the folder ID's to DocRefs
        final Map<Long, DocRef> folderDocRefsById = getDocRefsById(connection, ExplorerConstants.FOLDER, SQLNameConstants.FOLDER);

        // Get all the feed ID's to Names
        final Map<Long, String> feedNamesById = getStringFieldById(connection, "FD", "NAME");

        // Get all the stream type ID's to names
        final Map<Long, String> streamTypeNamesById = getStringFieldById(connection, "STRM_TP", "NAME");

        // Get all the stream type ID's to names
        final Map<Long, DocRef> pipeDocRefsById = getDocRefsById(connection, PipelineEntity.ENTITY_TYPE, "PIPE");

        // Convert the existing criteria to query data/expression based
        final Map<Long, String> queryDataStrById = new HashMap<>();

        for (final Map.Entry<Long, OldFindStreamCriteria> criteriaEntry : findStreamCriteriaById.entrySet()) {
            try {
                final QueryData queryData = this.convertFindStreamCriteria(
                        criteriaEntry.getValue(),
                        l -> Optional.ofNullable(feedNamesById.get(l)),
                        l -> Optional.ofNullable(streamTypeNamesById.get(l)),
                        l -> Optional.ofNullable(pipeDocRefsById.get(l)),
                        l -> Optional.ofNullable(folderDocRefsById.get(l)));
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

    private QueryData convertFindStreamCriteria(final OldFindStreamCriteria criteria,
                                                final Function<Long, Optional<String>> feedNamesById,
                                                final Function<Long, Optional<String>> streamTypeNamesById,
                                                final Function<Long, Optional<DocRef>> pipeDocRefsById,
                                                final Function<Long, Optional<DocRef>> folderDocRefsById) {

        final ExpressionOperator.Builder rootAnd = new ExpressionOperator.Builder(ExpressionOperator.Op.AND);

        // Include folders
        final Set<Long> folderIdSet = criteria.obtainFolderIdSet().getSet();
        if (folderIdSet.size() > 0) {
            ExpressionOperator.Builder parent = rootAnd;

            if (folderIdSet.size() > 1) {
                parent = new ExpressionOperator.Builder(ExpressionOperator.Op.OR);
            }

            for (final Long folderId : folderIdSet) {
                final DocRef folderDocRef = folderDocRefsById.apply(folderId).orElseGet(() -> {
                    LOGGER.warn("Could not find folder with id {}", folderId);
                    return new DocRef(ExplorerConstants.FOLDER, "--missing folder (" + folderId + ")");
                });
                parent.addDocRefTerm(StreamDataSource.FEED_NAME, Condition.IN_FOLDER, folderDocRef);
            }

            if (folderIdSet.size() > 1) {
                rootAnd.addOperator(parent.build());
            }
        }

        // Include Feeds
        final Set<Long> includeFeedIds = criteria.obtainFeeds().obtainInclude().getSet();
        if (includeFeedIds.size() > 0) {
            applyIncludesTerm(rootAnd, includeFeedIds, feedNamesById, StreamDataSource.FEED_NAME);
        }

        // Exclude Feeds
        final Set<Long> excludeFeedIds = criteria.obtainFeeds().obtainExclude().getSet();
        if (excludeFeedIds.size() > 0) {
            final ExpressionOperator.Builder not = new ExpressionOperator.Builder(ExpressionOperator.Op.NOT);
            applyIncludesTerm(not, excludeFeedIds, feedNamesById, StreamDataSource.FEED_NAME);
            rootAnd.addOperator(not.build());
        }

        // Stream Types
        final Set<Long> streamTypeIds = criteria.obtainStreamTypeIdSet().getSet();
        applyIncludesTerm(rootAnd, streamTypeIds, streamTypeNamesById, StreamDataSource.STREAM_TYPE_NAME);

        // Pipeline
        final Set<Long> pipelineIds = criteria.obtainPipelineIdSet().getSet();
        applyIncludesDocRefTerm(rootAnd, pipelineIds, pipeDocRefsById, StreamDataSource.PIPELINE_UUID);

        // Parent Stream ID
        final Set<Long> parentStreamIds = criteria.obtainParentStreamIdSet().getSet();
        applyIncludesTerm(rootAnd, parentStreamIds, i -> Optional.of(i.toString()), StreamDataSource.PARENT_STREAM_ID);

        // Stream ID, two clauses feed into this, absolute stream ID values and ranges
        final Set<Long> streamIds = criteria.obtainStreamIdSet().getSet();
        final IdRange streamIdRange = criteria.obtainStreamIdRange();
        if ((streamIds.size() > 0) || streamIdRange.isConstrained()) {
            final ExpressionOperator.Builder or = new ExpressionOperator.Builder(ExpressionOperator.Op.OR);

            applyIncludesTerm(or, streamIds, i -> Optional.of(i.toString()), StreamDataSource.STREAM_ID);
            applyBoundedTerm(or, streamIdRange, StreamDataSource.STREAM_ID, Object::toString);

            rootAnd.addOperator(or.build());
        }

        // Dynamic Stream Attributes
        criteria.obtainAttributeConditionList().forEach(c ->
                rootAnd.addTerm(c.getStreamAttributeKey().getName(), c.getCondition(), c.getFieldValue()));

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

    private <T extends Number> void applyBoundedTerm(final ExpressionOperator.Builder parentTerm,
                                                     final Range<T> range,
                                                     final String fieldName,
                                                     final Function<T, String> toString) {
        if (range.isBounded()) {
            final String boundTerm = String.format("%s,%s", toString.apply(range.getFrom()), toString.apply(range.getTo()));
            parentTerm.addTerm(fieldName, ExpressionTerm.Condition.BETWEEN, boundTerm);
        } else if (null != range.getFrom()) {
            parentTerm.addTerm(fieldName, ExpressionTerm.Condition.GREATER_THAN_OR_EQUAL_TO, toString.apply(range.getFrom()));
        } else if (null != range.getTo()) {
            parentTerm.addTerm(fieldName, ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO, toString.apply(range.getTo()));
        }
    }

    private Map<Long, DocRef> getDocRefsById(final Connection connection,
                                             final String docRefType,
                                             final String tableName) throws Exception {
        final String sql = String.format("SELECT ID, UUID, NAME FROM %s", tableName);
        try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
            try (final ResultSet rs = stmt.executeQuery()) {
                final Map<Long, DocRef> docRefsById = new HashMap<>();

                while (rs.next()) {
                    final long id = rs.getLong(1);
                    final String uuid = rs.getString(2);
                    final String name = rs.getString(3);
                    docRefsById.put(id, new DocRef.Builder()
                            .uuid(uuid)
                            .type(docRefType)
                            .name(name)
                            .build());
                }

                return docRefsById;
            }
        }
    }

    // This function takes a table name and picks all the values of ID, NAME and puts them in a map
    private Map<Long, String> getStringFieldById(final Connection connection,
                                                 final String tableName,
                                                 final String fieldName) throws Exception {
        final String sql = String.format("SELECT ID, %s FROM %s", fieldName, tableName);
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

    private <T> void applyIncludesDocRefTerm(final ExpressionOperator.Builder parentTerm,
                                             final Set<T> rawTerms,
                                             final Function<T, Optional<DocRef>> toDocRef,
                                             final String fieldName) {
        if (rawTerms.size() > 1) {
            final ExpressionOperator.Builder opOp = new ExpressionOperator.Builder().op(ExpressionOperator.Op.OR);
            rawTerms.stream()
                    .map(l -> {
                        final Optional<DocRef> value = toDocRef.apply(l);
                        if (!value.isPresent()) {
                            LOGGER.warn("Could not find value for {} in field {}", l, fieldName);
                        }
                        return value.orElseGet(() -> {
                            final String name = "--missing " + fieldName + " (" + l + ")";
                            return new DocRef(fieldName, name, name);
                        });
                    })
                    .forEach(d -> opOp.addDocRefTerm(fieldName, ExpressionTerm.Condition.IS_DOC_REF, d));
            parentTerm.addOperator(opOp.build());
        } else if (rawTerms.size() == 1) {
            toDocRef.apply(rawTerms.iterator().next()).ifPresent(value ->
                    parentTerm.addDocRefTerm(fieldName, ExpressionTerm.Condition.IS_DOC_REF, value)
            );
        }
    }

    private <T> void applyIncludesTerm(final ExpressionOperator.Builder parentTerm,
                                       final Set<T> rawTerms,
                                       final Function<T, Optional<String>> toString,
                                       final String fieldName) {
        if (rawTerms.size() > 1) {
            final String values = rawTerms.stream()
                    .map(l -> {
                        final Optional<String> value = toString.apply(l);
                        if (!value.isPresent()) {
                            LOGGER.warn("Could not find value for {} in field {}", l, fieldName);
                        }
                        return value.orElseGet(() -> "--missing " + fieldName + " (" + l + ")");
                    })
                    .collect(Collectors.joining(IN_CONDITION_DELIMITER));
            parentTerm.addTerm(fieldName, ExpressionTerm.Condition.IN, values);
        } else if (rawTerms.size() == 1) {
            toString.apply(rawTerms.iterator().next()).ifPresent(value ->
                    parentTerm.addTerm(fieldName, ExpressionTerm.Condition.EQUALS, value)
            );
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