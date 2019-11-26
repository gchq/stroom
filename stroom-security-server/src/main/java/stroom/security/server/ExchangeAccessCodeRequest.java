package stroom.security.server;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExchangeAccessCodeRequest{
    @JsonProperty
    private String accessCode;

    public ExchangeAccessCodeRequest(){

    }
    public ExchangeAccessCodeRequest(final String accessCode){
        this.accessCode = accessCode;
    }

    public String getAccessCode() {
        return accessCode;
    }
}
