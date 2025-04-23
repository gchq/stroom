package stroom.proxy.app.handler;

import stroom.proxy.repo.FeedKey;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;
import java.util.stream.Stream;

@JsonPropertyOrder({
        "feedName",
        "typeName",
        "manifestEntry",
        "metaEntry",
        "contextEntry",
        "dataEntry",
})
@JsonInclude(Include.NON_NULL)
public class ZipEntryGroup {

    private String feedName;
    private String typeName;

    private Entry manifestEntry;
    private Entry metaEntry;
    private Entry contextEntry;
    private Entry dataEntry;

    public ZipEntryGroup(final String feedName,
                         final String typeName) {
        this.feedName = feedName;
        this.typeName = typeName;
    }

    @JsonCreator
    public ZipEntryGroup(@JsonProperty("feedName") final String feedName,
                         @JsonProperty("typeName") final String typeName,
                         @JsonProperty("manifestEntry") final Entry manifestEntry,
                         @JsonProperty("metaEntry") final Entry metaEntry,
                         @JsonProperty("contextEntry") final Entry contextEntry,
                         @JsonProperty("dataEntry") final Entry dataEntry) {
        this.feedName = feedName;
        this.typeName = typeName;
        this.manifestEntry = manifestEntry;
        this.metaEntry = metaEntry;
        this.contextEntry = contextEntry;
        this.dataEntry = dataEntry;
    }

    public String getFeedName() {
        return feedName;
    }

    public void setFeedName(final String feedName) {
        this.feedName = feedName;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(final String typeName) {
        this.typeName = typeName;
    }

    @JsonIgnore
    public FeedKey getFeedKey() {
        return new FeedKey(feedName, typeName);
    }

    public Entry getManifestEntry() {
        return manifestEntry;
    }

    public void setManifestEntry(final Entry manifestEntry) {
        this.manifestEntry = manifestEntry;
    }

    public Entry getMetaEntry() {
        return metaEntry;
    }

    public void setMetaEntry(final Entry metaEntry) {
        this.metaEntry = metaEntry;
    }

    public Entry getContextEntry() {
        return contextEntry;
    }

    public void setContextEntry(final Entry contextEntry) {
        this.contextEntry = contextEntry;
    }

    public Entry getDataEntry() {
        return dataEntry;
    }

    public void setDataEntry(final Entry dataEntry) {
        this.dataEntry = dataEntry;
    }

    public void write(final Writer writer) throws IOException {
        final String json = JsonUtil.writeValueAsString(this, false);
        writer.write(json);
        writer.write("\n");
    }

    public static ZipEntryGroup read(final String line) throws IOException {
        return JsonUtil.readValue(line, ZipEntryGroup.class);
    }

    @JsonIgnore
    public long getTotalUncompressedSize() {
        return Stream.of(manifestEntry, metaEntry, contextEntry, dataEntry)
                .filter(Objects::nonNull)
                .mapToLong(Entry::getUncompressedSize)
                .sum();
    }

    @Override
    public String toString() {
        return "ZipEntryGroup{" +
               "feedName='" + feedName + '\'' +
               ", typeName='" + typeName + '\'' +
               ", manifestEntry=" + entryToStr(manifestEntry) +
               ", metaEntry=" + entryToStr(metaEntry) +
               ", contextEntry=" + entryToStr(contextEntry) +
               ", dataEntry=" + entryToStr(dataEntry) +
               '}';
    }

    private String entryToStr(final Entry entry) {
        if (entry != null) {
            return entry.getName() + "(" + entry.getUncompressedSize() + ")";
        } else {
            return "null";
        }
    }

    // --------------------------------------------------------------------------------


    @JsonPropertyOrder({
            "name",
            "uncompressedSize"
    })
    @JsonInclude(Include.NON_NULL)
    public static class Entry {

        private final String name;
        private final long uncompressedSize;

        @JsonCreator
        public Entry(@JsonProperty("name") final String name,
                     @JsonProperty("uncompressedSize") final long uncompressedSize) {
            this.name = name;
            this.uncompressedSize = uncompressedSize;
        }

        public String getName() {
            return name;
        }

        public long getUncompressedSize() {
            return uncompressedSize;
        }

        @Override
        public String toString() {
            return "Entry{" +
                   "name='" + name + '\'' +
                   ", uncompressedSize=" + uncompressedSize +
                   '}';
        }
    }
}
