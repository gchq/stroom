package stroom.util.shared;

import java.util.Optional;

public interface HasFetchByUuid<T> {

    Optional<T> fetchByUuid(final String uuid);
}
