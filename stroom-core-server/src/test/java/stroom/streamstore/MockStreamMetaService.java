package stroom.streamstore;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.Period;
import stroom.streamstore.api.StreamProperties;
import stroom.streamstore.meta.StreamMetaService;
import stroom.streamstore.shared.FeedEntity;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.FindStreamTypeCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamEntity;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamStatusId;
import stroom.streamstore.shared.StreamTypeEntity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Singleton
public class MockStreamMetaService implements StreamMetaService, Clearable {
    private final FeedEntityService feedService;
    private final StreamTypeEntityService streamTypeService;

    private final Map<Long, Stream> streamMap = new HashMap<>();

    /**
     * This id is used to emulate the primary key on the database.
     */
    private long currentId;

    @Inject
    MockStreamMetaService(final FeedEntityService feedService,
                          final StreamTypeEntityService streamTypeService) {
        this.feedService = feedService;
        this.streamTypeService = streamTypeService;
    }

    public MockStreamMetaService() {
        this.feedService = new MockFeedService();
        this.streamTypeService = new MockStreamTypeService();
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
    public Stream createStream(final StreamProperties streamProperties) {
        final StreamTypeEntity streamType = streamTypeService.getOrCreate(streamProperties.getStreamTypeName());
        final FeedEntity feed = feedService.getOrCreate(streamProperties.getFeedName());

        final StreamEntity stream = new StreamEntity();

        if (streamProperties.getParent() != null) {
            stream.setParentStreamId(streamProperties.getParent().getId());
        }

        stream.setFeed(feed);
        stream.setStreamType(streamType);
        stream.setStreamProcessor(streamProperties.getStreamProcessor());
        if (streamProperties.getStreamTask() != null) {
            stream.setStreamTaskId(streamProperties.getStreamTask().getId());
        }
        stream.setCreateMs(streamProperties.getCreateMs());
        stream.setEffectiveMs(streamProperties.getEffectiveMs());
        stream.setStatusMs(streamProperties.getStatusMs());
        stream.setPstatus(StreamStatusId.LOCKED);

        currentId++;
        stream.setId(currentId);
        streamMap.put(stream.getId(), stream);

        return stream;
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
        Stream stream = streamMap.get(id);
        if (stream != null) {
            final StreamEntity streamEntity = (StreamEntity) stream;
            streamEntity.updateStatus(streamStatus);
        }
        return stream;
    }

    @Override
    public Long deleteStream(final long streamId) {
        return deleteStream(streamId, true);
    }

    @Override
    public Long deleteStream(final long streamId, final boolean lockCheck) {
        final Stream stream = streamMap.get(streamId);
        if (lockCheck && !StreamStatus.UNLOCKED.equals(stream.getStatus())) {
            return 0L;
        }

        if (streamMap.remove(streamId) != null) {
            return 1L;
        }
        return 0L;
    }

    @Override
    public long getLockCount() {
        return streamMap.values().stream().filter(stream -> StreamStatus.LOCKED.equals(stream.getStatus())).count();
    }

    @Override
    public Period getCreatePeriod() {
        return new Period(0L, Long.MAX_VALUE);
    }

    @Override
    public List<String> getFeeds() {
        final List<FeedEntity> feeds = feedService.find(new FindFeedCriteria());
        if (feeds == null) {
            return Collections.emptyList();
        }
        return feeds.stream()
                .map(NamedEntity::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getStreamTypes() {
        final List<StreamTypeEntity> streamTypes = streamTypeService.find(new FindStreamTypeCriteria());
        if (streamTypes == null) {
            return Collections.emptyList();
        }
        return streamTypes.stream()
                .map(NamedEntity::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    @Override
    public Long findDelete(final FindStreamCriteria criteria) {
        return null;
    }

    @Override
    public BaseResultList<Stream> find(final FindStreamCriteria criteria) {
        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(StreamDataSource.getExtendedFieldMap(), null);
        final List<Stream> list = new ArrayList<>();
        for (final Entry<Long, Stream> entry : streamMap.entrySet()) {
            try {
                final Stream stream = entry.getValue();
                final StreamAttributeMap streamAttributeMap = new StreamAttributeMap(stream);
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
    public FindStreamCriteria createCriteria() {
        return new FindStreamCriteria();
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
        streamMap.clear();
        currentId = 0;
    }

    public Map<Long, Stream> getStreamMap() {
        return streamMap;
    }
}
