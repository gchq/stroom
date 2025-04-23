package stroom.proxy.repo;

public record FeedKey(String feed, String type) {

    public static FeedKey of(final String feed, final String type) {
        return new FeedKey(feed, type);
    }

    @Override
    public String toString() {
        return feed + ":" + type;
    }
}
