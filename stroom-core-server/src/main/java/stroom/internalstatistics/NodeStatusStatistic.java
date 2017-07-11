package stroom.internalstatistics;

public enum NodeStatusStatistic {
    CPU("CPU"),
    MEMORY("Memory"),
    EPS("EPS");

    private final String statisticName;

    NodeStatusStatistic(final String statisticName) {
        this.statisticName = statisticName;
    }

    public String getStatisticName() {
        return statisticName;
    }
}
