package com.vaddya.urlcounter;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.vaddya.urlcounter.client.LocalServiceClient;
import com.vaddya.urlcounter.client.RemoteServiceClient;
import com.vaddya.urlcounter.client.ServiceClient;
import com.vaddya.urlcounter.local.UrlCounter;
import com.vaddya.urlcounter.topology.Topology;
import org.codehaus.jackson.map.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

public final class UrlCounterServer extends HttpServer {
    private static final Logger log = LoggerFactory.getLogger(UrlCounterServer.class);

    private final Topology topology;
    private final Map<String, ServiceClient> clients = new HashMap<>();
    private final ObjectMapper json = new ObjectMapper();

    public UrlCounterServer(
            @NotNull final HttpServerConfig config,
            @NotNull final Topology topology,
            @NotNull final UrlCounter counter) throws IOException {
        super(config);
        this.topology = topology;
        for (String node : topology.all()) {
            if (node.equals(topology.me())) {
                clients.put(node, new LocalServiceClient(counter));
            } else {
                clients.put(node, new RemoteServiceClient(node, json));
            }
        }
    }

    @Override
    public void handleDefault(
            @NotNull final Request request,
            @NotNull final HttpSession session) throws IOException {
        final String[] path = request.getPath().split("/", 3);
        if (path.length < 3) {
            session.sendError(Response.BAD_REQUEST, "");
            return;
        }
        switch (path[1]) {
            case Endpoints.ADD:
                handleAdd(session, path[2]);
                break;
            case Endpoints.TOP:
                handleTop(session, path[2]);
                break;
            case Endpoints.COUNTS:
                handleCounts(session, path[2]);
                break;
            default:
                session.sendError(Response.BAD_REQUEST, "Bad request");
        }
    }

    private void handleAdd(
            @NotNull final HttpSession session,
            @NotNull final String url) throws IOException {
        final String domain = Utils.extractDomain(url);
        if (domain == null) {
            log.error("Illegal URL: {}", url);
            session.sendError(Response.BAD_REQUEST, "Illegal URL: " + url);
            return;
        }
        final String node = topology.primaryFor(domain);
        final ServiceClient client = clients.get(node);
        client.addAsync(domain)
                .thenApply(x -> new Response(Response.OK, Response.EMPTY))
                .thenAccept(response -> sendResponse(session, response));
    }

    private void handleTop(
            @NotNull final HttpSession session,
            @NotNull final String number) throws IOException {
        int n;
        try {
            n = Integer.parseInt(number);
        } catch (NumberFormatException e) {
            log.error("Illegal number: {}", number);
            session.sendError(Response.BAD_REQUEST, "Illegal number: " + number);
            return;
        }
        final List<CompletableFuture<Map<String, Integer>>> futures = clients.values()
                .stream()
                .map(client -> client.topCountAsync(n))
                .collect(Collectors.toList());
        Utils.join(futures)
                .thenApply(results -> mergeResults(results, n))
                .thenAccept(response -> sendResponse(session, response));
    }

    private void handleCounts(
            @NotNull final HttpSession session,
            @NotNull final String number) throws IOException {
        final int n;
        try {
            n = Integer.parseInt(number);
        } catch (NumberFormatException e) {
            log.error("Illegal number: {}", number);
            session.sendError(Response.BAD_REQUEST, "Illegal number: " + number);
            return;
        }
        final ServiceClient client = clients.get(topology.me());
        client.topCountAsync(n)
                .thenApply(result -> Response.ok(serialize(result)))
                .thenAccept(response -> sendResponse(session, response));
    }

    @NotNull
    private Response mergeResults(
            @NotNull final List<Map<String, Integer>> results,
            final int n) {
        final List<String> result = results.stream()
                .flatMap(entries -> entries.entrySet().stream())
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .limit(n)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        final byte[] body = serialize(result);
        return Response.ok(body);
    }

    private void sendResponse(
            @NotNull final HttpSession session,
            @NotNull final Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            log.error("Cannot send response", e);
        }
    }

    @NotNull
    private byte[] serialize(@NotNull final Object object) {
        try {
            return json.writeValueAsBytes(object);
        } catch (IOException e) {
            log.error("Cannot serialize", e);
            return null;
        }
    }
}
