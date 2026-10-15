 package com.anabada.fleaflea.domain.notification.sse;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class SseEmitterRepository {

    private final Map<Long, Map<String, SseConnection>> emitters =
            new ConcurrentHashMap<>();

    public void save(
            Long memberId,
            String emitterId,
            SseEmitter emitter,
            Instant expiresAt
    ) {
        emitters.compute(memberId, (key, memberEmitters) -> {
            Map<String, SseConnection> target =
                    (memberEmitters == null) ? new ConcurrentHashMap<>() : memberEmitters;

            target.put(emitterId, SseConnection.of(emitter, expiresAt));
            return target;
        });
    }

    public void delete(
            Long memberId,
            String emitterId
    ) {
        emitters.computeIfPresent(memberId, (key, memberEmitters) -> {
            memberEmitters.remove(emitterId);
            return memberEmitters.isEmpty() ? null : memberEmitters;
        });
    }

    public Collection<Map.Entry<String, SseEmitter>> findAllByMemberId(Long memberId) {
        return emitters.getOrDefault(memberId, Map.of()).entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().emitter()))
                .toList();
    }

    public boolean isActive(Long memberId, String emitterId, Instant now) {
        SseConnection connection = emitters.getOrDefault(memberId, Map.of()).get(emitterId);
        return connection != null && connection.expiresAt().isAfter(now);
    }

    public Collection<SseEmitter> removeAllByMemberId(Long memberId) {
        Map<String, SseConnection> removed = emitters.remove(memberId);
        return removed == null ? List.of() : removed.values().stream().map(SseConnection::emitter).toList();
    }

    public Collection<Map.Entry<Long, Map.Entry<String, SseEmitter>>> findAll() {
        return emitters.entrySet().stream()
                .flatMap(memberEntry -> memberEntry.getValue().entrySet().stream()
                        .map(emitterEntry -> Map.entry(memberEntry.getKey(),
                                Map.entry(emitterEntry.getKey(), emitterEntry.getValue().emitter()))))
                .toList();
    }

    public int countConnections() {
        return emitters.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    private record SseConnection(SseEmitter emitter, Instant expiresAt) {
        private static SseConnection of(SseEmitter emitter, Instant expiresAt) {
            return new SseConnection(emitter, expiresAt);
        }
    }
}
