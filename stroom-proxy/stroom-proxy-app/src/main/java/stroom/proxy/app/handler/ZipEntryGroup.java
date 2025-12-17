/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app.handler;

import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.FeedKey.FeedKeyInterner;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
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

    // Hold the feed+type as a FeedKey to try to reduce mem usage
    // but serialise as individual feedName and typeName
    @JsonIgnore
    private FeedKey feedKey;

    private Entry manifestEntry;
    private Entry metaEntry;
    private Entry contextEntry;
    private Entry dataEntry;

    public ZipEntryGroup(final FeedKey feedKey) {
        this.feedKey = feedKey;
    }

    @JsonCreator
    public ZipEntryGroup(@JsonProperty("feedName") final String feedName,
                         @JsonProperty("typeName") final String typeName,
                         @JsonProperty("manifestEntry") final Entry manifestEntry,
                         @JsonProperty("metaEntry") final Entry metaEntry,
                         @JsonProperty("contextEntry") final Entry contextEntry,
                         @JsonProperty("dataEntry") final Entry dataEntry) {
        this.feedKey = FeedKey.of(feedName, typeName);
        this.manifestEntry = manifestEntry;
        this.metaEntry = metaEntry;
        this.contextEntry = contextEntry;
        this.dataEntry = dataEntry;
    }

    public ZipEntryGroup(final FeedKey feedKey,
                         final Entry manifestEntry,
                         final Entry metaEntry,
                         final Entry contextEntry,
                         final Entry dataEntry) {
        this.feedKey = feedKey;
        this.manifestEntry = manifestEntry;
        this.metaEntry = metaEntry;
        this.contextEntry = contextEntry;
        this.dataEntry = dataEntry;
    }

    @JsonProperty("feedName")
    public String getFeedName() {
        return NullSafe.get(feedKey, FeedKey::feed);
    }

    @JsonProperty("typeName")
    public String getTypeName() {
        return NullSafe.get(feedKey, FeedKey::type);
    }

    @JsonIgnore
    public FeedKey getFeedKey() {
        return feedKey;
    }

    @JsonIgnore
    public void setFeedKey(final FeedKey feedKey) {
        this.feedKey = feedKey;
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

    public static List<ZipEntryGroup> read(final Path entriesFile) {
        final FeedKeyInterner interner = FeedKey.createInterner();
        return read(entriesFile, interner);
    }

    public static List<ZipEntryGroup> read(final Path entriesFile,
                                           final FeedKeyInterner feedKeyInterner) {
        try (final Stream<String> linesStream = Files.lines(entriesFile)) {
            return linesStream
                    .filter(Predicate.not(String::isBlank))
                    .map(line ->
                            read(line, feedKeyInterner))
                    .toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ZipEntryGroup read(final String line,
                                     final FeedKeyInterner feedKeyInterner) {
        final ZipEntryGroup zipEntryGroup = read(line);
        // Use an interned FeedKey to save on mem use
        feedKeyInterner.consumeInterned(zipEntryGroup.feedKey, zipEntryGroup::setFeedKey);
        return zipEntryGroup;
    }

    private static ZipEntryGroup read(final String line) {
        return JsonUtil.readValue(line, ZipEntryGroup.class);
    }

    @JsonIgnore
    public long getTotalUncompressedSize() {
        return getUncompressedSize(manifestEntry)
               + getUncompressedSize(metaEntry)
               + getUncompressedSize(contextEntry)
               + getUncompressedSize(dataEntry);
    }

    private static long getUncompressedSize(final Entry entry) {
        return entry != null
                ? entry.uncompressedSize
                : 0;
    }

    @Override
    public String toString() {
        return "ZipEntryGroup{" +
               "feedKey='" + feedKey + '\'' +
               ", manifestEntry=" + entryToStr(manifestEntry) +
               ", metaEntry=" + entryToStr(metaEntry) +
               ", contextEntry=" + entryToStr(contextEntry) +
               ", dataEntry=" + entryToStr(dataEntry) +
               '}';
    }

    private String entryToStr(final Entry entry) {
        if (entry != null) {
            return entry.getName()
                   + "("
                   + ModelStringUtil.formatCsv(entry.getUncompressedSize())
                   + ")";
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
