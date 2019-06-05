package stroom.db.migration.mysql;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import stroom.entity.server.util.XMLMarshallerUtil;
import stroom.stream.OldFindStreamCriteria;

import javax.xml.bind.JAXBContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class V6_0_0_9__ProcessingFilterIT {

    private static final String TEST_USER = "stroomuser";
    private static final String TEST_PASSWORD = "stroompassword1";

    /**
     * If you have a database on a version prior to 6.0.0.13 then you can run this test to
     * step through the various things done during this migration without having to run up stroom
     *

     * @throws Exception If anything goes wrong, just go bang */
    @Test
    @Ignore
    public void testMigrateOnDockerImage() throws Exception {
        final V6_0_0_9__ProcessingFilter filter = new V6_0_0_9__ProcessingFilter(false);

        final Properties connectionProps = new Properties();
        connectionProps.put("user", TEST_USER);
        connectionProps.put("password", TEST_PASSWORD);

        final Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3307/stroom",
                connectionProps);

        filter.migrate(conn);

        conn.close();
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
