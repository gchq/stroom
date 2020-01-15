package stroom.meta.api;

import java.util.List;

public interface PhysicalDelete {
    void cleanup(List<Long> idList);
}
