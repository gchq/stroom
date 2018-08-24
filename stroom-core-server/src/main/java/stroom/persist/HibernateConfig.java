package stroom.persist;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class HibernateConfig {
    private String dialect = "org.hibernate.dialect.MySQLInnoDBDialect";
    private boolean showSql;
    private boolean formatSql;
    private String jpaHbm2DdlAuto = "validate";
    private boolean generateStatistics = true;

    @JsonPropertyDescription("Should only be set per node in application property file")
    public String getDialect() {
        return dialect;
    }

    public void setDialect(final String dialect) {
        this.dialect = dialect;
    }

    @JsonPropertyDescription("Log SQL")
    public boolean isShowSql() {
        return showSql;
    }

    public void setShowSql(final boolean showSql) {
        this.showSql = showSql;
    }

    public boolean isFormatSql() {
        return formatSql;
    }

    public void setFormatSql(final boolean formatSql) {
        this.formatSql = formatSql;
    }

    @JsonPropertyDescription("Set by property file to enable auto schema creation")
    public String getJpaHbm2DdlAuto() {
        return jpaHbm2DdlAuto;
    }

    public void setJpaHbm2DdlAuto(final String jpaHbm2DdlAuto) {
        this.jpaHbm2DdlAuto = jpaHbm2DdlAuto;
    }

    public boolean isGenerateStatistics() {
        return generateStatistics;
    }

    public void setGenerateStatistics(final boolean generateStatistics) {
        this.generateStatistics = generateStatistics;
    }
}
