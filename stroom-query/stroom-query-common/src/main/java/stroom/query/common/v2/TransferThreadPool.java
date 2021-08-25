package stroom.query.common.v2;

import stroom.util.concurrent.ScalingThreadPoolExecutor;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import java.util.concurrent.TimeUnit;

public class TransferThreadPool {

    private final ThreadGroup poolThreadGroup = new ThreadGroup(StroomThreadGroup.instance(), "LmdbDataStore");
    private final CustomThreadFactory taskThreadFactory = new CustomThreadFactory(
            "LmdbDataStore - Transfer #", poolThreadGroup, Thread.NORM_PRIORITY);
    private final ScalingThreadPoolExecutor executor = ScalingThreadPoolExecutor.newScalingThreadPool(
            0,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            60L,
            TimeUnit.SECONDS,
            taskThreadFactory);

    public ScalingThreadPoolExecutor getExecutor() {
        return executor;
    }
}
