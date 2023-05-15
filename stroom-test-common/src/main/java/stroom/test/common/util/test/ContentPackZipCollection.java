package stroom.test.common.util.test;

import stroom.content.ContentPack;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ContentPackZipCollection {

    @JsonProperty
    private final List<ContentPackZip> contentPacks;

    @JsonCreator
    public ContentPackZipCollection(@JsonProperty("contentPacks") final List<ContentPackZip> contentPacks) {
        this.contentPacks = contentPacks;
    }

    public List<ContentPackZip> getContentPacks() {
        return contentPacks;
    }
}
