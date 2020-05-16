package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.feed.impl.FeedSerialiser;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.shared.ImportState;
import stroom.legacy.model_6_1.Feed;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Singleton
class FeedDataMapConverter implements DataMapConverter {
    private final FeedSerialiser serialiser;

    @Inject
    FeedDataMapConverter(final FeedSerialiser serialiser) {
        this.serialiser = serialiser;
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportState.ImportMode importMode,
                                       final String userId) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 0 && !dataMap.containsKey("meta")) {
            try {
                final Feed oldFeed = new Feed();
                LegacyXmlSerialiser.performImport(oldFeed, dataMap);

                final FeedDoc document = new FeedDoc();
                document.setType(docRef.getType());
                document.setUuid(docRef.getUuid());
                document.setName(docRef.getName());
                document.setVersion(UUID.randomUUID().toString());
                document.setCreateTimeMs(oldFeed.getCreateTime());
                document.setUpdateTimeMs(oldFeed.getUpdateTime());
                document.setCreateUser(oldFeed.getCreateUser());
                document.setUpdateUser(oldFeed.getUpdateUser());

                document.setDescription(oldFeed.getDescription());
                document.setClassification(oldFeed.getClassification());
                document.setEncoding(oldFeed.getEncoding());
                document.setContextEncoding(oldFeed.getContextEncoding());
                document.setRetentionDayAge(oldFeed.getRetentionDayAge());
                document.setReference(oldFeed.isReference());
                if (oldFeed.getStreamType() != null) {
                    document.setStreamType(oldFeed.getStreamType().getName());
                }
                if (oldFeed.getStatus() != null) {
                    document.setStatus(FeedDoc.FeedStatus.valueOf(oldFeed.getStatus().name()));
                }

                result = serialiser.write(document);

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }
}
