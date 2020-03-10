package stroom.proxy.status.remote;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

@JsonInclude(Include.NON_NULL)
public class GetStatusResponse implements Serializable {
    private static final long serialVersionUID = 8200506344347303608L;

    @JsonProperty
    public final List<StatusEntry> statusEntryList;

    @JsonCreator
    public GetStatusResponse(@JsonProperty("statusEntryList") final List<StatusEntry> statusEntryList) {
        this.statusEntryList = statusEntryList;
    }

    public List<StatusEntry> getStatusEntryList() {
        return statusEntryList;
    }

    public enum Status {
        Error, Warn, Info
    }

    public static class StatusEntry implements Serializable {
        private static final long serialVersionUID = 4610323736148802674L;
        private Status status;
        private String area;
        private String message;

        public StatusEntry(Status status, String area, String message) {
            this.status = status;
            this.area = area;
            this.message = message;
        }

        public Status getStatus() {
            return status;
        }

        public String getArea() {
            return area;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return status + ", " + area + ", " + message;
        }
    }
}
