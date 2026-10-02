package com.ttcs.homestay.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BookingRateLimiter {

    private static final int MAX_REQUESTS = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final ConcurrentHashMap<String, Deque<Instant>> requests =
            new ConcurrentHashMap<>();

    public boolean allow(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return true;
        }

        Instant now = Instant.now();

        Deque<Instant> timestamps =
                requests.computeIfAbsent(ipAddress, key -> new ArrayDeque<>());

        synchronized (timestamps) {

            Instant limit = now.minus(WINDOW);

            while (!timestamps.isEmpty()
                    && timestamps.peekFirst().isBefore(limit)) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= MAX_REQUESTS) {
                return false;
            }

            timestamps.addLast(now);
            return true;
        }
    }

    public void clear() {
        requests.clear();
    }
}