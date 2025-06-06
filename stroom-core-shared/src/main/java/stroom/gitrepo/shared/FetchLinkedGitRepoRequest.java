package stroom.gitrepo.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Set;

@JsonPropertyOrder({"gitRepo", "loadedGitRepos"})
@JsonInclude(Include.NON_NULL)
public class FetchLinkedGitRepoRequest {

    @JsonProperty
    private DocRef gitRepo;
    @JsonProperty
    private Set<DocRef> loadedGitRepos;

    public FetchLinkedGitRepoRequest() {
    }

    @JsonCreator
    public FetchLinkedGitRepoRequest(@JsonProperty("gitRepo") final DocRef gitRepo,
                                     @JsonProperty("loadedGitRepos") final Set<DocRef> loadedGitRepos) {
        this.gitRepo = gitRepo;
        this.loadedGitRepos = loadedGitRepos;
    }

    public DocRef getGitRepo() {
        return gitRepo;
    }

    public void setGitRepo(final DocRef gitRepo) {
        this.gitRepo = gitRepo;
    }

    public Set<DocRef> getLoadedGitRepos() {
        return loadedGitRepos;
    }

    public void setLoadedGitRepos(final Set<DocRef> loadedGitRepos) {
        this.loadedGitRepos = loadedGitRepos;
    }
}
