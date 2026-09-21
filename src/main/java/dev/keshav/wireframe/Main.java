package dev.keshav.wireframe;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
            client.setSoTimeout(5000);

            // ---------- READ THE REQUEST ----------

            // client.getInputStream()   -> raw bytes coming IN from the client (the request)
            // InputStreamReader         -> converts those bytes into characters (UTF-8)
            // BufferedReader            -> collects characters and gives us readLine()
            InputStream in = client.getInputStream();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean isFirst=true;
            String method = null;
            String path = null;
            String version = null;
            HashMap<String, String> header = new HashMap<>();
            int contentLength=0;

            // Loop per LINE: readLine() gives ONE line per call, without the \r\n.
            // Stops when:
            //   line == null    -> the client disconnected
            //   line.isEmpty()  -> the blank line, which means the headers are finished
            // The request line and headers are printed as they are read. Real parsing is Day 2.
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    // 1. Convert accumulated bytes to string and trim trailing \r
                    String line = baos.toString(StandardCharsets.UTF_8).trim();
                    baos.reset();

                    // 2. BLANK LINE DETECTED -> End of Headers!
                    if (line.isEmpty()) {
                        break; // Stop reading stream immediately!
                    }

                    // 3. Parse Request Line or Header Key-Value Pair
                    if (isFirst) {
                        String[] result = line.split(" ");
                        if (result.length == 3) {
                            method = result[0];
                            path = result[1];
                            version = result[2];
                            isFirst = false;
                        }
                    } else {
                        int indexOfColon = line.indexOf(":");
                        if (indexOfColon != -1) {
                            String key = line.substring(0, indexOfColon).trim().toLowerCase();
                            String value = line.substring(indexOfColon + 1).trim();
                            header.put(key, value);
                        }
                    }
                } else {
                    // Append character byte to accumulator
                    baos.write(b);
                }
            }

            if (header.containsKey("content-length")){
                try {
                    String value = header.get("content-length");
                    contentLength=Integer.parseInt(value);
                } catch (NumberFormatException e){
                    System.out.println("NumberFormatException" + e);
                }
            }

            int MAX_BODY_SIZE= 1048576;

            if (contentLength>0 && contentLength <=MAX_BODY_SIZE ){
                byte[] content = new byte[contentLength];
                int totalBytesRead = 0;
                int bytesRemaining = contentLength;
                while(totalBytesRead < contentLength){

                    int bytesRead =  in.read(content,totalBytesRead,bytesRemaining);

                    if (bytesRead == -1) break;

                    totalBytesRead += bytesRead;
                    bytesRemaining-=bytesRead;
                }
                String contentType = header.get("content-type");
                if (contentType != null && contentType.toLowerCase().contains("text/plain") && Objects.equals(method, "POST")) {
                    String requestBody = new String(content, 0, totalBytesRead, StandardCharsets.UTF_8);
                    System.out.println(requestBody);
                }
            }

            //for now later on we can use switch case or somthing in proper methods


            System.out.println("Method: " + method + " " +"Path: " + path + " " + "version: " + version);

            header.forEach((key,value) ->{
                System.out.println("Key: " + key + ", Value: " + value);
            });

            // ---------- BUILD THE RESPONSE ----------

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
            System.err.println("Client timed out: " + client.getRemoteSocketAddress());
            // Optionally send a 408 Request Timeout if output stream is still usable
        } catch (IOException e) {
            System.err.println("Client I/O error: " + e.getMessage());
        }
    }
}
