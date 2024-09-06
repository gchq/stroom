package stroom.proxy.app;

import stroom.util.io.StreamUtil;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public class SendReferenceProxyData {

    private SendReferenceProxyData() {
    }

    public static void main(final String[] args) {
        try {
            String urlS = "http://some.server.co.uk/stroom/datafeed";

            URL url = new URL(urlS);

            for (int i = 0; i < 200; i++) {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                if (connection instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) connection).setHostnameVerifier((arg0, arg1) -> true);
                }

                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/audit");
                connection.setRequestProperty("Compression", "gzip");
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.addRequestProperty("Feed", "VERY_SIMPLE_DATA_SPLITTER-EVENTS-V1");
                connection.setRequestProperty("Connection", "Keep-Alive");

                try (final PrintWriter printWriter =
                        new PrintWriter(
                                new OutputStreamWriter(
                                        new GzipCompressorOutputStream(connection.getOutputStream()),
                                        StreamUtil.DEFAULT_CHARSET))) {
                    printWriter.println("Time,Action,User,File");
                    printWriter.println("01/01/2009:00:00:01,OPEN,userone,proxyload.txt");
                }

                int response = connection.getResponseCode();
                String msg = connection.getResponseMessage();

                byte[] buffer = new byte[1000];
                if (response == 200) {
                    while (connection.getInputStream().read(buffer) != -1) {
                    }
                } else {
                    while (connection.getErrorStream().read(buffer) != -1) {
                    }
                }

                System.out.println("Client Got Response " + response);
                if (msg != null && !msg.isEmpty()) {
                    System.out.println(msg);
                }
                connection.disconnect();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
