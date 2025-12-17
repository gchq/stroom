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

import stroom.docs.shared.Description;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Resty-GWT is restricted to one parameter. So to send more than on parameter
 * we need to package them up in a DTO. This DTO serves the button that requests
 * a Git Push to Remote.
 */
@Description(
        "Holds all the data for a Git push to remote repository.")
@JsonPropertyOrder({
        "gitRepoDoc",
        "commitMessage"})
@JsonInclude(Include.NON_NULL)
public class GitRepoPushDto {

    /** The document that holds the Git settings for the push */
    @JsonProperty
    private GitRepoDoc gitRepoDoc;

    /** The commit message */
    @JsonProperty
    private String commitMessage;

    /**
     * Default constructor.
     * Everything initialised to null.
     */
    @SuppressWarnings("unused")
    public GitRepoPushDto() {
        // No code
    }

    /**
     * Initialising constructor.
     * @param gitRepoDoc The object that getGitRepoDoc() should return.
     * @param commitMessage The object that getCommitMessage() should return.
     */
    @JsonCreator
    public GitRepoPushDto(@JsonProperty("gitRepoDoc") final GitRepoDoc gitRepoDoc,
                          @JsonProperty("commitMessage") final String commitMessage) {
        this.gitRepoDoc = gitRepoDoc;
        this.commitMessage = commitMessage;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GitRepoPushDto that = (GitRepoPushDto) o;
        return Objects.equals(gitRepoDoc, that.gitRepoDoc) && Objects.equals(commitMessage,
                that.commitMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gitRepoDoc, commitMessage);
    }

    public GitRepoDoc getGitRepoDoc() {
        return gitRepoDoc;
    }

    @SuppressWarnings("unused")
    public void setGitRepoDoc(final GitRepoDoc gitRepoDoc) {
        this.gitRepoDoc = gitRepoDoc;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    @SuppressWarnings("unused")
    public void setCommitMessage(final String commitMessage) {
        this.commitMessage = commitMessage;
    }

    @Override
    public String toString() {
        return "GitRepoPushDto: {" +
               "gitRepoDoc=" + gitRepoDoc +
               ", commitMessage='" + commitMessage + '\'' +
               '}';
    }
}
