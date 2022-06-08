package stroom.proxy.repo;

import java.util.List;
import java.util.Map;

public record Items(Map<Source, List<Item>> map) {

    public record Source(long id,
                         long fileStoreId) {

    }

    public record Item(Source repoSource,
                       long id,
                       String name,
                       long feedId,
                       Long aggregateId,
                       long totalByteSize,
                       String extensions) {

    }
}


