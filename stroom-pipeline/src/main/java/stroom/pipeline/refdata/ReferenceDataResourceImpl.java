package stroom.pipeline.refdata;

import stroom.pipeline.refdata.store.RefStoreEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;

public class ReferenceDataResourceImpl implements ReferenceDataResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataResourceImpl.class);

    private final Provider<ReferenceDataService> referenceDataServiceProvider;

    @Inject
    public ReferenceDataResourceImpl(final Provider<ReferenceDataService> referenceDataServiceProvider) {
        this.referenceDataServiceProvider = referenceDataServiceProvider;
    }

    @Override
    public List<RefStoreEntry> entries(final Integer limit) {
        return referenceDataServiceProvider.get()
                .entries(limit != null ? limit : 100);
    }

    @Override
    public String lookup(final RefDataLookupRequest refDataLookupRequest) {
        return referenceDataServiceProvider.get()
                .lookup(refDataLookupRequest);
    }
}
