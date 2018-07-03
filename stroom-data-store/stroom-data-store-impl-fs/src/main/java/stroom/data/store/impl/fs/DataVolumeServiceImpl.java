package stroom.data.store.impl.fs;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.PageRequest;
import stroom.node.shared.VolumeEntity;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.util.concurrent.AtomicSequence;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static stroom.data.store.impl.fs.db.stroom.tables.DataVolume.DATA_VOLUME;
import static stroom.data.store.impl.fs.db.stroom.tables.Vol.VOL;

public class DataVolumeServiceImpl implements DataVolumeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataVolumeServiceImpl.class);

    private final ConnectionProvider connectionProvider;
    private final Security security;

    /**
     * Create a positive sequence of numbers
     */
    private AtomicSequence sequence = new AtomicSequence();

    @Inject
    DataVolumeServiceImpl(final ConnectionProvider connectionProvider,
                          final Security security) {
        this.connectionProvider = connectionProvider;
        this.security = security;
    }

//    @SuppressWarnings("unchecked")
//    @Override
//    // @Transactional
//    public BaseResultList<StreamVolume> find(final FindStreamVolumeCriteria criteria) {
//        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> {
//            if (!criteria.isValidCriteria()) {
//                throw new IllegalArgumentException("Not enough criteria to run");
//            }
//
//            final HqlBuilder sql = new HqlBuilder();
//            sql.append("SELECT sv FROM ");
//            sql.append(StreamVolume.class.getName());
//            sql.append(" sv");
//            sql.append(" WHERE 1=1");
//
//            sql.appendEntityIdSetQuery("sv.volume.node", criteria.getNodeIdSet());
//            sql.appendEntityIdSetQuery("sv.volume", criteria.getVolumeIdSet());
//            sql.appendCriteriaSet("sv.stream.id", criteria.getStreamIdSet());
//            sql.appendPrimitiveValueSetQuery("sv.stream.pstatus", StreamStatusId.convertStatusSet(criteria.getStreamStatusSet()));
//
//            if (criteria.getStreamRange() != null && criteria.getStreamRange().getStreamTypePath() != null) {
//                sql.append(" AND sv.stream.streamType.path = ");
//                sql.arg(criteria.getStreamRange().getStreamTypePath());
//            }
//            if (criteria.getStreamRange() != null && criteria.getStreamRange().isFileLocation()) {
//                sql.appendRangeQuery("sv.stream.id", criteria.getStreamRange());
//                sql.appendRangeQuery("sv.stream.createMs", criteria.getStreamRange().getCreatePeriod());
//            }
//            // Create the query
//            final List<StreamVolume> results = entityManager.executeQueryResultList(sql, criteria);
//
//            return BaseResultList.createCriterialBasedList(results, criteria);
//        });
//    }

    private Optional<Condition> criteriaSetToCondition(final TableField<?, ?> field, final CriteriaSet<Long> criteriaSet) {
        if (criteriaSet == null || Boolean.TRUE.equals(criteriaSet.getMatchAll())) {
            return Optional.empty();
        }

        if (criteriaSet.getMatchNull() != null && criteriaSet.getMatchNull()) {
            return Optional.of(field.isNull());
        }

        return Optional.of(field.in(criteriaSet.getSet()));
    }

    private int getOffset(final PageRequest pageRequest) {
        if (pageRequest == null || pageRequest.getOffset() == null) {
            return 0;
        }
        return pageRequest.getOffset().intValue();
    }

    private int getNumberOfRows(final PageRequest pageRequest) {
        if (pageRequest == null || pageRequest.getOffset() == null) {
            return 1000000;
        }
        return pageRequest.getLength();
    }

    @SuppressWarnings("unchecked")
    @Override
    // @Transactional
    public BaseResultList<DataVolume> find(final FindDataVolumeCriteria criteria) {
        final Optional<Condition> volumeIdCondition = criteriaSetToCondition(DATA_VOLUME.VOLUME_ID, criteria.getVolumeIdSet());
        final Optional<Condition> streamIdCondition = criteriaSetToCondition(DATA_VOLUME.DATA_ID, criteria.getStreamIdSet());

        final List<Condition> conditions = new ArrayList<>();
        volumeIdCondition.ifPresent(conditions::add);
        streamIdCondition.ifPresent(conditions::add);

        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> {
            if (!criteria.isValidCriteria()) {
                throw new IllegalArgumentException("Not enough criteria to run");
            }

            try (final Connection connection = connectionProvider.getConnection()) {
                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
                final List<DataVolume> list = create.select(DATA_VOLUME.DATA_ID, VOL.PATH)
                        .from(DATA_VOLUME)
                        .join(VOL).on(VOL.ID.eq(DATA_VOLUME.VOLUME_ID))
                        .where(conditions)
                        .limit(getOffset(criteria.getPageRequest()), getNumberOfRows(criteria.getPageRequest()))
                        .fetch()
                        .map(r -> new DataVolumeImpl(r.value1(), r.value2()));
                return BaseResultList.createCriterialBasedList(list, criteria);
            } catch (final SQLException e) {
                LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }


////            final StreamRange streamRange = criteria.getStreamRange();
////            final boolean joinStreamType = streamRange != null && streamRange.getTypeName() != null;
////            final boolean joinStream = (streamRange != null && streamRange.getCreatePeriod() != null) || (criteria.getStreamStatusSet() != null && criteria.getStreamStatusSet().isConstrained());
//
//            final SqlBuilder sql = new SqlBuilder();
//            sql.append("SELECT");
//            sql.append(" sv.FK_STRM_ID, v.PATH, v.VOL_TP, n.ID, n.FK_RK_ID");
//            sql.append(" FROM ");
//            sql.append(TABLE_NAME);
//            sql.append(" sv");
//            sql.append(" JOIN VOL v ON (v.ID = sv.FK_VOL_ID)");
//            sql.append(" JOIN ND n ON (n.ID = v.FK_ND_ID)");
////            if (joinStream) {
////                sql.append(" JOIN STRM s ON (s.ID = sv.FK_STRM_ID)");
////            }
////            if (joinStreamType) {
////                sql.append(" JOIN STRM_TP st ON (st.ID = s.FK_STRM_TP_ID)");
////            }
//            sql.append(" WHERE 1=1");
//
//            sql.appendCriteriaSetQuery("v.FK_ND_ID", criteria.getNodeIdSet());
//            sql.appendCriteriaSetQuery("sv.FK_VOL_ID", criteria.getVolumeIdSet());
//            sql.appendCriteriaSetQuery("sv.FK_STRM_ID", criteria.getStreamIdSet());
////            sql.appendPrimitiveValueSetQuery("s.STAT", StreamStatusId.convertStatusSet(criteria.getStreamStatusSet()));
////
////            if (streamRange != null) {
////                if (joinStreamType) {
////                    sql.append(" AND st.NAME = ");
////                    sql.arg(streamRange.getTypeName());
////                }
////                if (streamRange.isFileLocation()) {
////                    sql.appendRangeQuery("sv.FK_STRM_ID", streamRange);
////                }
////                if (joinStream) {
////                    sql.appendRangeQuery("s.CRT_MS", streamRange.getCreatePeriod());
////                }
////            }
//
//            sql.applyRestrictionCriteria(criteria);
//
//            final List<DataVolume> results = new ArrayList<>();
//            queryStreamVolumes(sql, list);
//
//            // Create the query
//            return BaseResultList.createCriterialBasedList(list, criteria);
        });
    }

