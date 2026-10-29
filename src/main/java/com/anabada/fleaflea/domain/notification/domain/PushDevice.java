package com.anabada.fleaflea.domain.notification.domain;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.notification.exception.PushDeviceNotOwnerException;
import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "push_devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDevice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pushDeviceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, unique = true, length = 128)
    private String fid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PushPlatform platform;

    @Column(nullable = false)
    private UUID registrationVersion;

    public void validateOwner(Long memberId) {
        if (!member.getMemberId().equals(memberId)) {
            throw new PushDeviceNotOwnerException();
        }
    }
}
