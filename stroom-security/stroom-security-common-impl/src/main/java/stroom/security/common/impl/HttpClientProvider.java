package stroom.security.common.impl;


import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

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
        //        HttpHost localhost = new HttpHost("localhost", 80);
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
