package stroom.index.impl;

import stroom.index.shared.IndexShard;
import stroom.util.sysinfo.SystemInfoResult;

public interface IndexSystemInfoProvider {
    SystemInfoResult getSystemInfo(IndexShard indexShard,
                                   Integer limit,
                                   Long streamId);
}
