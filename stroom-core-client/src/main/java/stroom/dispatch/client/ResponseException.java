package stroom.dispatch.client;

class ResponseException extends RuntimeException {

    private final String method;
    private final String url;
    private final String json;
    private final Integer code;
    private final String details;
    private final String responseKeyValues;

    ResponseException(final String method,
                      final String url,
                      final String json,
                      final Integer code,
                      final String message,
                      final String details,
                      final String responseKeyValues,
                      final Throwable cause) {
        super(message, cause);
        this.method = method;
        this.url = url;
        this.json = json;
        this.code = code;
        this.details = details;
        this.responseKeyValues = responseKeyValues;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return "ResponseException{" +
                "method='" + method + '\'' +
                ", url='" + url + '\'' +
                ", json='" + json + '\'' +
                ", code='" + code + '\'' +
                ", message='" + getMessage() + '\'' +
                ", details='" + details + '\'' +
                ", responseKeyValues='" + responseKeyValues + '\'' +
                '}';
    }
}
