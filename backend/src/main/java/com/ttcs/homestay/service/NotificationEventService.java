package com.ttcs.homestay.service;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.LongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Routes payload-free inbox change hints only to the notification recipients. */
@Service
public class NotificationEventService {

    static final long STREAM_TIMEOUT_MILLIS = 15 * 60 * 1000L;
    private static final Logger log = LoggerFactory.getLogger(NotificationEventService.class);
    private final ConcurrentHashMap<Long, Set<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    private final LongFunction<SseEmitter> emitterFactory;
    static final String EVENT_NAME = "inbox-changed";
    static final String EVENT_PAYLOAD = "changed";

    public NotificationEventService() {
        this(timeout -> new SseEmitter(timeout));
    }

    NotificationEventService(LongFunction<SseEmitter> emitterFactory) {
        this.emitterFactory = emitterFactory;
    }

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = emitterFactory.apply(STREAM_TIMEOUT_MILLIS);
        Set<SseEmitter> userEmitters = emittersByUser.computeIfAbsent(
                userId, ignored -> new CopyOnWriteArraySet<>());
        userEmitters.add(emitter);
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(ignored -> cleanup.run());
        return emitter;
    }

    public void publish(List<Long> recipientIds) {
        for (Long recipientId : recipientIds) {
            Set<SseEmitter> userEmitters = emittersByUser.get(recipientId);
            if (userEmitters == null) {
                continue;
            }
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(EVENT_NAME)
                            .data(EVENT_PAYLOAD));
                } catch (IOException | IllegalStateException exception) {
                    remove(recipientId, emitter);
                    try {
                        emitter.completeWithError(exception);
                    } catch (RuntimeException ignored) {
                        // A response may already be closed; cleanup above is sufficient.
                    }
                    log.debug("Removing failed notification SSE connection for user {}", recipientId);
                } catch (RuntimeException exception) {
                    // Delivery is best effort after commit; it must not affect the business transaction.
                    remove(recipientId, emitter);
                    log.warn("Removing notification SSE connection after delivery failure for user {}", recipientId);
                }
            }
        }
    }

    public void heartbeat() {
        emittersByUser.forEach((userId, userEmitters) -> {
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException exception) {
                    remove(userId, emitter);
                    try {
                        emitter.completeWithError(exception);
                    } catch (RuntimeException ignored) {
                        // A response may already be closed; cleanup above is sufficient.
                    }
                } catch (RuntimeException exception) {
                    remove(userId, emitter);
                    log.warn("Removing notification SSE connection after heartbeat failure for user {}", userId);
                }
            }
        });
    }

    private void remove(Long userId, SseEmitter emitter) {
        emittersByUser.computeIfPresent(userId, (ignored, userEmitters) -> {
            userEmitters.remove(emitter);
            return userEmitters.isEmpty() ? null : userEmitters;
        });
    }

    int connectionCount(Long userId) {
        Set<SseEmitter> userEmitters = emittersByUser.get(userId);
        return userEmitters == null ? 0 : userEmitters.size();
    }

    Set<Long> connectedUserIds() {
        return Set.copyOf(emittersByUser.keySet());
    }
}