//    @SuppressWarnings("unchecked")
//    // @Transactional
//    public BaseResultList<StreamAndVolumes> findStreamAndVolumes(final FindStreamVolumeCriteria criteria) {
//        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> {
//            if (!criteria.isValidCriteria()) {
//                throw new IllegalArgumentException("Not enough criteria to run");
//            }
//
//            final StreamRange streamRange = criteria.getStreamRange();
//            final boolean joinStreamType = streamRange != null && streamRange.getTypeName() != null;
//            final boolean joinStream = streamRange != null && streamRange.getCreatePeriod() != null;
//
//            final SqlBuilder sql = new SqlBuilder();
//            sql.append("SELECT");
//
//
//
//            sql.append(" s.ID,");
//            sql.append(" f.NAME,");
//            sql.append(" s.FK_FD_ID,");
//            sql.append(" st.NAME,");
////            sql.append(" p.NAME,");
////            sql.append(" p.UUID,");
//            sql.append(" s.PARNT_STRM_ID,");
//            sql.append(" s.STRM_TASK_ID,");
//            sql.append(" s.FK_STRM_PROC_ID,");
//            sql.append(" s.STAT,");
//            sql.append(" s.STAT_MS,");
//            sql.append(" s.CRT_MS,");
//            sql.append(" s.EFFECT_MS,");
//
//
//
//
//
//            sql.append(" sv.ID,");
//            sql.append(" sv.VER,");
//            sql.append(" sv.FK_STRM_ID,");
//            sql.append(" sv.FK_VOL_ID,");
//            sql.append(" v.PATH,");
//            sql.append(" v.VOL_TP,");
//            sql.append(" n.ID,");
//            sql.append(" n.FK_RK_ID");
//            sql.append(" FROM ");
//            sql.append("STRM_VOL");
//            sql.append(" sv");
////            sql.append(" JOIN STRM s ON (s.ID = sv.FK_STRM_ID)");
//            sql.append(" JOIN FD f ON (f.ID = s.FK_FD_ID)");
//            sql.append(" JOIN STRM_TP st ON (st.ID = s.FK_STRM_TP_ID)");
////            sql.append(" LEFT OUTER JOIN STRM_TASK st ON (st.ID = s.FK_STRM_TP_ID)");
//            sql.append(" JOIN VOL v ON (v.ID = sv.FK_VOL_ID)");
//            sql.append(" JOIN ND n ON (n.ID = v.FK_ND_ID)");
////            if (joinStream) {
//                sql.append(" JOIN STRM s ON (s.ID = sv.FK_STRM_ID)");
////            }
////            if (joinStreamType) {
//                sql.append(" JOIN STRM_TP st ON (st.ID = s.FK_STRM_TP_ID)");
////            }
//            sql.append(" WHERE 1=1");
//
//            sql.appendEntityIdSetQuery("v.FK_ND_ID", criteria.getNodeIdSet());
//            sql.appendEntityIdSetQuery("sv.FK_VOL_ID", criteria.getVolumeIdSet());
//            sql.appendCriteriaSet("sv.FK_STRM_ID", criteria.getStreamIdSet());
//            sql.appendPrimitiveValueSetQuery("s.STAT", StreamStatusId.convertStatusSet(criteria.getStreamStatusSet()));
//
//            if (streamRange != null) {
//                if (joinStreamType) {
//                    sql.append(" AND st.NAME = ");
//                    sql.arg(streamRange.getTypeName());
//                }
//                if (streamRange.isFileLocation()) {
//                    sql.appendRangeQuery("sv.FK_STRM_ID", streamRange);
//                }
//                if (joinStream) {
//                    sql.appendRangeQuery("s.CRT_MS", streamRange.getCreatePeriod());
//                }
//            }
//
//            sql.applyRestrictionCriteria(criteria);
//
//            final List<StreamAndVolumes> results = new ArrayList<>();
//
//            try (final Connection connection = dataSource.getConnection()) {
//                try (final PreparedStatement preparedStatement = connection.prepareStatement(sql.toString())) {
//                    PreparedStatementUtil.setArguments(preparedStatement, sql.getArgs());
//                    try (final ResultSet rs = preparedStatement.executeQuery()) {
//                        while (rs.next()) {
//                            final StreamVolume svol = new StreamVolumeImpl(
//                                    rs.getLong(1),
//                                    rs.getInt(2),
//                                    rs.getLong(3),
//                                    rs.getInt(4),
//                                    rs.getString(5),
//                                    VolumeType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(rs.getByte(6)),
//                                    rs.getInt(7),
//                                    rs.getInt(8)
//                            );
//                            results.add(svol);
//                        }
//                    }
//                }
//            } catch (final SQLException e) {
//                throw new RuntimeException(e.getMessage(), e);
//            }
//
//
//
//            query(sql, results);
//
//            // Create the query
//            return BaseResultList.createCriterialBasedList(results, criteria);
//        });
//    }
//
//    @Override
//    public FindDataVolumeCriteria createCriteria() {
//        return new FindDataVolumeCriteria();
//    }

    /**
     * Return the meta data volumes for a stream id.
     */
    @Override
    public Set<DataVolume> findStreamVolume(final long dataId) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create.select(DATA_VOLUME.DATA_ID, VOL.PATH)
                    .from(DATA_VOLUME)
                    .join(VOL).on(VOL.ID.eq(DATA_VOLUME.VOLUME_ID))
                    .where(DATA_VOLUME.DATA_ID.eq(dataId))
                    .fetch()
                    .stream()
                    .map(r -> new DataVolumeImpl(r.value1(), r.value2()))
                    .collect(Collectors.toSet());
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT");
//        sql.append(" sv.FK_STRM_ID, v.PATH, v.VOL_TP, n.ID, n.FK_RK_ID");
//        sql.append(" FROM ");
//        sql.append(TABLE_NAME);
//        sql.append(" sv");
//        sql.append(" JOIN VOL v ON (v.ID = sv.FK_VOL_ID)");
//        sql.append(" JOIN ND n ON (n.ID = v.FK_ND_ID)");
//        sql.append(" WHERE FK_STRM_ID = ");
//        sql.arg(streamId);
//
//        final Set<DataVolume> results = new HashSet<>();
//        queryStreamVolumes(sql, results);
//        return results;
    }

    //
