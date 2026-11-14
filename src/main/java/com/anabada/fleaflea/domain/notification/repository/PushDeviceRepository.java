package com.anabada.fleaflea.domain.notification.repository;

import com.anabada.fleaflea.domain.notification.domain.PushDevice;
import com.anabada.fleaflea.domain.notification.push.PushTarget;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    long countByMember_MemberId(Long memberId);

    boolean existsByFidAndMember_MemberId(String fid, Long memberId);

    // 사전 조회 후 저장하면 동일 FID의 동시 등록을 막을 수 없으므로 DB에서 원자적으로 갱신한다.
    @Modifying
    @Query(value = """
            INSERT INTO push_devices (member_id, fid, platform, registration_version, created_at, updated_at)
            VALUES (:memberId, :fid, :platform, :version, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (fid) DO UPDATE SET member_id = EXCLUDED.member_id,
                platform = EXCLUDED.platform, registration_version = EXCLUDED.registration_version,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void upsert(
            @Param("memberId") Long memberId,
            @Param("fid") String fid,
            @Param("platform") String platform,
            @Param("version") UUID registrationVersion
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PushDevice d where d.fid = :fid")
    Optional<PushDevice> findLockedByFid(@Param("fid") String fid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PushDevice d where d.pushDeviceId = :deviceId")
    Optional<PushDevice> findLockedByDeviceId(@Param("deviceId") Long deviceId);

    @Query("""
            select new com.anabada.fleaflea.domain.notification.push.PushTarget(
                d.pushDeviceId, d.member.memberId, d.fid, d.registrationVersion)
            from PushDevice d where d.member.memberId = :memberId
            """)
    List<PushTarget> findTargets(@Param("memberId") Long memberId);

    @Modifying
    @Query("""
            delete from PushDevice d where d.pushDeviceId = :deviceId
            and d.member.memberId = :memberId and d.registrationVersion = :version
            """)
    int deleteUnregistered(
            @Param("deviceId") Long pushDeviceId,
            @Param("memberId") Long memberId,
            @Param("version") UUID registrationVersion
    );
}
