/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// What the user sees in the tab that is active when they send a question, unsaved changes included: the document
/// shown, its text parts as edited, and the search requests behind any tables of results it shows. It is sent with
/// the question in full only when it has changed since it was last sent to the chat (its hash differs); otherwise
/// only its hash is sent, and the server uses the copy it already holds.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiTabSnapshot {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final String tabLabel;
    @JsonProperty
    private final String innerTab;
    @JsonProperty
    private final boolean dirty;
    @JsonProperty
    private final List<AiTabSnapshotPart> parts;
    @JsonProperty
    private final List<AiTabSearch> searches;
    @JsonProperty
    private final String note;
    @JsonProperty
    private final AiSteppingContext stepping;
    @JsonProperty
    private final String hash;

    /// @param docRef   The document shown in the tab.
    /// @param tabLabel The tab's name, as shown, e.g. "Event translation".
    /// @param innerTab The name of the tab selected within it, e.g. "XSLT", or null.
    /// @param dirty    True if the tab has unsaved changes.
    /// @param parts    Its text parts, as the user sees them, or null if only the hash is sent.
    /// @param searches The search requests behind the tables of results it shows, or null if only the hash is
    ///                 sent.
    /// @param note     Something the model should know about what is shared, e.g. that a form's unsaved edits
    ///                 aren't, or null.
    /// @param hash     A hash of everything above, to tell whether it has changed since it was last sent.
    public AiTabSnapshot(final DocRef docRef,
                         final String tabLabel,
                         final String innerTab,
                         final Boolean dirty,
                         final List<AiTabSnapshotPart> parts,
                         final List<AiTabSearch> searches,
                         final String note,
                         final String hash) {
        this(docRef, tabLabel, innerTab, dirty, parts, searches, note, null, hash);
    }

    /// @param docRef   The document shown in the tab.
    /// @param tabLabel The tab's name, as shown, e.g. "Event translation".
    /// @param innerTab The name of the tab selected within it, e.g. "XSLT", or null.
    /// @param dirty    True if the tab has unsaved changes.
    /// @param parts    Its text parts, as the user sees them, or null if only the hash is sent.
    /// @param searches The search requests behind the tables of results it shows, or null if only the hash is
    ///                 sent.
    /// @param note     Something the model should know about what is shared, e.g. that a form's unsaved edits
    ///                 aren't, or null.
    /// @param stepping What a pipeline's stepping screen shows, if the tab is stepping, or null.
    /// @param hash     A hash of everything above, to tell whether it has changed since it was last sent.
    @JsonCreator
    public AiTabSnapshot(@JsonProperty("docRef") final DocRef docRef,
                         @JsonProperty("tabLabel") final String tabLabel,
                         @JsonProperty("innerTab") final String innerTab,
                         @JsonProperty("dirty") final Boolean dirty,
                         @JsonProperty("parts") final List<AiTabSnapshotPart> parts,
                         @JsonProperty("searches") final List<AiTabSearch> searches,
                         @JsonProperty("note") final String note,
                         @JsonProperty("stepping") final AiSteppingContext stepping,
                         @JsonProperty("hash") final String hash) {
        this.docRef = docRef;
        this.tabLabel = tabLabel;
        this.innerTab = innerTab;
        this.dirty = dirty != null && dirty;
        this.parts = parts == null
                ? null
                : new ArrayList<>(parts);
        this.searches = searches == null
                ? null
                : new ArrayList<>(searches);
        this.note = note;
        this.stepping = stepping;
        this.hash = hash;
    }

    /// @return The document shown in the tab.
    public DocRef getDocRef() {
        return docRef;
    }

    /// @return The tab's name, as shown.
    public String getTabLabel() {
        return tabLabel;
    }

    /// @return The name of the tab selected within it, or null.
    public String getInnerTab() {
        return innerTab;
    }

    /// @return True if the tab has unsaved changes.
    public boolean isDirty() {
        return dirty;
    }

    /// @return Its text parts, as the user sees them, or null if only the hash is sent.
    public List<AiTabSnapshotPart> getParts() {
        return parts;
    }

    /// @return The search requests behind the tables of results it shows, or null if only the hash is sent.
    public List<AiTabSearch> getSearches() {
        return searches;
    }

    /// @return Something the model should know about what is shared, or null.
    public String getNote() {
        return note;
    }

    /// @return What a pipeline's stepping screen shows, if the tab is stepping, or null.
    public AiSteppingContext getStepping() {
        return stepping;
    }

    /// @return A hash of everything else, to tell whether it has changed since it was last sent.
    public String getHash() {
        return hash;
    }

    /// @return True if only the hash is sent, as the server already holds a copy with it.
    @JsonIgnore
    public boolean isHashOnly() {
        return parts == null && searches == null && stepping == null;
    }

    /// @return This snapshot as sent when it hasn't changed since it was last sent: its document, name, dirty
    /// state and hash, without its parts or searches.
    @JsonIgnore
    public AiTabSnapshot hashOnly() {
        return new AiTabSnapshot(docRef, tabLabel, innerTab, dirty, null, null, null, null, hash);
    }

    /// @return This snapshot with the given hash.
    @JsonIgnore
    public AiTabSnapshot withHash(final String hash) {
        return new AiTabSnapshot(docRef, tabLabel, innerTab, dirty, parts, searches, note, stepping, hash);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiTabSnapshot that = (AiTabSnapshot) o;
        return dirty == that.dirty
               && Objects.equals(docRef, that.docRef)
               && Objects.equals(tabLabel, that.tabLabel)
               && Objects.equals(innerTab, that.innerTab)
               && Objects.equals(parts, that.parts)
               && Objects.equals(searches, that.searches)
               && Objects.equals(note, that.note)
               && Objects.equals(stepping, that.stepping)
               && Objects.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, tabLabel, innerTab, dirty, parts, searches, note, stepping, hash);
    }

    @Override
    public String toString() {
        return "AiTabSnapshot{docRef=" + docRef + ", tabLabel='" + tabLabel + "', innerTab='" + innerTab
               + "', dirty=" + dirty + ", parts=" + parts + ", searches=" + searches + ", stepping=" + stepping
               + ", hash='" + hash + "'}";
    }
}
