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

package stroom.meta.mock;

import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.api.DataRetentionTracker;
import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.FindDataRetentionImpactCriteria;
import stroom.data.shared.StreamTypeNames;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.EffectiveMetaDataCriteria;
import stroom.meta.api.EffectiveMetaSet;
import stroom.meta.api.EffectiveMetaSet.Builder;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.DataFormatNames;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.SimpleMeta;
import stroom.meta.shared.Status;
import stroom.processor.shared.FeedDependency;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.time.TimePeriod;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

@Singleton
public class MockMetaService implements MetaService, Clearable {

    private static final Set<String> STANDARD_TYPES = Set.of(
            StreamTypeNames.RAW_EVENTS,
            StreamTypeNames.RAW_REFERENCE,
            StreamTypeNames.EVENTS,
            StreamTypeNames.REFERENCE,
            StreamTypeNames.RECORDS,
            StreamTypeNames.ERROR);

    private final Set<String> feeds = new HashSet<>();
    private final Set<String> types = new HashSet<>(STANDARD_TYPES);
    private final Set<String> rawTypes = new HashSet<>(StreamTypeNames.ALL_HARD_CODED_RAW_STREAM_TYPE_NAMES);
    private final Set<String> dataFormats = new HashSet<>(DataFormatNames.ALL_HARD_CODED_FORMAT_NAMES);
    private final Map<Long, Meta> metaMap = new HashMap<>();

    /**
     * This id is used to emulate the primary key on the database.
     */
    private long currentId;

    @Override
    public Long getMaxId() {
        if (currentId == 0) {
            return null;
        }
        return currentId;
    }

    @Override
    public Long getMaxId(final long maxCreateTimeMs) {
        return getMaxId();
    }

    @Override
    public Meta create(final MetaProperties properties) {
        feeds.add(properties.getFeedName());
        types.add(properties.getTypeName());

        final Meta.Builder builder = Meta.builder();
        builder.parentDataId(properties.getParentId());
        builder.feedName(properties.getFeedName());
        builder.typeName(properties.getTypeName());
        builder.processorUuid(properties.getProcessorUuid());
        builder.createMs(properties.getCreateMs());
        builder.effectiveMs(properties.getEffectiveMs());
        builder.statusMs(properties.getStatusMs());
        builder.status(Status.LOCKED);

        currentId++;
        builder.id(currentId);

        final Meta meta = builder.build();
        metaMap.put(currentId, meta);

        return meta;
    }

    @Override
    public Meta getMeta(final long id) {
        return metaMap.get(id);
    }

    @Override
    public Meta getMeta(final long id, final boolean anyStatus) {
        return metaMap.get(id);
    }

    @Override
    public Meta updateStatus(final Meta meta, final Status currentStatus, final Status newStatus) {
        Objects.requireNonNull(meta, "Null data");

        Meta result = metaMap.get(meta.getId());
        if (result != null) {
            if (currentStatus != result.getStatus()) {
                throw new RuntimeException("Unexpected status " +
                                           result.getStatus() +
                                           " (expected " +
                                           currentStatus +
                                           ")");
            }

            result = meta
                    .copy()
                    .status(newStatus)
                    .statusMs(System.currentTimeMillis())
                    .build();
            metaMap.put(result.getId(), result);
        }
        return result;
    }

    @Override
    public int updateStatus(final FindMetaCriteria criteria, final Status currentStatus, final Status newStatus) {
        return 0;
    }

    @Override
    public void addAttributes(final Meta meta, final AttributeMap attributes) {
        // Do nothing.
    }

    @Override
    public int delete(final long id) {
        return delete(id, true);
    }

    @Override
    public int delete(final List<DataRetentionRuleAction> ruleActions,
                      final TimePeriod deletionPeriod) {
        return 0;
    }

    @Override
    public int delete(final long id, final boolean lockCheck) {
        final Meta meta = metaMap.get(id);
        if (lockCheck && !Status.UNLOCKED.equals(meta.getStatus())) {
            return 0;
        }

        if (metaMap.remove(id) != null) {
            return 1;
        }
        return 0;
    }

    @Override
    public int getLockCount() {
        return (int) metaMap.values().stream().filter(data -> Status.LOCKED.equals(data.getStatus())).count();
    }

    @Override
    public Set<String> getFeeds() {
        return feeds;
    }

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public Set<String> getRawTypes() {
        return rawTypes;
    }

    @Override
    public Set<String> getDataFormats() {
        return dataFormats;
    }

