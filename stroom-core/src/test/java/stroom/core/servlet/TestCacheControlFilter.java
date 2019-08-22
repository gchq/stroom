package stroom.core.servlet;


import org.junit.jupiter.api.Test;

class TestCacheControlFilter {
    @Test
    void testExpires() {
        // Add an expiry time, e.g. Expires: Wed, 21 Oct 2015 07:28:00 GMT
        final CacheControlFilter filter = new CacheControlFilter();
        System.out.println(filter.getExpires(600));
    }
}
