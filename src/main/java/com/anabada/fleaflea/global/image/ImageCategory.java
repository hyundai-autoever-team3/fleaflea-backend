package com.anabada.fleaflea.global.image;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageCategory {

    PROFILE("profiles"),
    COLLECTION_ITEM("collection-items"),
    ITEM("items");

    private final String prefix;
}