    @Override
    public ResultPage<Meta> find(final FindMetaCriteria criteria) {
        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(MetaFields.getAllFieldMap());
        final List<Meta> list = new ArrayList<>();
        for (final Entry<Long, Meta> entry : metaMap.entrySet()) {
            try {
                final Meta meta = entry.getValue();
//                final MetaRow row = new MetaRow(meta);
                final Map<String, Object> attributeMap = createAttributeMap(meta);
                if (criteria.getExpression() == null || expressionMatcher.match(attributeMap,
                        criteria.getExpression())) {
                    list.add(meta);
                }
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }

        return ResultPage.createUnboundedList(list);
    }

    @Override
    public ResultPage<Meta> findReprocess(final FindMetaCriteria criteria) {
        return null;
    }

    @Override
    public SelectionSummary getSelectionSummary(final FindMetaCriteria criteria) {
        return null;
    }

    @Override
    public SelectionSummary getSelectionSummary(final FindMetaCriteria criteria,
                                                final DocumentPermission permission) {
        return null;
    }

    @Override
    public SelectionSummary getReprocessSelectionSummary(final FindMetaCriteria criteria) {
        return null;
    }

    @Override
    public SelectionSummary getReprocessSelectionSummary(final FindMetaCriteria criteria,
                                                         final DocumentPermission permission) {
        return null;
    }

    /**
     * Turns a data row object into a generic map of attributes for use by an expression filter.
     */
    private static Map<String, Object> createAttributeMap(final Meta meta) {
        final Map<String, Object> attributeMap = new HashMap<>();

        if (meta != null) {
            attributeMap.put(MetaFields.ID.getFldName(), meta.getId());
            attributeMap.put(MetaFields.CREATE_TIME.getFldName(), meta.getCreateMs());
            attributeMap.put(MetaFields.EFFECTIVE_TIME.getFldName(), meta.getEffectiveMs());
            attributeMap.put(MetaFields.STATUS_TIME.getFldName(), meta.getStatusMs());
            attributeMap.put(MetaFields.STATUS.getFldName(), meta.getStatus().getDisplayValue());
            if (meta.getParentMetaId() != null) {
                attributeMap.put(MetaFields.PARENT_ID.getFldName(), meta.getParentMetaId());
            }
            if (meta.getTypeName() != null) {
                attributeMap.put(MetaFields.TYPE.getFldName(), meta.getTypeName());
            }
            final String feedName = meta.getFeedName();
            if (feedName != null) {
                attributeMap.put(MetaFields.FEED.getFldName(), feedName);
            }
            final String pipelineUuid = meta.getPipelineUuid();
            attributeMap.put(MetaFields.PIPELINE.getFldName(), pipelineUuid);
//            if (processor != null) {
//                final String pipelineUuid = processor.getPipelineUuid();
//                if (pipelineUuid != null) {
//                    attributeMap.put(MetaDataSource.PIPELINE, pipelineUuid);
//                }
//            }
        }
//
//        MetaFieldNames.getExtendedFields().forEach(field -> {
//            final String value = row.getAttributeValue(field.getName());
//            if (value != null) {
//                try {
//                    switch (field.getType()) {
//                        case FIELD:
//                            attributeMap.put(field.getName(), value);
//                            break;
//                        case DATE_FIELD:
//                            attributeMap.put(field.getName(), DateUtil.parseNormalDateTimeString(value));
//                            break;
//                        default:
//                            attributeMap.put(field.getName(), Long.valueOf(value));
//                            break;
//                    }
//                } catch (final RuntimeException e) {
//                    LOGGER.error(e.getMessage(), e);
//                }
//            }
//        });
        return attributeMap;
    }

    @Override
    public ResultPage<MetaRow> findRows(final FindMetaCriteria criteria) {
        return null;
    }

    @Override
    public ResultPage<MetaRow> findDecoratedRows(final FindMetaCriteria criteria) {
        return null;
    }

    @Override
    public List<MetaRow> findRelatedData(final long id, final boolean anyStatus) {
        return null;
    }

    @Override
    public EffectiveMetaSet findEffectiveData(final EffectiveMetaDataCriteria criteria) {
        final Builder builder = EffectiveMetaSet.builder(criteria.getFeed(), criteria.getType());

        try {
            metaMap.values()
                    .stream()
                    .filter(meta ->
                            NullSafe.test(criteria.getType(), type -> type.equals(meta.getTypeName())))
                    .filter(meta ->
                            NullSafe.test(criteria.getFeed(), feed -> feed.equals(meta.getFeedName())))
                    .forEach(meta -> builder.add(meta.getId(), meta.getEffectiveMs()));

        } catch (final RuntimeException e) {
            System.out.println(e.getMessage());
            // Ignore ... just a mock
        }
        return builder.build();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final long id : metaMap.keySet()) {
            final Meta meta = metaMap.get(id);
            sb.append(meta);
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public void clear() {
        feeds.clear();
        types.clear();
        types.addAll(STANDARD_TYPES);
        metaMap.clear();
        currentId = 0;
    }

    @Override
    public List<DataRetentionTracker> getRetentionTrackers() {
        return Collections.emptyList();
    }

    @Override
    public void setTracker(final DataRetentionTracker dataRetentionTracker) {

    }

    @Override
    public void deleteTrackers(final String rulesVersion) {

    }

    public Map<Long, Meta> getMetaMap() {
        return metaMap;
    }

    @Override
    public List<String> getProcessorUuidList(final FindMetaCriteria criteria) {
        return null;
    }

    @Override
    public List<DataRetentionDeleteSummary> getRetentionDeleteSummary(final String queryId,
                                                                      final DataRetentionRules rules,
                                                                      final FindDataRetentionImpactCriteria criteria) {
        return Collections.emptyList();
    }

    @Override
    public boolean cancelRetentionDeleteSummary(final String queryId) {
        return true;
    }

    @Override
    public Set<Long> findLockedMeta(final Collection<Long> metaIdCollection) {
        return null;
    }

    @Override
    public List<SimpleMeta> getLogicallyDeleted(final Instant deleteThreshold,
                                                final int batchSize,
                                                final Set<Long> metaIdExcludeSet) {
        return Collections.emptyList();
    }

    @Override
    public List<SimpleMeta> findBatch(final long minId, final Long maxId, final int batchSize) {
        return Collections.emptyList();
    }

    @Override
    public Set<Long> exists(final Set<Long> ids) {
        return Collections.emptySet();
    }

    @Override
    public Instant getFeedDependencyEffectiveTime(final List<FeedDependency> feedDependencies) {
        return null;
    }
}
