package com.gsmv.ai;

import com.gsmv.ai.dto.AssistantAiDtos;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

@Component
public class AssistantQueryCache {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_ENTRIES = 256;

    private final ConcurrentHashMap<String, CacheEntry> entries = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> order = new ConcurrentLinkedDeque<>();

    public AssistantAiDtos.ChatResponse get(String key) {
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            entries.remove(key, entry);
            return null;
        }
        return entry.response();
    }

    public void put(String key, AssistantAiDtos.ChatResponse response) {
        if (key == null || response == null) {
            return;
        }
        entries.put(key, new CacheEntry(response, System.currentTimeMillis() + TTL.toMillis()));
        order.addLast(key);
        cleanupOverflow();
    }

    public void invalidateAll() {
        entries.clear();
        order.clear();
    }

    private void cleanupOverflow() {
        while (entries.size() > MAX_ENTRIES) {
            String oldestKey = order.pollFirst();
            if (oldestKey == null) {
                break;
            }
            entries.remove(oldestKey);
        }
    }

    private record CacheEntry(
            AssistantAiDtos.ChatResponse response,
            long expireAtMillis
    ) {
        private CacheEntry {
            Objects.requireNonNull(response, "response");
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expireAtMillis;
        }
    }
}