//    private void queryStreamVolumes(final SqlBuilder sql, final Collection<DataVolume> results) {
//        try (final Connection connection = dataSource.getConnection()) {
//            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql.toString())) {
//                PreparedStatementUtil.setArguments(preparedStatement, sql.getArgs());
//                try (final ResultSet rs = preparedStatement.executeQuery()) {
//                    while (rs.next()) {
//                        final DataVolume svol = new DataVolumeImpl(
//                                rs.getLong(1),
//                                rs.getString(2),
//                                VolumeType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(rs.getByte(3)),
//                                rs.getInt(4),
//                                rs.getInt(5)
//                        );
//                        results.add(svol);
//                    }
//                }
//            }
//        } catch (final SQLException e) {
//            throw new RuntimeException(e.getMessage(), e);
//        }
//    }
//
//    @Override
    public Set<DataVolume> createStreamVolumes(final long dataId, final Set<VolumeEntity> volumes) {
//        final List<StrmVolRecord> batch = new ArrayList<>();
//        for (final VolumeEntity volume : volumes) {
//            batch.add(new StrmVolRecord(null, null, dataId, (int) volume.getId()));
//        }

        final Set<DataVolume> set = new HashSet<>();

        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            for (final VolumeEntity volume : volumes) {
                create.insertInto(DATA_VOLUME, DATA_VOLUME.DATA_ID, DATA_VOLUME.VOLUME_ID)
                        .values(dataId, (int) volume.getId())
                        .execute();
                set.add(new DataVolumeImpl(dataId, volume.getPath()));
            }
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return set;


//        for (final VolumeEntity volume : volumes) {
//            final SqlBuilder sql = new SqlBuilder();
//            sql.append("INSERT INTO ");
//            sql.append(TABLE_NAME);
//            sql.append(" (VER, FK_STRM_ID, FK_VOL_ID) VALUES (");
//            sql.arg(1);
//            sql.append(", ");
//            sql.arg(dataId);
//            sql.append(", ");
//            sql.arg(volume.getId());
//            sql.append(")");
//
//            try (final Connection connection = dataSource.getConnection()) {
//                try (final PreparedStatement preparedStatement = connection.prepareStatement(sql.toString())) {
//                    PreparedStatementUtil.setArguments(preparedStatement, sql.getArgs());
//                    preparedStatement.executeUpdate();
//                }
//            } catch (final SQLException e) {
//                throw new RuntimeException(e.getMessage(), e);
//            }
//        }
//
//        return findStreamVolume(dataId);
    }

