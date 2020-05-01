package stroom.index.impl.db;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.db.jooq.tables.records.IndexVolumeRecord;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.index.shared.IndexVolumeFields;
import stroom.query.api.v2.ExpressionOperator;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Sort;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static stroom.index.impl.db.jooq.tables.IndexVolume.INDEX_VOLUME;
import static stroom.index.impl.db.jooq.tables.IndexVolumeGroup.INDEX_VOLUME_GROUP;

class IndexVolumeDaoImpl implements IndexVolumeDao {
    static final Function<Record, IndexVolume> RECORD_TO_INDEX_VOLUME_MAPPER = record -> {
        final IndexVolume indexVolume = new IndexVolume();
        indexVolume.setId(record.get(INDEX_VOLUME.ID));
        indexVolume.setVersion(record.get(INDEX_VOLUME.VERSION));
        indexVolume.setCreateTimeMs(record.get(INDEX_VOLUME.CREATE_TIME_MS));
        indexVolume.setCreateUser(record.get(INDEX_VOLUME.CREATE_USER));
        indexVolume.setUpdateTimeMs(record.get(INDEX_VOLUME.UPDATE_TIME_MS));
        indexVolume.setUpdateUser(record.get(INDEX_VOLUME.UPDATE_USER));
        indexVolume.setPath(record.get(INDEX_VOLUME.PATH));
        indexVolume.setNodeName(record.get(INDEX_VOLUME.NODE_NAME));
        indexVolume.setIndexVolumeGroupId(record.get(INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID));
        final Byte state = record.get(INDEX_VOLUME.STATE);
        if (state != null) {
            indexVolume.setState(VolumeUseState.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(state));
        }
        indexVolume.setBytesLimit(record.get(INDEX_VOLUME.BYTES_LIMIT));
        indexVolume.setBytesUsed(record.get(INDEX_VOLUME.BYTES_USED));
        indexVolume.setBytesFree(record.get(INDEX_VOLUME.BYTES_FREE));
        indexVolume.setBytesTotal(record.get(INDEX_VOLUME.BYTES_TOTAL));
        indexVolume.setStatusMs(record.get(INDEX_VOLUME.STATUS_MS));
        return indexVolume;
    };

    private static final BiFunction<IndexVolume, IndexVolumeRecord, IndexVolumeRecord> INDEX_VOLUME_TO_RECORD_MAPPER = (indexVolume, record) -> {
        record.from(indexVolume);
        record.set(INDEX_VOLUME.ID, indexVolume.getId());
        record.set(INDEX_VOLUME.VERSION, indexVolume.getVersion());
        record.set(INDEX_VOLUME.CREATE_TIME_MS, indexVolume.getCreateTimeMs());
        record.set(INDEX_VOLUME.CREATE_USER, indexVolume.getCreateUser());
        record.set(INDEX_VOLUME.UPDATE_TIME_MS, indexVolume.getUpdateTimeMs());
        record.set(INDEX_VOLUME.UPDATE_USER, indexVolume.getUpdateUser());
        record.set(INDEX_VOLUME.PATH, indexVolume.getPath());
        record.set(INDEX_VOLUME.NODE_NAME, indexVolume.getNodeName());
        record.set(INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID, indexVolume.getIndexVolumeGroupId());
        if (indexVolume.getState() != null) {
            record.set(INDEX_VOLUME.STATE, indexVolume.getState().getPrimitiveValue());
        }
        record.set(INDEX_VOLUME.BYTES_LIMIT, indexVolume.getBytesLimit());
        record.set(INDEX_VOLUME.BYTES_USED, indexVolume.getBytesUsed());
        record.set(INDEX_VOLUME.BYTES_FREE, indexVolume.getBytesFree());
        record.set(INDEX_VOLUME.BYTES_TOTAL, indexVolume.getBytesTotal());
        record.set(INDEX_VOLUME.STATUS_MS, indexVolume.getStatusMs());
        return record;
    };

    private final IndexDbConnProvider indexDbConnProvider;
    private final GenericDao<IndexVolumeRecord, IndexVolume, Integer> genericDao;
    private final ExpressionMapper expressionMapper;

    @Inject
    IndexVolumeDaoImpl(final IndexDbConnProvider indexDbConnProvider,
                       final ExpressionMapperFactory expressionMapperFactory) {
        this.indexDbConnProvider = indexDbConnProvider;
        genericDao = new GenericDao<>(INDEX_VOLUME, INDEX_VOLUME.ID, IndexVolume.class, indexDbConnProvider);
        genericDao.setRecordToObjectMapper(RECORD_TO_INDEX_VOLUME_MAPPER);
        genericDao.setObjectToRecordMapper(INDEX_VOLUME_TO_RECORD_MAPPER);

        // Standard fields.
        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(IndexVolumeFields.ID, INDEX_VOLUME.ID, Integer::valueOf);
        expressionMapper.map(IndexVolumeFields.GROUP_ID, INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID, Integer::valueOf);
        expressionMapper.map(IndexVolumeFields.NODE_NAME, INDEX_VOLUME.NODE_NAME, value -> value);
    }

