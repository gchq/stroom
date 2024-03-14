package stroom.proxy.repo.dao.lmdb;

public record AggregateKey(long feedId, long createTimeMs, long idSuffix) {

}
