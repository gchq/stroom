package stroom.explorer.impl;

import stroom.explorer.shared.StandardTagNames;

import java.util.HashMap;
import java.util.Map;

public class ExplorerTags {
    // TODO : This is a temporary means to set tags on nodes for the purpose of finding data source nodes.
    // TODO : The explorer will eventually allow a user to set custom tags and to find nodes searching by tag.
    private static final Map<String, String> DEFAULT_TAG_MAP = new HashMap<>();

    static {
        DEFAULT_TAG_MAP.put("StatisticStore", StandardTagNames.DATA_SOURCE);
        DEFAULT_TAG_MAP.put("StroomStatsStore", StandardTagNames.DATA_SOURCE);
        DEFAULT_TAG_MAP.put("Index", StandardTagNames.DATA_SOURCE);
        DEFAULT_TAG_MAP.put("ElasticIndex", StandardTagNames.DATA_SOURCE);
        DEFAULT_TAG_MAP.put("SolrIndex", StandardTagNames.DATA_SOURCE);
    }

    public static String getTags(final String type) {
        return DEFAULT_TAG_MAP.get(type);
    }
}
