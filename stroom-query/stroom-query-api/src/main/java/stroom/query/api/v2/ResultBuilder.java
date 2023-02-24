package stroom.query.api.v2;

import java.util.List;

public interface ResultBuilder<T_CHILD_CLASS extends ResultBuilder<?>> {

    /**
     * @param componentId The ID of the component that this result set was requested for. See ResultRequest in
     *                    SearchRequest
     * @return The {@link Result.Builder}, enabling method chaining
     */
    T_CHILD_CLASS componentId(final String componentId);

    /**
     * @param errors If an error has occurred producing this result set then this will have details
     * @return The {@link Result.Builder}, enabling method chaining
     */
    T_CHILD_CLASS errors(final List<String> errors);
}