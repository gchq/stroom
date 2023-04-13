package stroom.proxy.repo;

import java.util.List;

public record SourceItems(Source source, List<Item> list) {

    public record Source(long id,
                         long fileStoreId) {

    }

    public record Item(long id,
                       String name,
                       long feedId,
                       Long aggregateId,
                       long totalByteSize,
                       String extensions) {

    }
}


