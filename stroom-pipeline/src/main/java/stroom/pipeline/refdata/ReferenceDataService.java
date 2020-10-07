package stroom.pipeline.refdata;

import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.searchable.api.Searchable;

import java.util.List;

public interface ReferenceDataService extends Searchable {

    List<RefStoreEntry> entries(final int limit);

    String lookup(final RefDataLookupRequest refDataLookupRequest);
}
