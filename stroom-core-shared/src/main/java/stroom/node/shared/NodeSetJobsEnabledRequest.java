package stroom.node.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class NodeSetJobsEnabledRequest {

    @JsonProperty
    private boolean enabled;

    /*
    List of job names to include in this operation
     */
    @JsonProperty
    private Set<String> includeJobs = Collections.emptySet();

    /*
    List of job names to exclude from this operation
     */
    @JsonProperty
    private Set<String> excludeJobs = Collections.emptySet();

    public NodeSetJobsEnabledRequest() {

    }

    @JsonCreator
    public NodeSetJobsEnabledRequest(
            @JsonProperty("enabled") final boolean enabled,
            @JsonProperty("includeJobs") final Set<String> includeJobs,
            @JsonProperty("excludeJobs") final Set<String> excludeJobs) {

        this.enabled = enabled;
        this.includeJobs = includeJobs;
        this.excludeJobs = excludeJobs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getIncludeJobs() {
        return includeJobs;
    }

    public void setIncludeJobs(final Set<String> includeJobs) {
        this.includeJobs = includeJobs;
    }

    public Set<String> getExcludeJobs() {
        return excludeJobs;
    }

    public void setExcludeJobs(final Set<String> excludeJobs) {
        this.excludeJobs = excludeJobs;
    }
}
