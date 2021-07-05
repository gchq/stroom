package stroom.node.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class NodeSetJobsEnabledRequest {

    @JsonProperty
    private boolean enabled;

    /*
    List of job names to include in this operation
     */
    @JsonProperty
    private String[] includeJobs;

    /*
    List of job names to exclude from this operation
     */
    @JsonProperty
    private String[] excludeJobs;

    public NodeSetJobsEnabledRequest() {

    }

    @JsonCreator
    public NodeSetJobsEnabledRequest(
            @JsonProperty("enabled") final boolean enabled,
            @JsonProperty("includeJobs") final String[] includeJobs,
            @JsonProperty("excludeJobs") final String[] excludeJobs) {

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

    public String[] getIncludeJobs() {
        return includeJobs;
    }

    public void setIncludeJobs(final String[] includeJobs) {
        this.includeJobs = includeJobs;
    }

    public String[] getExcludeJobs() {
        return excludeJobs;
    }

    public void setExcludeJobs(final String[] excludeJobs) {
        this.excludeJobs = excludeJobs;
    }
}
