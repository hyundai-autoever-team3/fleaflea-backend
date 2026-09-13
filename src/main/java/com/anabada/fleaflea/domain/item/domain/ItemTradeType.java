package com.anabada.fleaflea.domain.item.domain;

public enum ItemTradeType {
    SALE,
    GIVEAWAY,
    RENTAL,
    SWAP // TODO 박현제: SWAP은 Item 테이블에는 없으나 다른 테이블에서 이 값이 사용될 가능성 있음. 필요없으면 이후에 제거 예정
}