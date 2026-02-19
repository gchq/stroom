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

package stroom.gitrepo.shared;

import stroom.contentstore.shared.ContentStoreMetadata;
import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description(
        "Contains the configuration for a connection to a Git repository.")
@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "contentStoreMeta",
        "contentStoreContentPackId",
        "url",
        "credentialName",
        "branch",
        "path",
        "commit",
        "autoPush"
})
@JsonInclude(Include.NON_NULL)
public class GitRepoDoc extends AbstractDoc {

    public static final String TYPE = "GitRepo";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.GIT_REPO_DOCUMENT_TYPE;

    /**
     * If this is from a content store then this holds
     * the metadata about that content store. Otherwise
     * contentStoreMeta is null.
     */
    @JsonProperty
    private final ContentStoreMetadata contentStoreMetadata;

    /**
     * If this is from a content store then this holds
     * the ID of this content pack. Otherwise contentStoreContentPackId
     * is null.
     */
    @JsonProperty
    private final String contentStoreContentPackId;

    @JsonProperty
    private final String description;

    @JsonProperty
    private final String url;

    @JsonProperty
    private final String credentialName;

    @JsonProperty
    private final String branch;

    @JsonProperty
    private final String path;

    @JsonProperty
    private final String commit;

    @JsonProperty
    private final Boolean autoPush;

    @JsonCreator
    public GitRepoDoc(@JsonProperty("uuid") final String uuid,
                      @JsonProperty("name") final String name,
                      @JsonProperty("version") final String version,
                      @JsonProperty("createTimeMs") final Long createTimeMs,
                      @JsonProperty("updateTimeMs") final Long updateTimeMs,
                      @JsonProperty("createUser") final String createUser,
                      @JsonProperty("updateUser") final String updateUser,
                      @JsonProperty("description") final String description,
                      @JsonProperty("contentStoreMetadata") final ContentStoreMetadata contentStoreMetadata,
                      @JsonProperty("contentStoreContentPackId") final String contentStoreContentPackId,
                      @JsonProperty("url") final String url,
                      @JsonProperty("credentialName") final String credentialName,
                      @JsonProperty("branch") final String branch,
                      @JsonProperty("path") final String path,
                      @JsonProperty("commit") final String commit,
                      @JsonProperty("autoPush") final Boolean autoPush) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;

        // Content Pack stuff, if any
        this.contentStoreMetadata = contentStoreMetadata;
        this.contentStoreContentPackId = contentStoreContentPackId;

        // Git settings
        this.url = NullSafe.requireNonNullElse(url, "");
        this.credentialName = NullSafe.requireNonNullElse(credentialName, "");
        this.branch = NullSafe.requireNonNullElse(branch, "");
        this.path = NullSafe.requireNonNullElse(path, "");
        this.commit = NullSafe.requireNonNullElse(commit, "");
        this.autoPush = NullSafe.requireNonNullElse(autoPush, Boolean.FALSE);
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final GitRepoDoc that = (GitRepoDoc) o;
        return Objects.equals(description, that.description)
               && Objects.equals(contentStoreMetadata, that.contentStoreMetadata)
               && Objects.equals(contentStoreContentPackId, that.contentStoreContentPackId)
               && Objects.equals(url, that.url)
               && Objects.equals(credentialName, that.credentialName)
               && Objects.equals(branch, that.branch)
               && Objects.equals(path, that.path)
               && Objects.equals(commit, that.commit)
               && Objects.equals(autoPush, that.autoPush);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                description,
                contentStoreMetadata,
                contentStoreContentPackId,
                url,
                credentialName,
                branch,
                path,
                commit,
                autoPush);
    }

    public String getDescription() {
        return description;
    }

    /**
     * @return The metadata associated with the content store, if this is a content pack.
     * If not a content pack then this returns null.
     */
    public ContentStoreMetadata getContentStoreMetadata() {
        return this.contentStoreMetadata;
    }

    /**
     * @return the ID associated with the content pack this was derived
     * from, or null if not derived from a content pack.
     */
    public String getContentStoreContentPackId() {
        return this.contentStoreContentPackId;
    }

    public String getUrl() {
        return this.url;
    }

    public String getCredentialName() {
        return credentialName;
    }

    /**
     * @return true if this GitRepoDoc needs credentials to push to Git. false if not.
     */
    public boolean needsCredentials() {
        return credentialName != null && !credentialName.isBlank();
    }

    public String getBranch() {
        return branch;
    }

    public String getPath() {
        return path;
    }

    public String getCommit() {
        return commit;
    }

    public Boolean isAutoPush() {
        return autoPush;
    }

    /**
     * Returns debugging info about the Doc.
     */
    @Override
    public String toString() {
        return "GitRepoDoc: {\n  "
               + this.getName() + ",\n  "
               + description + ",\n  "
               + contentStoreMetadata + ",\n"
               + contentStoreContentPackId + ",\n"
               + url + ",\n  "
               + credentialName + ",\n  "
               + branch + "\n  "
               + path + "\n  "
               + commit + "\n  "
               + autoPush + "\n}";
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractDocBuilder<GitRepoDoc, Builder> {

        private String description = "";
        private ContentStoreMetadata contentStoreMetadata;
        private String contentStoreContentPackId;
        private String url = "";
        private String credentialName = "";
        private String branch = "";
        private String path = "";
        private String commit = "";
        private Boolean autoPush = Boolean.FALSE;

        private Builder() {
        }

        private Builder(final GitRepoDoc gitRepoDoc) {
            super(gitRepoDoc);
            this.description = gitRepoDoc.description;
            this.contentStoreMetadata = gitRepoDoc.contentStoreMetadata;
            this.contentStoreContentPackId = gitRepoDoc.contentStoreContentPackId;
            this.url = gitRepoDoc.url;
            this.credentialName = gitRepoDoc.credentialName;
            this.branch = gitRepoDoc.branch;
            this.path = gitRepoDoc.path;
            this.commit = gitRepoDoc.commit;
            this.autoPush = gitRepoDoc.autoPush;
        }

        public Builder contentStoreMetadata(final ContentStoreMetadata contentStoreMetadata) {
            this.contentStoreMetadata = contentStoreMetadata;
            return self();
        }

        public Builder contentStoreContentPackId(final String contentStoreContentPackId) {
            this.contentStoreContentPackId = contentStoreContentPackId;
            return self();
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder url(final String url) {
            this.url = url;
            return self();
        }

        public Builder credentialName(final String credentialName) {
            this.credentialName = credentialName;
            return self();
        }

        public Builder branch(final String branch) {
            this.branch = branch;
            return self();
        }

        public Builder path(final String path) {
            this.path = path;
            return self();
        }

        public Builder commit(final String commit) {
            this.commit = commit;
            return self();
        }

        public Builder autoPush(final Boolean autoPush) {
            this.autoPush = autoPush;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public GitRepoDoc build() {
            return new GitRepoDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    contentStoreMetadata,
                    contentStoreContentPackId,
                    url,
                    credentialName,
                    branch,
                    path,
                    commit,
                    autoPush);
        }
    }
}
