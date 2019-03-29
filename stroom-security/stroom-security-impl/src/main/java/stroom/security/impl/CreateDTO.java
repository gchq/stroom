package stroom.security.impl;

public class CreateDTO {
    private String name;
    private Boolean isGroup;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getGroup() {
        return isGroup;
    }

    public void setGroup(Boolean group) {
        isGroup = group;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CreateDTO{");
        sb.append("name='").append(name).append('\'');
        sb.append(", group=").append(isGroup);
        sb.append('}');
        return sb.toString();
    }

    public static class Builder {
        private final CreateDTO instance;

        public Builder(final CreateDTO instance) {
            this.instance = instance;
        }

        public Builder() {
            this(new CreateDTO());
        }

        public Builder name(final String value) {
            instance.setName(value);
            return this;
        }

        public Builder group(final Boolean value) {
            instance.setGroup(value);
            return this;
        }

        public CreateDTO build() {
            return instance;
        }
    }
}
