package stroom.proxy.repo;

public class ForwardUrl {
    private final int id;
    private final String url;

    public ForwardUrl(final int id, final String url) {
        this.id = id;
        this.url = url;
    }

    public int getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }
}
