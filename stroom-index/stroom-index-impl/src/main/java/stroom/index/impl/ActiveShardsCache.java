package stroom.index.impl;

import stroom.index.impl.ActiveShardsCacheImpl.ActiveShards;
import stroom.index.shared.IndexShardKey;

public interface ActiveShardsCache {

    ActiveShards get(IndexShardKey indexShardKey);
}
