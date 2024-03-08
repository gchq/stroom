package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticNotificationEmailDestination extends AnalyticNotificationDestination {

    @JsonProperty
    private final String to;
    @JsonProperty
    private final String cc;
    @JsonProperty
    private final String bcc;


    @JsonCreator
    public AnalyticNotificationEmailDestination(@JsonProperty("to") final String to,
                                                @JsonProperty("cc") final String cc,
                                                @JsonProperty("bcc") final String bcc) {
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
    }

    public String getTo() {
        return to;
    }

    public String getCc() {
        return cc;
    }

    public String getBcc() {
        return bcc;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticNotificationEmailDestination that = (AnalyticNotificationEmailDestination) o;
        return Objects.equals(to, that.to) && Objects.equals(cc, that.cc) && Objects.equals(
                bcc,
                that.bcc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(to, cc, bcc);
    }

    @Override
    public String toString() {
        return "AnalyticNotificationEmailDestination{" +
                "to='" + to + '\'' +
                ", cc='" + cc + '\'' +
                ", bcc='" + bcc + '\'' +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    public static class Builder {

        private String to;
        private String cc;
        private String bcc;

        private Builder() {
        }

        private Builder(final AnalyticNotificationEmailDestination config) {
            this.to = config.to;
            this.cc = config.cc;
            this.bcc = config.bcc;
        }

        public Builder to(final String to) {
            this.to = to;
            return this;
        }

        public Builder cc(final String cc) {
            this.cc = cc;
            return this;
        }

        public Builder bcc(final String bcc) {
            this.bcc = bcc;
            return this;
        }

        public AnalyticNotificationEmailDestination build() {
            return new AnalyticNotificationEmailDestination(to, cc, bcc);
        }
    }
}
