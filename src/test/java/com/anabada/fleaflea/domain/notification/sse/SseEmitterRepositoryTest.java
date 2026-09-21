package com.anabada.fleaflea.domain.notification.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterRepositoryTest {

    private final SseEmitterRepository repository = new SseEmitterRepository();

    @Test
    @DisplayName("한 회원이 탭을 여러 개 열면 연결이 모두 보관되고 전부 조회된다")
    void save_multipleTabsForSameMember() {
        SseEmitter tabA = new SseEmitter();
        SseEmitter tabB = new SseEmitter();
        SseEmitter tabC = new SseEmitter();

        repository.save(1L, "1_a", tabA);
        repository.save(1L, "1_b", tabB);
        repository.save(1L, "1_c", tabC);

        assertThat(repository.findAllByMemberId(1L))
                .extracting(Map.Entry::getValue)
                .containsExactlyInAnyOrder(tabA, tabB, tabC);

        assertThat(repository.countConnections()).isEqualTo(3);
    }

    @Test
    @DisplayName("탭 하나를 닫아도 나머지 탭의 연결은 유지된다")
    void delete_keepsRemainingTabs() {
        SseEmitter tabA = new SseEmitter();
        SseEmitter tabB = new SseEmitter();

        repository.save(1L, "1_a", tabA);
        repository.save(1L, "1_b", tabB);

        repository.delete(1L, "1_a");

        assertThat(repository.findAllByMemberId(1L))
                .extracting(Map.Entry::getValue)
                .containsExactly(tabB);
    }

    @Test
    @DisplayName("회원별로 연결이 격리된다")
    void findAllByMemberId_isolatesMembers() {
        SseEmitter mine = new SseEmitter();
        SseEmitter others = new SseEmitter();

        repository.save(1L, "1_a", mine);
        repository.save(2L, "2_a", others);

        assertThat(repository.findAllByMemberId(1L))
                .extracting(Map.Entry::getValue)
                .containsExactly(mine);
    }

    @Test
    @DisplayName("마지막 탭이 닫히면 회원 키까지 정리된다")
    void delete_removesMemberKeyWhenEmpty() {
        repository.save(1L, "1_a", new SseEmitter());

        repository.delete(1L, "1_a");

        assertThat(repository.countConnections()).isZero();
        assertThat(repository.findAllByMemberId(1L)).isEmpty();
    }

    @Test
    @DisplayName("탭을 닫는 동시에 새 탭을 열어도 새 연결이 유실되지 않는다")
    void save_isNotLostWhenConcurrentlyDeleting() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < 2_000; i++) {
                String closingId = "1_closing_" + i;
                String openingId = "1_opening_" + i;

                repository.save(1L, closingId, new SseEmitter());

                CountDownLatch start = new CountDownLatch(1);

                executor.submit(() -> {
                    await(start);
                    repository.delete(1L, closingId);
                });
                executor.submit(() -> {
                    await(start);
                    repository.save(1L, openingId, new SseEmitter());
                });

                start.countDown();

                long deadline = System.currentTimeMillis() + 1_000;
                boolean found = false;
                while (System.currentTimeMillis() < deadline) {
                    found = repository.findAllByMemberId(1L).stream()
                            .anyMatch(entry -> entry.getKey().equals(openingId));
                    if (found) {
                        break;
                    }
                }

                assertThat(found)
                        .as("새로 연 탭의 연결이 유실됐다 (i=%d)", i)
                        .isTrue();

                repository.delete(1L, openingId);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
