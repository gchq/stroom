package stroom.meta.mock;

import stroom.meta.api.ExpressionMatcher;
import stroom.meta.shared.AttributeMap;
import stroom.meta.shared.EffectiveMetaDataCriteria;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.Status;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.Clearable;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class MockMetaService implements MetaService, Clearable {
    private final Set<String> feeds = new HashSet<>();
    private final Set<String> types = new HashSet<>();
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
    public Meta create(final MetaProperties properties) {
        feeds.add(properties.getFeedName());
        types.add(properties.getTypeName());

        final Meta.Builder builder = new Meta.Builder();
        builder.parentDataId(properties.getParentId());
        builder.feedName(properties.getFeedName());
        builder.typeName(properties.getTypeName());
        builder.processorUuid(properties.getProcessorUuid());
        builder.processorTaskId(properties.getProcessorTaskId());
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

            result = new Meta.Builder(meta)
                    .status(newStatus)
                    .statusMs(System.currentTimeMillis())
                    .build();
            metaMap.put(result.getId(), result);
        }
        return result;
    }

    @Override
    public int updateStatus(final FindMetaCriteria criteria, final Status status) {
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

//    @Override
//    public Period getCreatePeriod() {
//        return new Period(0L, Long.MAX_VALUE);
//    }

    @Override
    public List<String> getFeeds() {
        return feeds.stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getTypes() {
        return types.stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    @Override
    public BaseResultList<Meta> find(final FindMetaCriteria criteria) {
        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(MetaFields.getExtendedFieldMap(), null, null);
        final List<Meta> list = new ArrayList<>();
        for (final Entry<Long, Meta> entry : metaMap.entrySet()) {
            try {
                final Meta meta = entry.getValue();
//                final MetaRow row = new MetaRow(meta);
                final Map<String, Object> attributeMap = createAttributeMap(meta);
                if (expressionMatcher.match(attributeMap, criteria.getExpression())) {
                    list.add(meta);
                }
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }

        return BaseResultList.createUnboundedList(list);
    }

    /**
     * Turns a data row object into a generic map of attributes for use by an expression filter.
     */
    private static Map<String, Object> createAttributeMap(final Meta meta) {
        final Map<String, Object> attributeMap = new HashMap<>();

        if (meta != null) {
            attributeMap.put(MetaFields.ID.getName(), meta.getId());
            attributeMap.put(MetaFields.CREATE_TIME.getName(), meta.getCreateMs());
            attributeMap.put(MetaFields.EFFECTIVE_TIME.getName(), meta.getEffectiveMs());
            attributeMap.put(MetaFields.STATUS_TIME.getName(), meta.getStatusMs());
            attributeMap.put(MetaFields.STATUS.getName(), meta.getStatus().getDisplayValue());
            if (meta.getParentMetaId() != null) {
                attributeMap.put(MetaFields.PARENT_ID.getName(), meta.getParentMetaId());
            }
            if (meta.getTypeName() != null) {
                attributeMap.put(MetaFields.TYPE_NAME.getName(), meta.getTypeName());
            }
            final String feedName = meta.getFeedName();
            if (feedName != null) {
                attributeMap.put(MetaFields.FEED_NAME.getName(), feedName);
            }
            final String pipelineUuid = meta.getPipelineUuid();
            attributeMap.put(MetaFields.PIPELINE.getName(), pipelineUuid);
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
    public BaseResultList<MetaRow> findRows(final FindMetaCriteria criteria) {
        return null;
    }

    @Override
    public List<MetaRow> findRelatedData(final long id, final boolean anyStatus) {
        return null;
    }

    @Override
    public Set<Meta> findEffectiveData(final EffectiveMetaDataCriteria criteria) {
        final Set<Meta> results = new HashSet<>();

        try {
            for (final Meta meta : metaMap.values()) {
                boolean match = true;

                if (criteria.getType() != null && !criteria.getType().equals(meta.getTypeName())) {
                    match = false;
                }
                if (criteria.getFeed() != null && !criteria.getFeed().equals(meta.getFeedName())) {
                    match = false;
                }

                if (match) {
                    results.add(meta);
                }
            }
        } catch (final RuntimeException e) {
            System.out.println(e.getMessage());
            // Ignore ... just a mock
        }

        return results;
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
        metaMap.clear();
        currentId = 0;
    }

    public Map<Long, Meta> getMetaMap() {
        return metaMap;
    }
}
