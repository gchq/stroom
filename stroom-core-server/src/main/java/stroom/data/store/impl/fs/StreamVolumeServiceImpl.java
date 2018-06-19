package stroom.data.store.impl.fs;

import event.logging.BaseAdvancedQueryItem;
import stroom.entity.CriteriaLoggingUtil;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.SQLNameConstants;
import stroom.entity.util.PreparedStatementUtil;
import stroom.entity.util.SqlBuilder;
import stroom.node.shared.VolumeEntity;
import stroom.node.shared.VolumeEntity.VolumeType;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.data.store.FindStreamVolumeCriteria;
import stroom.util.concurrent.AtomicSequence;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StreamVolumeServiceImpl implements StreamVolumeService {
    // Shame HSQLDB does not like keys smaller than int.
    public static final String VERSION = "VER";
    public static final String ID = "ID";
    protected static final String FK_PREFIX = "FK_";
    protected static final String ID_SUFFIX = "_ID";
    protected static final String SEP = "_";
    public static final String TABLE_NAME = SQLNameConstants.STREAM + SEP + SQLNameConstants.VOLUME;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;

    private final DataSource dataSource;
    private final Security security;

    /**
     * Create a positive sequence of numbers
     */
    private AtomicSequence sequence = new AtomicSequence();

    @Inject
    StreamVolumeServiceImpl(final DataSource dataSource,
                            final Security security) {
        this.dataSource = dataSource;
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

    @SuppressWarnings("unchecked")
    @Override
    // @Transactional
    public BaseResultList<StreamVolume> find(final FindStreamVolumeCriteria criteria) {
        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> {
            if (!criteria.isValidCriteria()) {
                throw new IllegalArgumentException("Not enough criteria to run");
            }

//            final StreamRange streamRange = criteria.getStreamRange();
//            final boolean joinStreamType = streamRange != null && streamRange.getStreamTypeName() != null;
//            final boolean joinStream = (streamRange != null && streamRange.getCreatePeriod() != null) || (criteria.getStreamStatusSet() != null && criteria.getStreamStatusSet().isConstrained());

            final SqlBuilder sql = new SqlBuilder();
            sql.append("SELECT");
            sql.append(" sv.FK_STRM_ID, v.PATH, v.VOL_TP, n.ID, n.FK_RK_ID");
            sql.append(" FROM ");
            sql.append(TABLE_NAME);
            sql.append(" sv");
            sql.append(" JOIN VOL v ON (v.ID = sv.FK_VOL_ID)");
            sql.append(" JOIN ND n ON (n.ID = v.FK_ND_ID)");
//            if (joinStream) {
//                sql.append(" JOIN STRM s ON (s.ID = sv.FK_STRM_ID)");
//            }
//            if (joinStreamType) {
//                sql.append(" JOIN STRM_TP st ON (st.ID = s.FK_STRM_TP_ID)");
//            }
            sql.append(" WHERE 1=1");

            sql.appendCriteriaSetQuery("v.FK_ND_ID", criteria.getNodeIdSet());
            sql.appendCriteriaSetQuery("sv.FK_VOL_ID", criteria.getVolumeIdSet());
            sql.appendCriteriaSetQuery("sv.FK_STRM_ID", criteria.getStreamIdSet());
//            sql.appendPrimitiveValueSetQuery("s.STAT", StreamStatusId.convertStatusSet(criteria.getStreamStatusSet()));
//
//            if (streamRange != null) {
//                if (joinStreamType) {
//                    sql.append(" AND st.NAME = ");
//                    sql.arg(streamRange.getStreamTypeName());
//                }
//                if (streamRange.isFileLocation()) {
//                    sql.appendRangeQuery("sv.FK_STRM_ID", streamRange);
//                }
//                if (joinStream) {
//                    sql.appendRangeQuery("s.CRT_MS", streamRange.getCreatePeriod());
//                }
//            }

            sql.applyRestrictionCriteria(criteria);

            final List<StreamVolume> results = new ArrayList<>();
            queryStreamVolumes(sql, results);

            // Create the query
            return BaseResultList.createCriterialBasedList(results, criteria);
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
//            final boolean joinStreamType = streamRange != null && streamRange.getStreamTypeName() != null;
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
//                    sql.arg(streamRange.getStreamTypeName());
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

    @Override
    public FindStreamVolumeCriteria createCriteria() {
        return new FindStreamVolumeCriteria();
    }

    /**
     * Return the meta data volumes for a stream id.
     */
    @Override
    public Set<StreamVolume> findStreamVolume(final long streamId) {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT");
        sql.append(" sv.FK_STRM_ID, v.PATH, v.VOL_TP, n.ID, n.FK_RK_ID");
        sql.append(" FROM ");
        sql.append(TABLE_NAME);
        sql.append(" sv");
        sql.append(" JOIN VOL v ON (v.ID = sv.FK_VOL_ID)");
        sql.append(" JOIN ND n ON (n.ID = v.FK_ND_ID)");
        sql.append(" WHERE FK_STRM_ID = ");
        sql.arg(streamId);

        final Set<StreamVolume> results = new HashSet<>();
        queryStreamVolumes(sql, results);
        return results;
    }

    private void queryStreamVolumes(final SqlBuilder sql, final Collection<StreamVolume> results) {
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql.toString())) {
                PreparedStatementUtil.setArguments(preparedStatement, sql.getArgs());
                try (final ResultSet rs = preparedStatement.executeQuery()) {
                    while (rs.next()) {
                        final StreamVolume svol = new StreamVolumeImpl(
                                rs.getLong(1),
                                rs.getString(2),
                                VolumeType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(rs.getByte(3)),
                                rs.getInt(4),
                                rs.getInt(5)
                        );
                        results.add(svol);
                    }
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Set<StreamVolume> createStreamVolumes(final long streamId, final Set<VolumeEntity> volumes) {
        for (final VolumeEntity volume : volumes) {
            final SqlBuilder sql = new SqlBuilder();
            sql.append("INSERT INTO ");
            sql.append(TABLE_NAME);
            sql.append(" (VER, FK_STRM_ID, FK_VOL_ID) VALUES (");
            sql.arg(1);
            sql.append(", ");
            sql.arg(streamId);
            sql.append(", ");
            sql.arg(volume.getId());
            sql.append(")");

            try (final Connection connection = dataSource.getConnection()) {
                try (final PreparedStatement preparedStatement = connection.prepareStatement(sql.toString())) {
                    PreparedStatementUtil.setArguments(preparedStatement, sql.getArgs());
                    preparedStatement.executeUpdate();
                }
            } catch (final SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        return findStreamVolume(streamId);
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindStreamVolumeCriteria criteria) {
//        CriteriaLoggingUtil.appendRangeTerm(items, "streamRange", criteria.getStreamRange());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "streamStatusSet", criteria.getStreamStatusSet());
        CriteriaLoggingUtil.appendCriteriaSet(items, "nodeIdSet", criteria.getNodeIdSet());
        CriteriaLoggingUtil.appendCriteriaSet(items, "volumeIdSet", criteria.getVolumeIdSet());
        CriteriaLoggingUtil.appendCriteriaSet(items, "streamIdSet", criteria.getStreamIdSet());
        CriteriaLoggingUtil.appendPageRequest(items, criteria.getPageRequest());
    }

    /**
     * Given a set of volumes pick one that's nearest to us and readable,
     * otherwise a random one.
     */
    public StreamVolume pickBestVolume(final Set<StreamVolume> streamVolumes, final long nodeId, final long rackId) {
        // Try and locate a volume on the same node that is private
        for (final StreamVolume streamVolume : streamVolumes) {
            if (streamVolume.getVolumeType().equals(VolumeType.PRIVATE)
                    && streamVolume.getNodeId() == nodeId) {
                return streamVolume;
            }
        }

        // Otherwise have a go on one in the same rack that is public
        for (final StreamVolume streamVolume : streamVolumes) {
            if (streamVolume.getVolumeType().equals(VolumeType.PUBLIC)
                    && streamVolume.getRackId() == rackId) {
                return streamVolume;
            }
        }

        final Set<StreamVolume> publicVolumes = streamVolumes
                .stream()
                .filter(streamVolume -> streamVolume.getVolumeType().equals(VolumeType.PUBLIC))
                .collect(Collectors.toSet());

        if (publicVolumes.size() == 0) {
            return null;
        }

        // Otherwise pick a random one
        final Iterator<StreamVolume> iter = publicVolumes.iterator();
        final int pickIndex = pickIndex(publicVolumes.size());
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
}
