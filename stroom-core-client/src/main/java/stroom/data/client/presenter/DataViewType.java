package stroom.data.client.presenter;

public enum DataViewType {
    PREVIEW("preview"),
    SOURCE("source");

    private final String name;

    DataViewType(final String name) {
        this.name = name;
    }

    public static DataViewType parse(final String name) {

        for (final DataViewType dataViewType : DataViewType.values()) {
            if (dataViewType.name.equals(name)) {
                return dataViewType;
            }
        }
        throw new IllegalArgumentException("Unknown dataViewType name" + name);
    }

    public String getName() {
        return name;
    }
}
