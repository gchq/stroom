package stroom.pipeline.refdata;

import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.RefStreamProcessingInfo;
import stroom.searchable.api.Searchable;
import stroom.util.time.StroomDuration;

import java.util.List;

public interface ReferenceDataService extends Searchable {

    List<RefStoreEntry> entries(int limit);

    List<RefStoreEntry> entries(final int limit,
                                final Long refStreamId,
                                final String mapName);

    List<RefStreamProcessingInfo> refStreamInfo(int limit);

    List<RefStreamProcessingInfo> refStreamInfo(final int limit,
                                                final Long refStreamId,
                                                final String mapName);

    String lookup(final RefDataLookupRequest refDataLookupRequest);

    void purge(final StroomDuration stroomDuration);

    void purge(final long refStreamId, final long partNo);
}
