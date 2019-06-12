package stroom.core.db.migration.mysql;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import stroom.config.common.ConnectionConfig;
import stroom.db.util.DbUtil;

import javax.xml.bind.JAXBContext;
import java.sql.Connection;

@Disabled
class TestProcessingFilterMigration {
    private static final String TEST_USER = "stroomuser";
    private static final String TEST_PASSWORD = "stroompassword1";

    /**
     * If you have a database on a version prior to 6.0.0.13 then you can run this test to
     * step through the various things done during this migration without having to run up stroom
     *
     * @throws Exception If anything goes wrong, just go bang
     */
    @Test
    @Disabled
    public void testMigrateOnDockerImage() throws Exception {
        final V6_0_0_9__ProcessingFilter filter = new V6_0_0_9__ProcessingFilter(false);

        final ConnectionConfig connectionConfig = new ConnectionConfig();
        DbUtil.decorateConnectionConfig(connectionConfig);
        DbUtil.validate(connectionConfig);
        try (final Connection conn = DbUtil.getSingleConnection(connectionConfig)) {
            filter.migrate(conn);
        }
    }

    /**
     * Verify unmarshalling of the entrySetId works
     */
    @Test
    public void testUnMarshal1() throws Exception {

        JAXBContext findStreamCriteriaJaxb;
        findStreamCriteriaJaxb = JAXBContext.newInstance(OldFindStreamCriteria.class);

        String input = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<findStreamCriteria>")
                .append("  <feedIdSet>")
                .append("    <idSet>1</idSet>")
                .append("  </feedIdSet>")
                .append("  <streamTypeIdSet>")
                .append("    <idSet>11</idSet>")
                .append("  </streamTypeIdSet>")
                .append("</findStreamCriteria>")
                .toString();

        System.out.println(input);

        OldFindStreamCriteria criteria = XMLMarshallerUtil.unmarshal(
                findStreamCriteriaJaxb, OldFindStreamCriteria.class, input);

        Assert.assertTrue(criteria.getFeeds().getInclude().getSet().contains(1L));
        Assert.assertTrue(criteria.getStreamTypeIdSet().getSet().contains(11L));
    }

    /**
     * Verify unmarshalling of the entrySetId works
     */
    @Test
    public void testUnMarshal2() throws Exception {

        JAXBContext findStreamCriteriaJaxb;
        findStreamCriteriaJaxb = JAXBContext.newInstance(OldFindStreamCriteria.class);

        String input = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<findStreamCriteria>")
                .append("  <feedIdSet>")
                .append("    <id>1</id>")
                .append("    <id>2</id>")
                .append("  </feedIdSet>")
                .append("  <streamTypeIdSet>")
                .append("    <id>11</id>")
                .append("    <id>12</id>")
                .append("  </streamTypeIdSet>")
                .append("</findStreamCriteria>")
                .toString();

        System.out.println(input);

        OldFindStreamCriteria criteria = XMLMarshallerUtil.unmarshal(
                findStreamCriteriaJaxb, OldFindStreamCriteria.class, input);

        Assert.assertTrue(criteria.getFeeds().getInclude().getSet().contains(1L));
        Assert.assertTrue(criteria.getFeeds().getInclude().getSet().contains(2L));
        Assert.assertTrue(criteria.getStreamTypeIdSet().getSet().contains(11L));
        Assert.assertTrue(criteria.getStreamTypeIdSet().getSet().contains(12L));
    }
}