    @Override
    public IndexVolume create(final IndexVolume indexVolume) {
        return genericDao.create(indexVolume);
    }

    @Override
    public IndexVolume update(IndexVolume indexVolume) {
        return genericDao.update(indexVolume);
    }

    @Override
    public Optional<IndexVolume> fetch(final int id) {
        return genericDao.fetch(id);
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
    }

    @Override
    public List<IndexVolume> getAll() {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select()
                .from(INDEX_VOLUME)
                .fetch()
                .map(RECORD_TO_INDEX_VOLUME_MAPPER::apply));
    }

    @Override
    public List<IndexVolume> getVolumesInGroupOnNode(final String groupName, final String nodeName) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select()
                .from(INDEX_VOLUME)
                .join(INDEX_VOLUME_GROUP).on(INDEX_VOLUME_GROUP.ID.eq(INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID))
                .where(INDEX_VOLUME_GROUP.NAME.eq(groupName))
                .and(INDEX_VOLUME.NODE_NAME.eq(nodeName))
                .fetch()
                .map(RECORD_TO_INDEX_VOLUME_MAPPER::apply));
    }

    @Override
    public List<IndexVolume> getVolumesInGroup(final String groupName) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select()
                .from(INDEX_VOLUME)
                .join(INDEX_VOLUME_GROUP).on(INDEX_VOLUME_GROUP.ID.eq(INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID))
                .where(INDEX_VOLUME_GROUP.NAME.eq(groupName))
                .fetch()
                .map(RECORD_TO_INDEX_VOLUME_MAPPER::apply));
    }

    @Override
    public void updateVolumeState(final int id, final Long updateTimeMs, final Long bytesUsed, final Long bytesFree, final Long bytesTotal) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_VOLUME)
                .set(INDEX_VOLUME.UPDATE_TIME_MS, updateTimeMs)
                .set(INDEX_VOLUME.BYTES_USED, bytesUsed)
                .set(INDEX_VOLUME.BYTES_FREE, bytesFree)
                .set(INDEX_VOLUME.BYTES_TOTAL, bytesTotal)
                .where(INDEX_VOLUME.ID.eq(id))
                .execute());
    }

    @Override
    public ResultPage<IndexVolume> find(final ExpressionCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria);
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);

        int offset = 0;
        int numberOfRows = 1000000;

        if (pageRequest != null) {
            offset = pageRequest.getOffset().intValue();
            numberOfRows = pageRequest.getLength();
        }

        final List<IndexVolume> list = find(conditions, orderFields, offset, numberOfRows);
        return ResultPage.createPageResultList(list, criteria.getPageRequest(), null);
    }

    private List<IndexVolume> find(final Collection<Condition> conditions, final Collection<OrderField<?>> orderFields, final int offset, final int numberOfRows) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select()
                .from(INDEX_VOLUME)
                .where(conditions)
                .orderBy(orderFields)
                .limit(offset, numberOfRows)
                .fetch()
                .map(RECORD_TO_INDEX_VOLUME_MAPPER::apply));
    }

    private Collection<Condition> createCondition(final ExpressionCriteria criteria) {
        return createCondition(criteria.getExpression());
    }

    private Collection<Condition> createCondition(final ExpressionOperator expression) {
        return expressionMapper.apply(expression);
    }

    private Collection<OrderField<?>> createOrderFields(final ExpressionCriteria criteria) {
        if (criteria.getSortList() == null || criteria.getSortList().size() == 0) {
            return Collections.singleton(INDEX_VOLUME.ID);
        }

        return criteria.getSortList().stream().map(sort -> {
            Field<?> field;
            if (IndexVolumeFields.FIELD_ID.equals(sort.getField())) {
                field = INDEX_VOLUME.ID;
            } else if (IndexVolumeFields.FIELD_NODE_NAME.equals(sort.getField())) {
                field = INDEX_VOLUME.NODE_NAME;
            } else {
                field = INDEX_VOLUME.ID;
            }

            OrderField<?> orderField = field;
            if (Sort.Direction.DESCENDING.equals(sort.getDirection())) {
                orderField = field.desc();
            }

            return orderField;
        }).collect(Collectors.toList());
    }
}
