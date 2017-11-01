package stroom.proxy.test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import stroom.util.io.StreamUtil;

public class SendSampleProxyData {
    private SendSampleProxyData() {
    }

    public static void main(final String[] args) {
        doWork("VERY_SIMPLE_DATA_SPLITTER-EVENTS");
        doWork("VERY_SIMPLE_DATA_SPLITTER-EVENTS-V2");
    }

    private static void doWork(String feed) {
        try {
            String urlS = "http://localhost:8980/stroom-proxy/datafeed";

            URL url = new URL(urlS);

            for (int i = 0; i < 10; i++) {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                if (connection instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) connection).setHostnameVerifier((arg0, arg1) -> true);
                }
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/audit");
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setChunkedStreamingMode(100);
                connection.addRequestProperty("Feed", feed);
                connection.connect();

                OutputStream out = connection.getOutputStream();
                PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(out, StreamUtil.DEFAULT_CHARSET));
                printWriter.println("Id,Time,Action,User,File");
                for (int z = 0; z < 10; z++) {
                    printWriter.println(z + ",01/01/2009:00:00:01,OPEN,userone,proxyload.txt");
                }

                printWriter.close();

                int response = connection.getResponseCode();
                String msg = connection.getResponseMessage();

                connection.disconnect();

                System.out.println("Client Got Response " + response);
                if (msg != null && msg.length() > 0) {
                    System.out.println(msg);
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
