 package com.anabada.fleaflea.domain.notification.sse;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class SseEmitterRepository {

    private final Map<Long, Map<String, SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    public void save(
            Long memberId,
            String emitterId,
            SseEmitter emitter
    ) {
        emitters.compute(memberId, (key, memberEmitters) -> {
            Map<String, SseEmitter> target =
                    (memberEmitters == null) ? new ConcurrentHashMap<>() : memberEmitters;

            target.put(emitterId, emitter);
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
        return List.copyOf(
                emitters.getOrDefault(memberId, Map.of())
                        .entrySet()
        );
    }

    public Collection<Map.Entry<Long, Map.Entry<String, SseEmitter>>> findAll() {
        return emitters.entrySet().stream()
                .flatMap(memberEntry -> memberEntry.getValue().entrySet().stream()
                        .map(emitterEntry -> Map.entry(memberEntry.getKey(), emitterEntry)))
                .toList();
    }

    public int countConnections() {
        return emitters.values().stream()
                .mapToInt(Map::size)
                .sum();
    }
}
