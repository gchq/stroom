package stroom.index.shared;

public interface Partition {
    String getLabel();

    Long getPartitionFromTime();

    Long getPartitionToTime();
}
