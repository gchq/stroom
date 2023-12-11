package stroom.proxy.repo.dao.lmdb;

public record RepoSourceItemPart(
        long fileStoreId,
        long feedId,
        String name,
        long aggregateId,
        long totalByteSize,
        String extensions) {

}
