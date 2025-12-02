package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A path key that uses the names of all sub path nodes in the expected order.
 *
 * @param names
 */
@JsonInclude(Include.NON_NULL)
public final class TerminalPathKey implements PathKey {

    public static final PathKey INSTANCE = new TerminalPathKey();

    @Override
    public String toString() {
        return "Terminal";
    }
}
