package stroom.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ContentPackCollection {

    @JsonProperty("content_packs")
    private final List<ContentPack> contentPacks;

    @JsonCreator
    public ContentPackCollection(@JsonProperty("content_packs") final List<ContentPack> contentPacks) {
        this.contentPacks = contentPacks;
    }

    public List<ContentPack> getContentPacks() {
        return contentPacks;
    }
}
