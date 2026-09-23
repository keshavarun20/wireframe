package dev.keshav.wireframe;

import dev.keshav.wireframe.http.*;
import dev.keshav.wireframe.router.Router;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;

public class Main {

    // The "door" our server listens on. Clients connect to this port.
    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {

        // Routes are registered once here, at startup, before any client
        // connects. The Router just holds a lookup table (method+path -> Handler),
        // this is where we fill it in.
        Router router = new Router();
        router.register(HttpMethod.GET, "/health", request -> HttpResponse.text(200, "OK"));
        router.register(HttpMethod.POST, "/echo", request -> HttpResponse.text(200, request.bodyAsString()));

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
                    handleClient(client, router);
                }
                // After handling, the loop goes back to accept() and waits for the next client.
            }
        }
    }

    private static void handleClient(Socket client, Router router) {
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

            // Debug-only: log the raw parsed body for a text/plain POST,
            // independent of whichever route ends up handling it below.
            String contentType = request.header("content-type");
            if (contentType != null && contentType.toLowerCase().contains("text/plain") && Objects.equals(request.method(), HttpMethod.POST)) {
                String requestBody = request.bodyAsString();
                System.out.println(requestBody);
            }

            System.out.println("Method: " + request.method() + " " + "Path: " + request.path() + " " + "version: " + request.version());

            request.headers().forEach((key, value) -> {
                System.out.println(key + ": " + value);
            });

            // Look up the Handler registered for this method+path and run it.
            // Falls back to a 404 HttpResponse if nothing matches.
            HttpResponse response = router.route(request);

            // client.getOutputStream() -> bytes going OUT to the client (the response).
            OutputStream out = client.getOutputStream();

            // Builds the status line, headers (including Content-Length,
            // computed from the response's actual body), blank line, and
            // body, then writes and flushes it all to the socket.
            HttpResponse.httpResponseBuilder(out, response);

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
