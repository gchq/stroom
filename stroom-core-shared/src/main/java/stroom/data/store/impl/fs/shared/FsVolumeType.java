package stroom.data.store.impl.fs.shared;

import stroom.docref.HasDisplayValue;

public enum FsVolumeType implements HasDisplayValue {
    STANDARD(0, "Standard"),
    S3(1, "S3");

    private static final FsVolumeType[] TYPES = {STANDARD, S3};

    private final int id;
    private final String displayValue;

    FsVolumeType(final int id,
                 final String displayValue) {
        this.id = id;
        this.displayValue = displayValue;
    }

    public static FsVolumeType fromId(final int id) {
        if (id < 0 || id > 1) {
            return STANDARD;
        }
        return TYPES[id];
    }

    public int getId() {
        return id;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
