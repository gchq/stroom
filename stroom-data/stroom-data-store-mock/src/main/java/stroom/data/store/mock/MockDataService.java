package stroom.data.store.mock;

import stroom.data.shared.DataInfoSection;
import stroom.data.shared.UploadDataRequest;
import stroom.data.store.api.DataService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class MockDataService implements DataService {

    @Override
    public ResourceGeneration download(final FindMetaCriteria criteria) {
        return null;
    }

    @Override
    public ResourceKey upload(final UploadDataRequest request) {
        return null;
    }

    @Override
    public Map<String, String> metaAttributes(final long id) {
        return Map.of();
    }

    @Override
    public List<DataInfoSection> info(final long id) {
        return List.of();
    }

    @Override
    public AbstractFetchDataResult fetch(final FetchDataRequest request) {
        return null;
    }

    @Override
    public Set<String> getChildStreamTypes(final long id, final long partNo) {
        return Set.of();
    }
}
