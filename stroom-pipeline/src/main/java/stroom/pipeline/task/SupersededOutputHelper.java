package stroom.pipeline.task;

import stroom.data.store.api.Target;

import java.util.function.Supplier;

public interface SupersededOutputHelper {
    /**
     * Record that we are creating a target for the current processing pipeline.
     *
     * @param supplier The supplier of the new target.
     * @return The newly created target.
     */
    Target addTarget(Supplier<Target> supplier);

    /**
     * Look for any other streams that have been produced by the same pipeline
     * and stream as the one we are processing. If we find any only the latest
     * stream task id is validate (which would normally be this stream task).
     * Any earlier stream tasks their streams should be deleted. If we are an
     * earlier stream task then mark our output as to be deleted (rather than
     * unlock it).
     */
    boolean isSuperseded();
}
