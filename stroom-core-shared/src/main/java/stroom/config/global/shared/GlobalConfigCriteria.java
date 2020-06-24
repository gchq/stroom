package stroom.config.global.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GlobalConfigCriteria extends BaseCriteria {

    @JsonProperty
    private String quickFilterInput;

    @JsonCreator
    public GlobalConfigCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                @JsonProperty("sortList") final List<Sort> sortList,
                                @JsonProperty("quickFilterInput") final String quickFilterInput) {
        super(pageRequest, sortList);
        this.quickFilterInput = quickFilterInput;
    }

    public GlobalConfigCriteria() {
        this(null);
    }

    public GlobalConfigCriteria(final String quickFilterInput) {
        super(new PageRequest(0L, Integer.MAX_VALUE), new ArrayList<>());
        this.quickFilterInput = quickFilterInput;
    }

    public String getQuickFilterInput() {
        return quickFilterInput;
    }

    public void setQuickFilterInput(final String quickFilterInput) {
        this.quickFilterInput = quickFilterInput;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final GlobalConfigCriteria that = (GlobalConfigCriteria) o;
        return Objects.equals(quickFilterInput, that.quickFilterInput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), quickFilterInput);
    }

    @Override
    public String toString() {
        return "GlobalConfigCriteria{" +
                "quickFilterInput='" + quickFilterInput + '\'' +
                '}';
    }
}
