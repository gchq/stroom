package stroom.websocket.client;

import stroom.util.shared.WebSocketMessage;

import org.fusesource.restygwt.client.JsonEncoderDecoder;

public interface WebSocketMessageCodec extends JsonEncoderDecoder<WebSocketMessage> {

}
