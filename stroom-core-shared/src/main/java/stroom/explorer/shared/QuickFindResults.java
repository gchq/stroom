package stroom.explorer.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class QuickFindResults {

    @JsonProperty
    private final List<QuickFindResult> quickFindResults;

    @JsonCreator
    public QuickFindResults(@JsonProperty("quickFindResults") final List<QuickFindResult> quickFindResults) {
        this.quickFindResults = quickFindResults;
    }

    public List<QuickFindResult> getQuickFindResults() {
        return quickFindResults;
    }

    @Override
    public String toString() {
        return "QuickFindResults{" +
                "quickFindResults=" + quickFindResults +
                '}';
    }
   

}
