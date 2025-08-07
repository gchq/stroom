package stroom.pathways;

import java.text.NumberFormat;
import java.time.Duration;

public class DurationUtil {
    private final NumberFormat nf;

    public DurationUtil() {
        nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);
    }

    public void append(final StringBuilder sb, final Duration duration) {
        final long seconds = duration.getSeconds();
        final long nanos = duration.getNano();
        if (seconds > 0) {
            final long minutes = seconds / 60;
            if (minutes > 0) {
                final long hours = minutes / 60;
                if (hours > 0) {
                    sb.append(nf.format(seconds / 3600F));
                    sb.append("h");
                } else {
                    sb.append(nf.format(seconds / 60F));
                    sb.append("m");
                }
            } else {
                final float fractionalSeconds = seconds + (nanos / 1000000000F);
                sb.append(nf.format(fractionalSeconds));
                sb.append("s");
            }
        } else {

            final long ms = nanos / 1000000;
            if (ms > 0) {
                sb.append(nf.format(nanos / 1000000F));
                sb.append("ms");
            } else {
                final long micro = nanos / 1000;
                if (micro > 0) {
                    sb.append(nf.format(nanos / 1000F));
                    sb.append("µs");
                } else {
                    sb.append(nanos);
                    sb.append("ns");
                }
            }
        }
    }
}
