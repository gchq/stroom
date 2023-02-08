package stroom.meta.shared;

import java.time.Instant;
import java.util.Optional;

public interface SimpleMeta {

    long getId();

    String getTypeName();

    String getFeedName();

    long getCreateMs();

    Instant getCreateTime();

    Long getStatusMs();

    Optional<Instant> getStatusTime();
}
