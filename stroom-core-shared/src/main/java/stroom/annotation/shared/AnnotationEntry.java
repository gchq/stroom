package stroom.annotation.shared;

import stroom.util.shared.HasDisplayValue;

public class AnnotationEntry {
    private Long id;
    private Integer version;
    private Long createTime;
    private String createUser;
    private Long updateTime;
    private String updateUser;
    private EntryType entryType;
    private String data;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(final Long createTime) {
        this.createTime = createTime;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(final Long updateTime) {
        this.updateTime = updateTime;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(final EntryType entryType) {
        this.entryType = entryType;
    }

    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public enum EntryType implements HasDisplayValue {
        TITLE("Title", 0),
        COMMENT("Comment", 1),
        STATUS("Status", 2),
        ASSIGNED_TO("Assigned", 3);

        private static EntryType[] values = new EntryType[] {TITLE, COMMENT, STATUS, ASSIGNED_TO};
        public static EntryType fromPrimitive(int index) {
            if (index >= 0 && index < values.length) {
                return values[index];
            }
            return null;
        }

        private final String displayValue;
        private final int primitiveValue;

        EntryType(final String displayValue, int primitiveValue) {
            this.displayValue = displayValue;
            this.primitiveValue = primitiveValue;
        }

        /**
         * @return drop down string value.
         */
        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        public int getPrimitiveValue() {
            return primitiveValue;
        }
    }
}
