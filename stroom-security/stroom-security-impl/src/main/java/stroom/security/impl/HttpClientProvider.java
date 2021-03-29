package stroom.security.impl;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class HttpClientProvider implements Provider<CloseableHttpClient> {
    private final PoolingHttpClientConnectionManager connectionManager;

    @Inject
    HttpClientProvider() {
        connectionManager = new PoolingHttpClientConnectionManager();
        // Increase max total connection to 200
        connectionManager.setMaxTotal(200);
        // Increase default max connection per route to 20
        connectionManager.setDefaultMaxPerRoute(20);
        // Increase max connections for localhost:80 to 50
        //        HttpHost localhost = new HttpHost("locahost", 80);
        //        cm.setMaxPerRoute(new HttpRoute(localhost), 50);
    }

    @Override
    public CloseableHttpClient get() {
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true)
                .build();
    }
}
