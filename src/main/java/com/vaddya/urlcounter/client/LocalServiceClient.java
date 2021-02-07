package com.vaddya.urlcounter.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.vaddya.urlcounter.local.UrlCounter;
import org.jetbrains.annotations.NotNull;

public final class LocalServiceClient implements ServiceClient {
    private final UrlCounter counter;
    private final Executor executor;

    public LocalServiceClient(@NotNull final UrlCounter counter) {
        this.counter = counter;
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @Override
    @NotNull
    public CompletableFuture<Void> addAsync(@NotNull final String domain) {
        return CompletableFuture.runAsync(() -> counter.add(domain), executor);
    }

    @Override
    public @NotNull CompletableFuture<List<String>> topAsync(int n) {
        return CompletableFuture.supplyAsync(() -> counter.top(n), executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Map<String, Integer>> topCountAsync(final int n) {
        return CompletableFuture.supplyAsync(() -> counter.topCount(n), executor);
    }
}
