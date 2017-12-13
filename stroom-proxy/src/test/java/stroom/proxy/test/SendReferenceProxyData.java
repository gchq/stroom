package stroom.proxy.test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;

import stroom.util.io.StreamUtil;

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

                OutputStream out = connection.getOutputStream();
                out = new GZIPOutputStream(out);
                PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(out, StreamUtil.DEFAULT_CHARSET));
                printWriter.println("Time,Action,User,File");
                printWriter.println("01/01/2009:00:00:01,OPEN,userone,proxyload.txt");

                printWriter.close();

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
                if (msg != null && msg.length() > 0) {
                    System.out.println(msg);
                }
                connection.disconnect();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
