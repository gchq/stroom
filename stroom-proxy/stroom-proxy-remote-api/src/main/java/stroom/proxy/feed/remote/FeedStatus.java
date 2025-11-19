package stroom.proxy.feed.remote;

public enum FeedStatus {
    // No idea why these are mixed case, but we can't change them as they
    // are part of the feed status API, so would break things for
    // old versions of proxy.
    Receive,
    Reject,
    Drop
}
