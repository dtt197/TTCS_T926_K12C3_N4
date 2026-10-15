package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationEventServiceTest {

    @Test
    void sendsOnlyToSpecifiedRecipientsAndDoesNotExposeIncidentDetails() {
        List<RecordingEmitter> emitters = new ArrayList<>();
        NotificationEventService service = new NotificationEventService(timeout -> {
            RecordingEmitter emitter = new RecordingEmitter(timeout);
            emitters.add(emitter);
            return emitter;
        });
        service.connect(10L);
        service.connect(20L);

        service.publish(List.of(10L));

        assertThat(emitters.get(0).sent).hasSize(1);
        assertThat(NotificationEventService.EVENT_NAME).isEqualTo("inbox-changed");
        assertThat(NotificationEventService.EVENT_PAYLOAD).isEqualTo("changed");
        assertThat(emitters.get(1).sent).isEmpty();
    }

    @Test
    void failedSendRemovesConnectionAndDoesNotThrow() {
        AtomicInteger sequence = new AtomicInteger();
        RecordingEmitter failed = new RecordingEmitter(1000L);
        failed.fail = true;
        NotificationEventService service = new NotificationEventService(timeout ->
                sequence.getAndIncrement() == 0 ? failed : new RecordingEmitter(timeout));
        service.connect(10L);
        service.publish(List.of(10L));
        assertThat(service.connectionCount(10L)).isZero();
        service.publish(List.of(10L));
    }

    @Test
    void timeoutAndDisconnectCleanupRemoveEmptyUserEntry() {
        RecordingEmitter emitter = new RecordingEmitter(1000L);
        NotificationEventService service = new NotificationEventService(timeout -> emitter);
        service.connect(10L);
        emitter.timeoutCallback.run();
        assertThat(service.connectionCount(10L)).isZero();
        service.heartbeat();
        assertThat(service.connectedUserIds()).isEmpty();

        RecordingEmitter disconnected = new RecordingEmitter(1000L);
        NotificationEventService secondService = new NotificationEventService(timeout -> disconnected);
        secondService.connect(20L);
        disconnected.completion.run();
        assertThat(secondService.connectedUserIds()).isEmpty();
    }

    private static final class RecordingEmitter extends SseEmitter {
        private final List<SseEmitter.SseEventBuilder> sent = new ArrayList<>();
        private Runnable completion;
        private Runnable timeoutCallback;
        private Consumer<Throwable> errorCallback;
        private boolean fail;

        private RecordingEmitter(long timeout) {
            super(timeout);
        }

        @Override
        public void send(SseEmitter.SseEventBuilder builder) throws IOException {
            if (fail) {
                throw new IOException("disconnected");
            }
            sent.add(builder);
        }

        @Override
        public void onCompletion(Runnable callback) {
            completion = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeoutCallback = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            errorCallback = callback;
        }
    }
}
