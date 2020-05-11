package stroom.docrefinfo.mock;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.Optional;

public class MockDocRefInfoModule extends AbstractModule {
    @Provides
    DocRefInfoService docRefInfoService() {
        return new DocRefInfoService() {
            @Override
            public Optional<DocRefInfo> info(final DocRef docRef) {
                return Optional.empty();
            }

            @Override
            public Optional<String> name(final DocRef docRef) {
                return Optional.empty();
            }
        };
    }
}
