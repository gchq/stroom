package stroom.pipeline.cache;

import com.maxmind.geoip2.DatabaseReader;

import java.time.Instant;

public interface GeoIp2DatabaseReaderCache {

    /**
     * Load a Maxmind GeoIp2 database from the stream store.
     * The time is used to calculate the effective reference stream to use for GeoIp2 lookups.
     * @param feedName Name of the feed containing GeoIp2 database reference streams
     * @param streamType Type of stream (e.g. `Raw Reference`)
     * @param time Time to use for effective stream lookup
     * @return DatabaseReader for performing IP address lookups.
     */
    DatabaseReader getReader(String feedName, String streamType, Instant time);
}
