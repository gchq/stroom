package stroom.meta.api;

import java.util.Collection;

public interface PhysicalDelete {

    void cleanup(Collection<Long> ids);
}
