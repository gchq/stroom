package stroom.dashboard.shared;

import stroom.util.shared.CompareUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Comparator;
import java.util.List;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class StructureElement implements Comparable<StructureElement> {

    public static final Comparator<StructureElement> CASE_INSENSITIVE_TITLE_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(StructureElement::getTitle);

    @JsonProperty
    private final String title;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final List<String> snippets;

    @JsonCreator
    public StructureElement(@JsonProperty("title") final String title,
                            @JsonProperty("description") final String description,
                            @JsonProperty("snippets") final List<String> snippets) {
        this.title = title;
        this.description = description;
        this.snippets = snippets;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getSnippets() {
        return snippets;
    }

    @Override
    public String toString() {
        return "StructureElement{" +
                "title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", snippets=" + snippets +
                '}';
    }

    @Override
    public int compareTo(final StructureElement o) {
        return CASE_INSENSITIVE_TITLE_COMPARATOR.compare(this, o);
    }
}
