package dev.keshav.wireframe.router;

import dev.keshav.wireframe.http.HttpMethod;
import dev.keshav.wireframe.http.HttpRequest;
import dev.keshav.wireframe.http.HttpResponse;

import java.util.HashMap;
import java.util.Map;

public class Router {

    Map<String, Handler> routeMap = new HashMap<>();

    public void register(HttpMethod method, String path, Handler handler){
        String key = method.name() + path;
        routeMap.put(key,handler);
    }

    public HttpResponse route(HttpRequest request){

        String key = request.method().name() + request.path();

        Handler handler = routeMap.get(key);

        if (handler ==  null){
            return HttpResponse.notFound();
        }

        return handler.handle(request);

    }

}
