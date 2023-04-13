package stroom.index.shared;

public class AllPartition implements Partition {

    public static final AllPartition INSTANCE = new AllPartition();

    private static final String ALL = "all";

    @Override
    public String getLabel() {
        return ALL;
    }

    @Override
    public Long getPartitionFromTime() {
        return null;
    }

    @Override
    public Long getPartitionToTime() {
        return null;
    }
}
