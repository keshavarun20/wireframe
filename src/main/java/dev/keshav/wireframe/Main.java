package dev.keshav.wireframe;

import dev.keshav.wireframe.http.HttpMethod;
import dev.keshav.wireframe.http.HttpParseException;
import dev.keshav.wireframe.http.HttpParser;
import dev.keshav.wireframe.http.HttpRequest;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Main {

    // The "door" our server listens on. Clients connect to this port.
    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {

        // Open the door (port 8080) and start listening.
        // try-with-resources closes the ServerSocket automatically when we exit.
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Wireframe listening on port " + PORT);

            // Infinite loop: keeps the server alive.
            // Without it, main() would finish after one client and the program would exit.
            while (true) {

                // accept() BLOCKS: the thread sleeps here until a client connects.
                // When one does, it returns a Socket, which is the connection to that client
                // (it already knows the client's IP and port).
                // try-with-resources closes the client socket when we're done with it.
                try (Socket client = serverSocket.accept()) {
                    handleClient(client);
                }
                // After handling, the loop goes back to accept() and waits for the next client.
            }
        }
    }

    private static void handleClient(Socket client) {
        try {
            System.out.println("Client connected: " + client.getRemoteSocketAddress());

            // Stop waiting on a stalled/slow client after 5s, so one bad
            // connection can't block the server forever (see SocketTimeoutException below).
            client.setSoTimeout(5000);

            // Raw bytes coming IN from the client. HttpParser reads directly
            // from this, no BufferedReader, so headers and body come from
            // the same stream with nothing buffered ahead and lost.
            InputStream in = client.getInputStream();

            // Parses the request line, headers, and body (using Content-Length)
            // into one HttpRequest object. Throws HttpParseException on anything
            // malformed (bad request line, unknown method, bad/oversized
            // Content-Length, body cut short, etc) instead of crashing.
            HttpParser parser = new HttpParser();
            HttpRequest request = parser.parse(in);

            // Temporary: only handle POST + text/plain bodies for now.
            // Real routing (different behavior per method/path) is Day 3.
            String contentType = request.header("content-type");
            if (contentType != null && contentType.toLowerCase().contains("text/plain") && Objects.equals(request.method(), HttpMethod.POST)) {
                String requestBody = request.bodyAsString();
                System.out.println(requestBody);
            }

            System.out.println("Method: " + request.method() + " " + "Path: " + request.path() + " " + "version: " + request.version());

            request.headers().forEach((key, value) -> {
                System.out.println("Key: " + key + ", Value: " + value);
            });

            // ---------- BUILD THE RESPONSE ----------

            // Fixed "Hello" response for every request, whatever method/path
            // came in. Real per-route responses are Day 3.
            // The body as bytes. We need bytes (not characters) because Content-Length counts bytes.
            byte[] body = "Hello\r\n".getBytes(StandardCharsets.UTF_8);

            // The status line and headers. Every line must end with \r\n.
            String headers = "HTTP/1.1 200 OK\r\n"                           // version, status code, reason
                    + "Content-Type: text/plain; charset=utf-8\r\n"          // what the body is
                    + "Content-Length: " + body.length + "\r\n"              // exact number of body bytes
                    + "Connection: close\r\n"                                // we hang up after this response
                    + "\r\n";                                                // blank line = end of headers

            // ---------- SEND THE RESPONSE ----------

            // client.getOutputStream() -> bytes going OUT to the client (the response).
            // This is the only way to reach the browser. System.out.println only prints to our console.
            OutputStream out = client.getOutputStream();

            out.write(headers.getBytes(StandardCharsets.UTF_8));   // headers first
            out.write(body);                                       // then the body
            out.flush();                                           // push any buffered bytes out now
            // When this method returns, the try-with-resources in main() closes the socket.

        } catch (SocketTimeoutException e) {
            // A client that stalls mid-request (or a fake Content-Length that
            // never fully arrives) lands here after the 5s timeout above.
            System.err.println("Client timed out: " + client.getRemoteSocketAddress());
        } catch (HttpParseException e) {
            // The request was well-formed at the TCP level but broken as HTTP
            // (bad request line, unknown method, invalid Content-Length...).
            // e.getStatusCode() carries the right HTTP status (400/413/431/501)
            // for when we start sending real error responses on Day 5.
            // No response is sent back yet, this only logs it server-side.
            System.err.println("Bad request (" + e.getStatusCode() + "): " + e.getMessage());
        } catch (IOException e) {
            // Any other socket/network failure (connection reset, broken pipe, etc).
            System.err.println("Client I/O error: " + e.getMessage());
        }
    }
}
