package dev.keshav.wireframe.http;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public record HttpRequest(
        HttpMethod method,
        String path,
        String version,
        Map<String,String> headers,
        byte[] body
) {

    public String bodyAsString(){
        return new String(body, StandardCharsets.UTF_8);
    }

    public String header(String name) {
        return headers.get(name.toLowerCase());
    }
}
