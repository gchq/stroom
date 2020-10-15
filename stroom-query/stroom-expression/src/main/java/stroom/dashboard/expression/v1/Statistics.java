package stroom.dashboard.expression.v1;

class Statistics {
    static double standardDeviation(final Double[] values) {
        return Math.sqrt(variance(values));
    }

    static double variance(final Double[] values) {
        if (values.length == 0) {
            return 0;
        }
        // Calculate mean
        double total = 0;
        for (final double d : values) {
            total += d;
        }
        final double mean = total / values.length;
        // calculate squared differences.
        double sqTotal = 0;
        for (final double d : values) {
            final double diff = d - mean;
            final double sq = diff * diff;
            sqTotal += sq;
        }
        // calculate variance
        return sqTotal / values.length;
    }
}
