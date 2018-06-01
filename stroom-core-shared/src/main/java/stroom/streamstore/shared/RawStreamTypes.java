package stroom.streamstore.shared;

public class RawStreamTypes {
    /** Get rid of this when the stream type of received data can be set by the receiving pipeline */
    @Deprecated
    public static boolean isRawType(final String type) {
        return "Raw Events".equals(type) || "Raw Reference".equals(type);
    }
}
