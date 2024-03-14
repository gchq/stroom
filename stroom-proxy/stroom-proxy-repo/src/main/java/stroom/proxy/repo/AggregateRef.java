package stroom.proxy.repo;

public record AggregateRef(
        Long id,
        Long createTime,
        Long feedId,
        Long byteSize,
        Integer items,
        Boolean complete,
        Long newPosition) {

}
