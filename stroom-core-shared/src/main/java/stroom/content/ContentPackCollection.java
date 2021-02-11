package stroom.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ContentPackCollection {

    @JsonProperty
    private final List<ContentPack> contentPacks;

    @JsonCreator
    public ContentPackCollection(@JsonProperty("contentPacks") final List<ContentPack> contentPacks) {
        this.contentPacks = contentPacks;
    }

    public List<ContentPack> getContentPacks() {
        return contentPacks;
    }
}
