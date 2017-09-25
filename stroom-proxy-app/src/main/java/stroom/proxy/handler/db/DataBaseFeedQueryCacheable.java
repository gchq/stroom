package stroom.proxy.handler.db;

import com.googlecode.ehcache.annotations.Cacheable;
import com.googlecode.ehcache.annotations.KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.server.FeedStatus;
import stroom.feed.server.GetFeedStatusRequest;
import stroom.feed.server.GetFeedStatusResponse;
import stroom.proxy.handler.LocalFeedService;
import stroom.util.cert.CertificateUtil;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DataBaseFeedQueryCacheable implements LocalFeedService {
    private final static Logger LOGGER = LoggerFactory.getLogger(DataBaseFeedQueryCacheable.class);

    public final static String SQL_Y = "Y";
    public final static String SQL_N = "N";
    public final static String SQL_RECEIVE = "RECEIVE";
    public final static String SQL_DROP = "RDROP";
    public final static String SQL_REQ_EFFECTIVE = "REQ_EFFECTIVE";
    public final static String SQL_REQ_CERT = "REQ_CERT";
    public final static String SQL_CHECK_CERT = "CHECK_CERT";

    @Resource
    DataBaseValidatorDataSource dataBaseValidatorDataSource;

    private String dbRequestValidatorFeedQuery;
    private String dbRequestValidatorAuthQuery;

    protected Connection getConnection() throws SQLException {
        DataSource dataSource = dataBaseValidatorDataSource.getDataSource();
        return dataSource.getConnection();
    }

    @Cacheable(cacheName = "dataBaseCache", keyGenerator = @KeyGenerator(name = "ListCacheKeyGenerator"))
    @SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public GetFeedStatusResponse getFeedStatus(GetFeedStatusRequest request) {
        GetFeedStatusResponse response = new GetFeedStatusResponse();
        response.setStatus(FeedStatus.Receive);

        try (Connection connection = getConnection()) {
            long start = System.currentTimeMillis();
            String feed = request.getFeedName();

            if (feed == null || feed.length() == 0) {
                return GetFeedStatusResponse.createFeedRequiredResponse();
            }

            try (PreparedStatement feedQuery = connection.prepareStatement(dbRequestValidatorFeedQuery)) {
                feedQuery.setString(1, feed);

                try (ResultSet feedResults = feedQuery.executeQuery()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("getFeedStatus() - Query took " + (System.currentTimeMillis() - start) + " ms - "
                                + dbRequestValidatorFeedQuery + " - (" + feed + ")");
                    }

                    if (feedResults.next()) {
                        String receive = feedResults.getString(SQL_RECEIVE);
                        String drop = feedResults.getString(SQL_DROP);
                        String reqCert = feedResults.getString(SQL_REQ_CERT);
                        String checkCert = feedResults.getString(SQL_CHECK_CERT);

                        if (!SQL_Y.equals(receive)) {
                            return GetFeedStatusResponse.createFeedNotSetToReceiveDataResponse();
                        }

                        if (SQL_Y.equals(drop)) {
                            return GetFeedStatusResponse.createOKDropResponse();
                        }

                        if (SQL_Y.equals(reqCert)) {
                            if (request.getSenderDn() == null) {
                                return GetFeedStatusResponse.createCertificateRequiredResponse();
                            }

                            if (SQL_Y.equals(checkCert)) {
                                String remoteDN = request.getSenderDn();
                                String remoteCN = CertificateUtil.extractCNFromDN(remoteDN);
                                start = System.currentTimeMillis();

                                try (PreparedStatement certQuery = connection
                                        .prepareStatement(dbRequestValidatorAuthQuery)) {
                                    certQuery.setString(1, feed);
                                    certQuery.setString(2, remoteCN);

                                    try (ResultSet certResults = certQuery.executeQuery()) {
                                        if (LOGGER.isDebugEnabled()) {
                                            LOGGER.debug("getFeedStatus() - Query took "
                                                    + (System.currentTimeMillis() - start) + " ms - "
                                                    + dbRequestValidatorAuthQuery + " - (" + feed + "," + remoteCN
                                                    + ")");
                                        }

                                        if (!certResults.next()) {
                                            return GetFeedStatusResponse.createCertificateNotAuthorisedResponse();
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        return GetFeedStatusResponse.createFeedNotSetToReceiveDataResponse();
                    }
                }
            }
        } catch (SQLException sqlEx) {
            LOGGER.error("getFeedStatus", sqlEx);
        }
        return response;
    }

    public void setDbRequestValidatorAuthQuery(String dbRequestValidatorAuthQuery) {
        this.dbRequestValidatorAuthQuery = dbRequestValidatorAuthQuery;
    }

    /**
     * Query Format
     * <p>
     * Accepts a feed name as a parameter and returns the following columns
     * RECEIVE - Y/N COMPUTER_AUTH - OPTIONAL / REQUIRED / RESTRICTED
     * <p>
     * dbRequestValidatorAuthQuery Accepts a feed name and computer name as
     * parameters and returns the a row result if OK ... otherwise returns no
     * rows
     */
    public void setDbRequestValidatorFeedQuery(String dbRequestValidatorFeedQuery) {
        this.dbRequestValidatorFeedQuery = dbRequestValidatorFeedQuery;
    }

}
