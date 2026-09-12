package com.anabada.fleaflea.domain.begrequest.domain;

import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "beg_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BegRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long begId;

    @Column(nullable = false)
    private Long collectionItemId;

    @Column(nullable = false)
    private Long applicantId;

    @Column(columnDefinition = "TEXT")
    private String story;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BegRequestStatus status;
}