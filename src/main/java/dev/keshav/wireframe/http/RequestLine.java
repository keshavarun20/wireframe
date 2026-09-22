package dev.keshav.wireframe.http;

public record RequestLine(
        HttpMethod method,
        String path,
        String version
) {
}
