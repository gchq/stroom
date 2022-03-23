package stroom.proxy.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;

public class RawVertx  extends AbstractVerticle {
    private static Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(24));

    // Convenience method so you can run it in your IDE
    public static void main(String[] args) throws Exception {
        System.out.println("AVAILABLE PROCESSORS = " + Runtime.getRuntime().availableProcessors());
        vertx.deployVerticle(new RawVertx());
    }

    @Override
    public void start() throws Exception {
        vertx.createHttpServer().requestHandler(req -> {
            req
                    .response()
                    .putHeader("content-type", "text/html")
                    .end("Hello world");
        }).listen(8090);
    }
}