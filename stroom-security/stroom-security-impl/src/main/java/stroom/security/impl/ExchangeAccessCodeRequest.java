package stroom.security.impl;

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

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }
}
