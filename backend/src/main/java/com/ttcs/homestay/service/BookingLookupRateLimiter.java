package com.ttcs.homestay.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * S2-08 Lát 2: tối đa 10 lần tra cứu sai từ một địa chỉ IP trong 15 phút.
 * Đủ 10 lần sai thì mọi lượt tra cứu từ IP đó bị từ chối, kể cả nhập đúng,
 * cho tới khi lần sai cũ nhất ra khỏi khoảng 15 phút.
 */
@Component
public class BookingLookupRateLimiter {

    static final int MAX_FAILURES = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public BookingLookupRateLimiter() {
        this(Clock.systemUTC());
    }

    BookingLookupRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Thời điểm IP được tra cứu lại nếu đang bị chặn; rỗng nếu được tra cứu. */
    public Optional<Instant> blockedUntil(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return Optional.empty();
        }
        Deque<Instant> timestamps = failures.get(ipAddress);
        if (timestamps == null) {
            return Optional.empty();
        }
        synchronized (timestamps) {
            removeExpired(timestamps);
            if (timestamps.size() >= MAX_FAILURES) {
                return Optional.of(timestamps.peekFirst().plus(WINDOW));
            }
            return Optional.empty();
        }
    }

    public void recordFailure(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return;
        }
        Deque<Instant> timestamps = failures.computeIfAbsent(ipAddress, key -> new ArrayDeque<>());
        synchronized (timestamps) {
            removeExpired(timestamps);
            timestamps.addLast(clock.instant());
        }
    }

    public void clear() {
        failures.clear();
    }

    private void removeExpired(Deque<Instant> timestamps) {
        Instant windowStart = clock.instant().minus(WINDOW);
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(windowStart)) {
            timestamps.pollFirst();
        }
    }
}