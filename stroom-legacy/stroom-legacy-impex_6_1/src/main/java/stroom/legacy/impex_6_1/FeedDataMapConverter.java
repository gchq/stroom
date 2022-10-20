package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.feed.impl.FeedSerialiser;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.shared.ImportState;
import stroom.legacy.model_6_1.Feed;
import stroom.legacy.model_6_1.StreamType;
import stroom.meta.api.MetaService;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Severity;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
@Deprecated
class FeedDataMapConverter implements DataMapConverter {

    private final FeedSerialiser serialiser;
    private final Provider<MetaService> metaServiceProvider;

    @Inject
    FeedDataMapConverter(final FeedSerialiser serialiser,
                         final Provider<MetaService> metaServiceProvider) {
        this.serialiser = serialiser;
        this.metaServiceProvider = metaServiceProvider;
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
                setStreamType(document, oldFeed, importState);
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

    private void setStreamType(final FeedDoc feedDoc,
                               final Feed oldFeed,
                               final ImportState importState) {

        NullSafe.consume(
                oldFeed.getStreamType(),
                StreamType::getName,
                streamTypeName -> {
                    if (metaServiceProvider.get().getTypes().contains(streamTypeName)) {
                        feedDoc.setStreamType(streamTypeName);
                    } else {
                        importState.addMessage(
                                Severity.ERROR,
                                LogUtil.message(
                                        """
                                                Feed '{}' has stream type '{}' which is not a valid stream type. \
                                                Custom stream types can be added using the property \
                                                'stroom.data.meta.metaTypes'.""",
                                        feedDoc.getName(),
                                        streamTypeName));
                    }
                });
    }
}
