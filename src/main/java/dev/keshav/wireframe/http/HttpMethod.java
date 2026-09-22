package dev.keshav.wireframe.http;

public enum HttpMethod {
    GET,POST,PUT,PATCH,DELETE;

    public static HttpMethod fromString(String text) {
        if (text != null) {
            for (HttpMethod method : values()) {
                if (method.name().equals(text)) {   // exact match: HTTP methods are case-sensitive
                    return method;
                }
            }
        }
        throw new HttpParseException(501, "Unknown HTTP method: " + text);
    }
}
