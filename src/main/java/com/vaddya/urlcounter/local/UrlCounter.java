package com.vaddya.urlcounter.local;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

public interface UrlCounter {
    void add(String domain);

    @NotNull
    List<String> top(int n);

    @NotNull
    Map<String, Integer> topCount(int n);
}
