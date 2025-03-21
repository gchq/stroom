package stroom.proxy.repo;

public record FeedKey(String feed, String type) {

    @Override
    public String toString() {
        return feed + ":" + type;
    }
}
