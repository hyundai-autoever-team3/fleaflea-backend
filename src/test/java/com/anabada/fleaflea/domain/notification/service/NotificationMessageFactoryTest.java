package com.anabada.fleaflea.domain.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMessageFactoryTest {

    private static final int MESSAGE_COLUMN_LENGTH = 255;

    private static final int MAX_NICKNAME_LENGTH = 50;

    private static final int MAX_TARGET_NAME_LENGTH = 150;

    @Test
    @DisplayName("모든 메시지 템플릿은 최대 길이 입력에서도 컬럼 길이를 넘지 않는다")
    void allMessages_fitWithinColumnLength() throws Exception {
        List<String> tooLong = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();

        for (Method method : messageFactoryMethods()) {
            String message = (String) method.invoke(null, (Object[]) argumentsFor(method));

            lengths.add(message.length());

            if (message.length() > MESSAGE_COLUMN_LENGTH) {
                tooLong.add("%s → %d자".formatted(method.getName(), message.length()));
            }
        }

        assertThat(lengths).isNotEmpty();
        assertThat(tooLong)
                .as("메시지가 %d자를 초과하는 템플릿", MESSAGE_COLUMN_LENGTH)
                .isEmpty();

        int worst = lengths.stream().max(Comparator.naturalOrder()).orElseThrow();
        System.out.printf(
                "메시지 템플릿 %d개 검사, 최댓값 %d자 (여유 %d자)%n",
                lengths.size(), worst, MESSAGE_COLUMN_LENGTH - worst
        );
    }

    private List<Method> messageFactoryMethods() {
        List<Method> methods = new ArrayList<>();

        for (Method method : NotificationMessageFactory.class.getDeclaredMethods()) {
            boolean isPublicStaticStringFactory =
                    Modifier.isPublic(method.getModifiers())
                            && Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() == String.class
                            && method.getParameterCount() > 0;

            if (isPublicStaticStringFactory) {
                methods.add(method);
            }
        }

        return methods;
    }

    private String[] argumentsFor(Method method) {
        if (method.getParameterCount() == 1) {
            return new String[]{"t".repeat(MAX_TARGET_NAME_LENGTH)};
        }

        return new String[]{
                "n".repeat(MAX_NICKNAME_LENGTH),
                "t".repeat(MAX_TARGET_NAME_LENGTH)
        };
    }
}
