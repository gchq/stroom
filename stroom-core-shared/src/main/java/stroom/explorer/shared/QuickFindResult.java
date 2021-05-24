package stroom.explorer.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Collectors;

public class QuickFindResult {

    @JsonProperty
    private final String type;
    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String iconUrl;

    // uuid for each branch starting from root.  If this QuickFindResult is a doc then this list will contain all
    // folders branches before it.  If this is a folder then this list will contain all folder branches before this
    //folder, but not this folder.
    @JsonProperty
    private final List<ExplorerPathPart> explorerPathParts;

    @JsonCreator
    public QuickFindResult(@JsonProperty("type") final String type,
                           @JsonProperty("uuid") final String uuid,
                           @JsonProperty("name") final String name,
                           @JsonProperty("iconUrl") final String iconUrl,
                           @JsonProperty("explorerPathParts") final List<ExplorerPathPart> explorerPathParts) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.iconUrl = iconUrl;
        this.explorerPathParts = explorerPathParts;
    }

    public String getType() {
        return type;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public List<ExplorerPathPart> getExplorerPathParts() {
        return explorerPathParts;
    }

    public String getPathStr() {
        return explorerPathParts.stream()
                .map(ExplorerPathPart::getName)
                .collect(Collectors.joining("/"));
    }

    @Override
    public String toString() {
        return "QuickFindResult{" +
                "type='" + type + '\'' +
                ", uuid='" + uuid + '\'' +
                ", name='" + name + '\'' +
                ", iconUrl='" + iconUrl + '\'' +
                ", explorerPathParts=" + explorerPathParts +
                '}';
    }

    public static class ExplorerPathPart {

        @JsonProperty
        private final String uuid;
        @JsonProperty
        private final String name;

        @JsonCreator
        public ExplorerPathPart(@JsonProperty("uuid") final String uuid,
                                @JsonProperty("name") final String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public String getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "ExplorerPathPart{" +
                    "uuid='" + uuid + '\'' +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
}
