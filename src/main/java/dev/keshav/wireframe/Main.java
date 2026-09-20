package dev.keshav.wireframe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

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

    private static void handleClient(Socket client) throws IOException {
        // Prints the client's IP and port (your console only, the browser can't see this).
        System.out.println("Client connected: " + client.getRemoteSocketAddress());

        // ---------- READ THE REQUEST ----------

        // client.getInputStream()   -> raw bytes coming IN from the client (the request)
        // InputStreamReader         -> converts those bytes into characters (UTF-8)
        // BufferedReader            -> collects characters and gives us readLine()
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));

        String line;

        // Loop per LINE: readLine() gives ONE line per call, without the \r\n.
        // Stops when:
        //   line == null    -> the client disconnected
        //   line.isEmpty()  -> the blank line, which means the headers are finished
        // The request line and headers are printed as they are read. Real parsing is Day 2.
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            System.out.println(line);
        }

        // ---------- BUILD THE RESPONSE ----------

        // The body as bytes. We need bytes (not characters) because Content-Length counts bytes.
        byte[] body = "Hello".getBytes(StandardCharsets.UTF_8);

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
    }
}
