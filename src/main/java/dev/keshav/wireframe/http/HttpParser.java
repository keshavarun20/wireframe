package dev.keshav.wireframe.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpParser {

    private static final int MAX_LINE_LENGTH = 8192;
    private static final int MAX_BODY_SIZE = 1048576;

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;

        while ((b = in.read()) != -1) {
            if (b == '\n') {
                byte[] bytes = buffer.toByteArray();
                int length = bytes.length;

                // remove only a trailing \r, not other whitespace
                if (length > 0 && bytes[length - 1] == '\r') {
                    length--;
                }
                return new String(bytes, 0, length, StandardCharsets.UTF_8);
            }

            if (buffer.size() >= MAX_LINE_LENGTH) {
                throw new HttpParseException(431, "Header line too long");
            }
            buffer.write(b);
        }

        // stream ended before a \n arrived
        if (buffer.size() == 0) {
            return null; // client closed normally
        }
        throw new HttpParseException(400, "Connection closed in the middle of a line");
    }

    private RequestLine parseRequestLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new HttpParseException(400, "Empty request line");
        }

        String[] parts = line.split(" ");
        if (parts.length != 3) {
            throw new HttpParseException(400, "Malformed request line " + line);
        }

        HttpMethod method = HttpMethod.fromString(parts[0]);
        String path = parts[1];
        String version = parts[2];

        if (!path.startsWith("/")) {
            throw new HttpParseException(400, "Invalid request path: " + path);
        }

        if (!version.startsWith("HTTP/")) {
            throw new HttpParseException(400, "Invalid HTTP version: " + version);
        }

        return new RequestLine(method, path, version);
    }

    private void parseHeader(String line, Map<String, String> header) {
        int indexOfColon = line.indexOf(":");
        if (indexOfColon != -1) {
            String key = line.substring(0, indexOfColon).trim().toLowerCase();
            String value = line.substring(indexOfColon + 1).trim();
            header.put(key, value);
        }
    }

    private int parseContentLength(Map<String, String> header) {
        if (header.containsKey("content-length")) {
            try {
                String value = header.get("content-length");
                int contentLength = Integer.parseInt(value);

                if (contentLength <= 0) {
                    throw new HttpParseException(400, "Invalid Content-Length: must be positive");
                }
                if (contentLength > MAX_BODY_SIZE) {
                    throw new HttpParseException(413, "Request body too large");
                }
                return contentLength;
            } catch (NumberFormatException e) {
                throw new HttpParseException(400, "Invalid Content-Length " + e);
            }
        }

        return 0;
    }

    private byte[] readBody(InputStream in, int contentLength) throws IOException {
        byte[] content = new byte[contentLength];
        int totalBytesRead = 0;
        int bytesRemaining = contentLength;

        while (totalBytesRead < contentLength) {
            int bytesRead = in.read(content, totalBytesRead, bytesRemaining);
            if (bytesRead == -1) break;
            totalBytesRead += bytesRead;
            bytesRemaining -= bytesRead;
        }

        if (totalBytesRead < contentLength) {
            throw new HttpParseException(400, "Connection closed before full body received");
        }
        return content;
    }

    public HttpRequest parse(InputStream in) throws IOException {
        Map<String, String> header = new HashMap<>();
        RequestLine requestLine = parseRequestLine(readLine(in));

        while (true) {
            String line = readLine(in);
            if (line == null) {
                throw new HttpParseException(400, "Connection closed before headers finished");
            }
            if (line.isEmpty()) break;
            parseHeader(line, header);
        }

        int contentLength = parseContentLength(header);
        byte[] body = readBody(in, contentLength);

        return new HttpRequest(
                requestLine.method(),
                requestLine.path(),
                requestLine.version(),
                header,
                body
        );
    }
}
