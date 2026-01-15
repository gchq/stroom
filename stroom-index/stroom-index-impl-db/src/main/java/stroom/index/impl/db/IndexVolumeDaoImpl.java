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

package stroom.index.impl.db;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.db.jooq.Tables;
import stroom.index.impl.db.jooq.tables.records.IndexVolumeRecord;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.index.shared.IndexVolumeFields;
import stroom.index.shared.IndexVolumeGroup;
import stroom.query.api.ExpressionOperator;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private static final BiFunction<IndexVolume, IndexVolumeRecord, IndexVolumeRecord> INDEX_VOLUME_TO_RECORD_MAPPER =
            (indexVolume, record) -> {
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
        genericDao = new GenericDao<>(
                indexDbConnProvider,
                INDEX_VOLUME,
                INDEX_VOLUME.ID,
                INDEX_VOLUME_TO_RECORD_MAPPER,
                RECORD_TO_INDEX_VOLUME_MAPPER);

        // Standard fields.
        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(IndexVolumeFields.ID, INDEX_VOLUME.ID, Integer::valueOf);
        expressionMapper.map(IndexVolumeFields.GROUP_ID, INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID, Integer::valueOf);
        expressionMapper.map(IndexVolumeFields.NODE_NAME, INDEX_VOLUME.NODE_NAME, value -> value);
        expressionMapper.map(IndexVolumeFields.PATH, INDEX_VOLUME.PATH, value -> value);
    }

    @Override
    public IndexVolume create(final IndexVolume indexVolume) {
        return genericDao.create(indexVolume);
    }

    @Override
    public IndexVolume update(final IndexVolume indexVolume) {
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
                        .fetch())
                .map(RECORD_TO_INDEX_VOLUME_MAPPER::apply);
    }

    @Override
    public List<IndexVolume> getVolumesInGroupOnNode(final String groupName, final String nodeName) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select()
                        .from(INDEX_VOLUME)
                        .join(INDEX_VOLUME_GROUP).on(INDEX_VOLUME_GROUP.ID.eq(INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID))
                        .where(INDEX_VOLUME_GROUP.NAME.eq(groupName))
                        .and(INDEX_VOLUME.NODE_NAME.eq(nodeName))
                        .fetch())
                .map(RECORD_TO_INDEX_VOLUME_MAPPER::apply);
    }

    @Override
    public List<IndexVolume> getVolumesInGroup(final String groupName) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select()
                        .from(INDEX_VOLUME)
                        .join(INDEX_VOLUME_GROUP).on(INDEX_VOLUME_GROUP.ID.eq(INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID))
                        .where(INDEX_VOLUME_GROUP.NAME.eq(groupName))
                        .fetch())
                .map(RECORD_TO_INDEX_VOLUME_MAPPER::apply);
    }

    @Override
    public List<IndexVolume> getVolumesInGroup(final int groupId) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select()
                        .from(INDEX_VOLUME)
                        .where(INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID.eq(groupId))
                        .fetch())
                .map(RECORD_TO_INDEX_VOLUME_MAPPER::apply);
    }

    @Override
    public Map<String, List<IndexVolume>> getVolumesOnNodeGrouped(final String nodeName) {
        final Map<String, List<IndexVolume>> map = new HashMap<>();
        JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select(INDEX_VOLUME_GROUP.NAME, INDEX_VOLUME.asterisk())
                        .from(INDEX_VOLUME)
                        .join(INDEX_VOLUME_GROUP).on(INDEX_VOLUME_GROUP.ID.eq(INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID))
                        .where(INDEX_VOLUME.NODE_NAME.eq(nodeName))
                        .fetch())
                .forEach(rec -> {
                    final String groupName = rec.get(INDEX_VOLUME_GROUP.NAME);
                    final IndexVolume indexVolume = RECORD_TO_INDEX_VOLUME_MAPPER.apply(rec);
                    map.computeIfAbsent(groupName, k -> new ArrayList<>())
                            .add(indexVolume);
                });
        return map;
    }

    @Override
    public void updateVolumeState(final int id,
                                  final Long updateTimeMs,
                                  final Long bytesUsed,
                                  final Long bytesFree,
                                  final Long bytesTotal) {
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
        final Condition condition = createCondition(criteria);
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);

        int offset = 0;
        int numberOfRows = 1000000;

        if (pageRequest != null) {
            offset = pageRequest.getOffset();
            numberOfRows = pageRequest.getLength();
        }

        final List<IndexVolume> list = find(
                condition,
                orderFields,
                offset,
                numberOfRows + 1);
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private List<IndexVolume> find(final Condition condition,
                                   final Collection<OrderField<?>> orderFields,
                                   final int offset,
                                   final int numberOfRows) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select()
                        .from(INDEX_VOLUME)
                        .where(condition)
                        .orderBy(orderFields)
                        .limit(offset, numberOfRows)
                        .fetch())
                .map(RECORD_TO_INDEX_VOLUME_MAPPER::apply);
    }

    @Override
    public Set<IndexVolumeGroup> getGroups(final String nodeName, final String path) {
        return new HashSet<>(JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select(Tables.INDEX_VOLUME_GROUP.asterisk())
                        .from(Tables.INDEX_VOLUME_GROUP)
                        .innerJoin(Tables.INDEX_VOLUME)
                        .on(Tables.INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID.eq(Tables.INDEX_VOLUME_GROUP.ID))
                        .where(Tables.INDEX_VOLUME.PATH.eq(path))
                        .and(Tables.INDEX_VOLUME.NODE_NAME.eq(nodeName))
                        .fetch())
                .map(IndexVolumeGroupDaoImpl.RECORD_TO_INDEX_VOLUME_GROUP_MAPPER::apply));
    }

    private Condition createCondition(final ExpressionCriteria criteria) {
        return createCondition(criteria.getExpression());
    }

    private Condition createCondition(final ExpressionOperator expression) {
        return expressionMapper.apply(expression);
    }

    private Collection<OrderField<?>> createOrderFields(final ExpressionCriteria criteria) {
        if (criteria.getSortList() == null || criteria.getSortList().size() == 0) {
            return Collections.singleton(INDEX_VOLUME.ID);
        }

        return criteria.getSortList().stream().map(sort -> {
            final Field<?> field;
            if (IndexVolumeFields.FIELD_ID.equals(sort.getId())) {
                field = INDEX_VOLUME.ID;
            } else if (IndexVolumeFields.FIELD_NODE_NAME.equals(sort.getId())) {
                field = INDEX_VOLUME.NODE_NAME;
            } else if (IndexVolumeFields.FIELD_PATH.equals(sort.getId())) {
                field = INDEX_VOLUME.PATH;
            } else {
                field = INDEX_VOLUME.ID;
            }

            OrderField<?> orderField = field;
            if (sort.isDesc()) {
                orderField = field.desc();
            }

            return orderField;
        }).collect(Collectors.toList());
    }
}
