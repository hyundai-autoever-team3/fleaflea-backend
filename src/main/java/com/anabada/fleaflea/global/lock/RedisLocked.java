package com.anabada.fleaflea.global.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisLocked {

    /** SpEL returning one key or a collection of keys; duplicates are removed and keys sorted. */
    String key();
}
