package stroom.streamstore.meta.impl.mock;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.Period;
import stroom.streamstore.meta.api.EffectiveMetaDataCriteria;
import stroom.streamstore.meta.api.FindStreamCriteria;
import stroom.streamstore.meta.api.Stream;
import stroom.streamstore.meta.api.StreamMetaService;
import stroom.streamstore.meta.api.StreamProperties;
import stroom.streamstore.meta.api.StreamStatus;
import stroom.streamstore.shared.StreamDataRow;
import stroom.streamstore.shared.StreamDataSource;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class MockStreamMetaService implements StreamMetaService, Clearable {
    private final Set<String> feeds = new HashSet<>();
    private final Set<String> streamTypes = new HashSet<>();
    private final Map<Long, Stream> streamMap = new HashMap<>();

    /**
     * This id is used to emulate the primary key on the database.
     */
    private long currentId;


    @Override
    public Stream createStream(final StreamProperties streamProperties) {
        feeds.add(streamProperties.getFeedName());
        streamTypes.add(streamProperties.getStreamTypeName());

        final MockStream.Builder builder = new MockStream.Builder();
        builder.parentStreamId(streamProperties.getParentId());
        builder.feedName(streamProperties.getFeedName());
        builder.streamTypeName(streamProperties.getStreamTypeName());
        builder.streamProcessorId(streamProperties.getStreamProcessorId());
        builder.streamTaskId(streamProperties.getStreamTaskId());
        builder.createMs(streamProperties.getCreateMs());
        builder.effectiveMs(streamProperties.getEffectiveMs());
        builder.statusMs(streamProperties.getStatusMs());
        builder.status(StreamStatus.LOCKED);

        currentId++;
        builder.id(currentId);

        final Stream stream = builder.build();
        streamMap.put(currentId, stream);

        return stream;
    }

    @Override
    public List<Stream> findEffectiveStream(final EffectiveMetaDataCriteria criteria) {
        final ArrayList<Stream> results = new ArrayList<>();

        try {
            for (final Stream stream : streamMap.values()) {
                boolean match = true;

                if (criteria.getStreamType() != null && !criteria.getStreamType().equals(stream.getStreamTypeName())) {
                    match = false;
                }
                if (criteria.getFeed() != null && !criteria.getFeed().equals(stream.getFeedName())) {
                    match = false;
                }

                if (match) {
                    results.add(stream);
                }
            }
        } catch (final RuntimeException e) {
            System.out.println(e.getMessage());
            // Ignore ... just a mock
        }

        return BaseResultList.createUnboundedList(results);
    }

    @Override
    public boolean canReadStream(final long streamId) {
        return streamMap.containsKey(streamId);
    }

    @Override
    public Stream getStream(final long streamId) {
        return streamMap.get(streamId);
    }

    @Override
    public Stream getStream(final long streamId, final boolean anyStatus) {
        return streamMap.get(streamId);
    }

    @Override
    public Stream updateStatus(final long id, final StreamStatus streamStatus) {
        MockStream stream = (MockStream) streamMap.get(id);
        if (stream != null) {
            stream.status = streamStatus;
            stream.statusMs = System.currentTimeMillis();
        }
        return stream;
    }

    @Override
    public int deleteStream(final long streamId) {
        return deleteStream(streamId, true);
    }

    @Override
    public int deleteStream(final long streamId, final boolean lockCheck) {
        final Stream stream = streamMap.get(streamId);
        if (lockCheck && !StreamStatus.UNLOCKED.equals(stream.getStatus())) {
            return 0;
        }

        if (streamMap.remove(streamId) != null) {
            return 1;
        }
        return 0;
    }

    @Override
    public int getLockCount() {
        return (int) streamMap.values().stream().filter(stream -> StreamStatus.LOCKED.equals(stream.getStatus())).count();
    }

    @Override
    public Period getCreatePeriod() {
        return new Period(0L, Long.MAX_VALUE);
    }

    @Override
    public List<String> getFeeds() {
        return feeds.stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getStreamTypes() {
        return streamTypes.stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    @Override
    public int findDelete(final FindStreamCriteria criteria) {
        return 0;
    }

    @Override
    public BaseResultList<Stream> find(final FindStreamCriteria criteria) {
        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(StreamDataSource.getExtendedFieldMap());
        final List<Stream> list = new ArrayList<>();
        for (final Entry<Long, Stream> entry : streamMap.entrySet()) {
            try {
                final Stream stream = entry.getValue();
                final StreamDataRow streamAttributeMap = new StreamDataRow(stream);
                final Map<String, Object> attributeMap = StreamAttributeMapUtil.createAttributeMap(streamAttributeMap);
                if (expressionMatcher.match(attributeMap, criteria.getExpression())) {
                    list.add(stream);
                }
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }

        return BaseResultList.createUnboundedList(list);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final long streamId : streamMap.keySet()) {
            final Stream stream = streamMap.get(streamId);
            sb.append(stream);
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public void clear() {
        feeds.clear();
        streamTypes.clear();
        streamMap.clear();
        currentId = 0;
    }

    public Map<Long, Stream> getStreamMap() {
        return streamMap;
    }
}
