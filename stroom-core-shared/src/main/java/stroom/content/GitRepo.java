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

package stroom.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class GitRepo {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final String uri;
    @JsonProperty
    private final String branch;
    @JsonProperty
    private final String commit;

    @JsonCreator
    public GitRepo(@JsonProperty("name") final String name,
                   @JsonProperty("uri") final String uri,
                   @JsonProperty("branch") final String branch,
                   @JsonProperty("commit") final String commit) {
        this.name = name;
        this.uri = uri;
        this.branch = branch;
        this.commit = commit;
    }

    public String getName() {
        return name;
    }

    public String getUri() {
        return uri;
    }

    public String getBranch() {
        return branch;
    }

    public String getCommit() {
        return commit;
    }

    @Override
    public String toString() {
        return "GitRepo{" +
                "name='" + name + '\'' +
                ", uri='" + uri + '\'' +
                ", branch='" + branch + '\'' +
                ", commit='" + commit + '\'' +
                '}';
    }
}