//    @Override
//    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindDataVolumeCriteria criteria) {
////        CriteriaLoggingUtil.appendRangeTerm(items, "streamRange", criteria.getStreamRange());
////        CriteriaLoggingUtil.appendCriteriaSet(items, "streamStatusSet", criteria.getStreamStatusSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "nodeIdSet", criteria.getNodeIdSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "volumeIdSet", criteria.getVolumeIdSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "streamIdSet", criteria.getStreamIdSet());
//        CriteriaLoggingUtil.appendPageRequest(items, criteria.getPageRequest());
//    }

    /**
     * Given a set of volumes pick one that's nearest to us and readable,
     * otherwise a random one.
     */
    public DataVolume pickBestVolume(final Set<DataVolume> streamVolumes, final long nodeId, final long rackId) {
//        // Try and locate a volume on the same node that is private
//        for (final DataVolume streamVolume : streamVolumes) {
//            if (streamVolume.getVolumeType().equals(VolumeType.PRIVATE)
//                    && streamVolume.getNodeId() == nodeId) {
//                return streamVolume;
//            }
//        }
//
//        // Otherwise have a go on one in the same rack that is public
//        for (final DataVolume streamVolume : streamVolumes) {
//            if (streamVolume.getVolumeType().equals(VolumeType.PUBLIC)
//                    && streamVolume.getRackId() == rackId) {
//                return streamVolume;
//            }
//        }
//
//        final Set<DataVolume> publicVolumes = streamVolumes
//                .stream()
//                .filter(streamVolume -> streamVolume.getVolumeType().equals(VolumeType.PUBLIC))
//                .collect(Collectors.toSet());
//
//        if (publicVolumes.size() == 0) {
//            return null;
//        }
//
//        // Otherwise pick a random one
//        final Iterator<DataVolume> iter = publicVolumes.iterator();
//        final int pickIndex = pickIndex(publicVolumes.size());
//        for (int i = 0; i < pickIndex; i++) {
//            iter.next();
//        }
//
//        return iter.next();
//
//
//
//


        if (streamVolumes.size() == 0) {
            return null;
        }

        // Otherwise pick a random one
        final Iterator<DataVolume> iter = streamVolumes.iterator();
        final int pickIndex = pickIndex(streamVolumes.size());
        for (int i = 0; i < pickIndex; i++) {
            iter.next();
        }

        return iter.next();
    }

    /**
     * Pick a number and try and round robin on the number that is chosen.
     */
    private int pickIndex(final int size) {
        return sequence.next(size);
    }

    class DataVolumeImpl implements DataVolume {
        private final long streamId;
        private final String volumePath;

        DataVolumeImpl(final long streamId,
                       final String volumePath) {
            this.streamId = streamId;
            this.volumePath = volumePath;
        }

        @Override
        public long getStreamId() {
            return streamId;
        }

        @Override
        public String getVolumePath() {
            return volumePath;
        }
    }
}
