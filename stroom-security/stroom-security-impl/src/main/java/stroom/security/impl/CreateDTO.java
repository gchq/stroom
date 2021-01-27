package stroom.security.impl;

public class CreateDTO {
    private final String name;
    private final Boolean group;

    public CreateDTO(final String name,
                     final Boolean group) {
        this.name = name;
        this.group = group;
    }

    public String getName() {
        return name;
    }

    public Boolean getGroup() {
        return group;
    }

    @Override
    public String toString() {
        return "CreateDTO{" +
                "name='" + name + '\'' +
                ", group=" + group +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {
        private String name;
        private Boolean group;

        private Builder() {
        }

        private Builder(final CreateDTO createDTO) {
            name = createDTO.name;
            group = createDTO.group;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder group(final Boolean group) {
            this.group = group;
            return this;
        }

        public CreateDTO build() {
            return new CreateDTO(name, group);
        }
    }
}
