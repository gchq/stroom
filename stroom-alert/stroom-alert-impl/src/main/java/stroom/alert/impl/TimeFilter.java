package stroom.alert.impl;

import java.time.Instant;

public record TimeFilter(String timeField, Instant from, Instant to) {

}
