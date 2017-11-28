package stroom.entity.server.util;

import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Subclasses of this will specify the URL and HTTP Method for sending commands
 * to remote Doc Ref management services.
 *
 * Assumptions - no body required, path parameters carry all the information.
 * Some body will come back, so the status should be HTTP 200
 */
public abstract class ExternalCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalCommand.class);

    protected abstract String getUrl();
    protected abstract String getMethod();

    public String send() throws Exception {
        final String method = getMethod();
        final String url = getUrl();
        final URL urlObj = new URL(url);
        final HttpURLConnection con = (HttpURLConnection) urlObj.openConnection();

        //add request header
        con.setRequestMethod(method);

        // Send post request
        con.setDoOutput(false);
        con.connect();

        final int responseCode = con.getResponseCode();
        LOGGER.debug(String.format("Sent %s to %s, received %d", method, url, responseCode));

        final StringBuilder response = new StringBuilder();

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()))) {
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }

        switch (responseCode) {
            case HttpStatus.OK_200:
            case HttpStatus.NO_CONTENT_204:
                break;
            default:
                throw new Exception(String.format("Bad response code from server %s - %d", url, responseCode));
        }

        //print result
        return response.toString();
    }
}
