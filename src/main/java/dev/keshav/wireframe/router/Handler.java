package dev.keshav.wireframe.router;

import dev.keshav.wireframe.http.HttpRequest;
import dev.keshav.wireframe.http.HttpResponse;

@FunctionalInterface
public interface Handler {
    HttpResponse handle(HttpRequest request);
}
