/*
 * Copyright 2016 Crown Copyright
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
import stroom.docstore.shared.Doc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description(
        "Contains the configuration for a connection to a GIT repository.")
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
        "username",
        "password",
        "branch",
        "path",
        "commit",
        "gitRemoteCommitName",
        "autoPush"
})
@JsonInclude(Include.NON_NULL)
public class GitRepoDoc extends Doc {

    public static final String TYPE = "GitRepo";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.GIT_REPO_DOCUMENT_TYPE;

    /**
     * If this is from a content store then this holds
     * the metadata about that content store. Otherwise
     * contentStoreMeta is null.
     */
    @JsonProperty
    private ContentStoreMetadata contentStoreMeta;

    /**
     * If this is from a content store then this holds
     * the ID of this content pack. Otherwise contentStoreContentPackId
     * is null.
     */
    @JsonProperty
    private String contentStoreContentPackId;

    @JsonProperty
    private String description = "";

    @JsonProperty
    private String url = "";

    @JsonProperty
    private String username = "";

    @JsonProperty
    private String password = "";

    @JsonProperty
    private String branch = "";

    @JsonProperty
    private String path = "";

    @JsonProperty
    private String commit = "";

    /**
     * Name of the commit that is currently checked out
     * and imported.
     * May be null if nothing has been pulled yet.
     */
    @JsonProperty
    private String gitRemoteCommitName = null;

    @JsonProperty
    private Boolean autoPush = Boolean.FALSE;

    /**
     * No-args constructor; needed by some code.
     */
    public GitRepoDoc() {
        // No code
    }

    @JsonCreator
    public GitRepoDoc(@JsonProperty("type") final String type,
                      @JsonProperty("uuid") final String uuid,
                      @JsonProperty("name") final String name,
                      @JsonProperty("version") final String version,
                      @JsonProperty("createTimeMs") final Long createTimeMs,
                      @JsonProperty("updateTimeMs") final Long updateTimeMs,
                      @JsonProperty("createUser") final String createUser,
                      @JsonProperty("updateUser") final String updateUser,
                      @JsonProperty("description") final String description,
                      @JsonProperty("contentStoreMeta") final ContentStoreMetadata contentStoreMeta,
                      @JsonProperty("contentStoreContentPackId") final String contentStoreContentPackId,
                      @JsonProperty("url") final String url,
                      @JsonProperty("username") final String username,
                      @JsonProperty("password") final String password,
                      @JsonProperty("branch") final String branch,
                      @JsonProperty("path") final String path,
                      @JsonProperty("commit") final String commit,
                      @JsonProperty("gitRemoteCommitName") final String gitRemoteCommitName,
                      @JsonProperty("autoPush") final Boolean autoPush) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;

        // Content Pack stuff, if any
        this.contentStoreMeta = contentStoreMeta;
        this.contentStoreContentPackId = contentStoreContentPackId;

        // Git settings
        this.url = url;
        this.username = username;
        this.password = password;
        this.branch = branch;
        this.path = path;
        this.commit = commit;
        this.gitRemoteCommitName = gitRemoteCommitName;
        this.autoPush = autoPush;

        // Make sure none of the settings are null
        if (this.url == null) {
            this.url = "";
        }
        if (this.username == null) {
            this.username = "";
        }
        if (this.password == null) {
            this.password = "";
        }
        if (this.branch == null) {
            this.branch = "";
        }
        if (this.path == null) {
            this.path = "";
        }
        if (this.commit == null) {
            this.commit = "";
        }
        if (this.autoPush == null) {
            this.autoPush = Boolean.FALSE;
        }
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
               && Objects.equals(contentStoreMeta, that.contentStoreMeta)
               && Objects.equals(contentStoreContentPackId, that.contentStoreContentPackId)
               && Objects.equals(url, that.url)
               && Objects.equals(username, that.username)
               && Objects.equals(password, that.password)
               && Objects.equals(branch, that.branch)
               && Objects.equals(path, that.path)
               && Objects.equals(commit, that.commit)
               && Objects.equals(gitRemoteCommitName, that.gitRemoteCommitName)
               && Objects.equals(autoPush, that.autoPush);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                description,
                contentStoreMeta,
                contentStoreContentPackId,
                url,
                username,
                password,
                branch,
                path,
                commit,
                gitRemoteCommitName,
                autoPush);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * @return The metadata associated with the content store, if this is a content pack.
     * If not a content pack then this returns null.
     */
    public ContentStoreMetadata getContentStoreMetadata() {
        return this.contentStoreMeta;
    }

    /**
     * Sets the metadata associated with the content store. Set to null if not
     * a content store.
     */
    public void setContentStoreMetadata(final ContentStoreMetadata meta) {
        this.contentStoreMeta = meta;
    }

    /**
     * @return the ID associated with the content pack this was derived
     * from, or null if not derived from a content pack.
     */
    public String getContentStoreContentPackId() {
        return this.contentStoreContentPackId;
    }

    /**
     * Sets the ID associated with the content pack. Set to null if not
     * derived from a content store content pack.
     * @param id The Content Pack ID.
     */
    public void setContentStoreContentPackId(final String id) {
        this.contentStoreContentPackId = id;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(final String branch) {
        this.branch = branch;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public String getCommit() {
        return commit;
    }

    public void setCommit(final String commit) {
        this.commit = commit;
    }

    public String getGitRemoteCommitName() {
        return gitRemoteCommitName;
    }

    public void setGitRemoteCommitName(final String gitRemoteCommitName) {
        this.gitRemoteCommitName = gitRemoteCommitName;
    }

    public Boolean isAutoPush() {
        return autoPush;
    }

    public void setAutoPush(final Boolean autoPush) {
        // Objects.requireNonNullElse() not defined for GWT
        if (autoPush == null) {
            this.autoPush = Boolean.FALSE;
        } else {
            this.autoPush = autoPush;
        }
    }

    /**
     * Returns debugging info about the Doc.
     */
    @Override
    public String toString() {
        return "GitRepoDoc: {\n  "
                + this.getName() + ",\n  "
                + description + ",\n  "
                + contentStoreMeta + ",\n"
                + contentStoreContentPackId + ",\n"
                + url + ",\n  "
                + username + ",\n  "
                + branch + "\n  "
                + path + "\n  "
                + commit +"\n  "
                + gitRemoteCommitName + "\n  "
                + autoPush + "\n}";
    }
}
