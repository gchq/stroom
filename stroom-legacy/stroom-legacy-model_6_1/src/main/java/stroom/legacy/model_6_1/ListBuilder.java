package stroom.legacy.model_6_1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A general purpose list builder
 * @param <ListedPojo> The classes being put into the list under construction
 */
@Deprecated
public class ListBuilder<ListedPojo> {
    private final List<ListedPojo> childValues = new ArrayList<>();

    /**
     * Add values to our list
     * @param values The values to add to our list
     * @return this builder, allowing method chaining
     */
    public ListBuilder<ListedPojo> value(final ListedPojo...values) {
        this.childValues.addAll(Arrays.asList(values));
        return this;
    }

    protected List<ListedPojo> build() {
        return childValues;
    }
}