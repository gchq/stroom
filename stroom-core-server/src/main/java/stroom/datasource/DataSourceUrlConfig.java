package stroom.datasource;

import javax.inject.Singleton;

@Singleton
public class DataSourceUrlConfig {
    private String index = "http://127.0.0.1:8080/api/stroom-index/v2";
    private String statisticStore = "http://127.0.0.1:8080/api/sqlstatistics/v2";
    private String annotations = "http://IP_ADDRESS/annotationsService/queryApi/v1";
    private String elasticIndex = "http://IP_ADDRESS/queryElasticService/queryApi/v1";

    public String getIndex() {
        return index;
    }

    public void setIndex(final String index) {
        this.index = index;
    }

    public String getStatisticStore() {
        return statisticStore;
    }

    public void setStatisticStore(final String statisticStore) {
        this.statisticStore = statisticStore;
    }

    public String getAnnotations() {
        return annotations;
    }

    public void setAnnotations(final String annotations) {
        this.annotations = annotations;
    }

    public String getElasticIndex() {
        return elasticIndex;
    }

    public void setElasticIndex(final String elasticIndex) {
        this.elasticIndex = elasticIndex;
    }
}
