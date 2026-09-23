package dev.keshav.wireframe.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public record HttpResponse(
        int statusCode,
        String statusMessage,
        Map<String, String> headers,
        byte[] body
) {

    public static HttpResponse text(int statusCode, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/plain; charset=utf-8");

        String message = statusMessageFor(statusCode);

        return new HttpResponse(statusCode, message, headers, body.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpResponse notFound() {
        return text(404, "Not Found");
    }

    private static String statusMessageFor(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 413 -> "Payload Too Large";
            case 431 -> "Request Header Fields Too Large";
            case 501 -> "Not Implemented";
            default -> "Unknown";
        };
    }

    public static void httpResponseBuilder(OutputStream out, HttpResponse response) throws IOException {
        byte[] body = response.body();

        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1").append(" ").append(response.statusCode()).append(" ").append(response.statusMessage()).append("\r\n");

        Map<String, String> headers = response.headers();
        headers.forEach((key, value) -> {
            header.append(key).append(": ").append(value).append("\r\n");
        });

        // Content-Length is always computed here, never trusted from the caller,
        // so it can never be missing or wrong, whatever body.length actually is (including 0).
        header.append("Content-Length: ").append(body.length).append("\r\n");

        header.append("\r\n");

        out.write(header.toString().getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }
}
