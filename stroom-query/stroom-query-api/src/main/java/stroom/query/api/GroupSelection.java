package stroom.query.api;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder({"expandMode", "selectedGroups"})
@JsonInclude(Include.NON_NULL)
public class GroupSelection {
    @JsonProperty
    private final boolean expandMode;
    @JsonProperty
    private final Set<String> selectedGroups;

    public GroupSelection() {
        this(false, new HashSet<>());
    }

    @JsonCreator
    public GroupSelection(@JsonProperty("expandMode") final boolean expandMode,
                          @JsonProperty("selectedGroups") final Set<String> selectedGroups) {
        this.expandMode = expandMode;
        this.selectedGroups = NullSafe.firstNonNull(selectedGroups).orElse(new HashSet<>());
    }

    public boolean isExpandMode() {
        return expandMode;
    }

    public Set<String> getSelectedGroups() {
        return selectedGroups;
    }

    public void add(final String group) {
        selectedGroups.add(group);
    }

    public void remove(final String group) {
        selectedGroups.remove(group);
    }

    public boolean isGroupSelected(final String group) {
        return selectedGroups != null && selectedGroups.contains(group);
    }

    public boolean isGroupOpen(final String group) {
        return (expandMode && !isGroupSelected(group)) || (!expandMode && isGroupSelected(group));
    }

    public boolean hasGroupsSelected() {
        return selectedGroups != null && !selectedGroups.isEmpty();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupSelection that = (GroupSelection) o;
        return expandMode == that.expandMode &&
               Objects.equals(selectedGroups, that.selectedGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expandMode, selectedGroups);
    }

    @Override
    public String toString() {
        return "GroupSelection{" +
               "expandMode=" + expandMode +
               ", selectedGroups=" + selectedGroups +
               '}';
    }
}
