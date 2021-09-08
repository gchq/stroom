package stroom.pipeline.refdata;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.pipeline.refdata.store.RefStreamProcessingInfo;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
public class ReferenceDataResourceImpl implements ReferenceDataResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataResourceImpl.class);

    private final Provider<ReferenceDataService> referenceDataServiceProvider;

    @Inject
    public ReferenceDataResourceImpl(final Provider<ReferenceDataService> referenceDataServiceProvider) {
        this.referenceDataServiceProvider = referenceDataServiceProvider;
    }

    @AutoLogged(OperationType.VIEW)
    @Override
    public List<RefStoreEntry> entries(final Integer limit,
                                       final Long refStreamId,
                                       final String mapName) {
        return referenceDataServiceProvider.get()
                .entries(limit != null
                                ? limit
                                : 100,
                        refStreamId,
                        mapName);
    }

    @Override
    public List<RefStreamProcessingInfo> refStreamInfo(final Integer limit,
                                                       final Long refStreamId,
                                                       final String mapName) {
        return referenceDataServiceProvider.get()
                .refStreamInfo(limit != null
                                ? limit
                                : 100,
                        refStreamId,
                        mapName);
    }

    @AutoLogged(OperationType.VIEW)
    @Override
    public String lookup(final RefDataLookupRequest refDataLookupRequest) {
        return referenceDataServiceProvider.get()
                .lookup(refDataLookupRequest);
    }

    @AutoLogged(OperationType.DELETE)
    @Override
    public boolean purge(final String purgeAge) {
        StroomDuration purgeAgeDuration;
        try {
            purgeAgeDuration = StroomDuration.parse(purgeAge);
        } catch (Exception e) {
            throw new IllegalArgumentException(LogUtil.message(
                    "Can't parse purgeAge [{}]", purgeAge), e);
        }
        try {
            referenceDataServiceProvider.get()
                    .purge(purgeAgeDuration);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to purgeAge " + purgeAge, e);
            throw e;
        }
    }

    @AutoLogged(OperationType.DELETE)
    @Override
    public boolean purge(final long refStreamId, final long partNo) {
        try {
            // partNo is one based, partIndex is zero based
            final long partIndex = partNo - 1;
            referenceDataServiceProvider.get()
                    .purge(refStreamId, partIndex);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to purge " + refStreamId + ":" + partNo, e);
            throw e;
        }
    }
}
