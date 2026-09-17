package com.anabada.fleaflea.domain.member.service;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.dto.*;
import com.anabada.fleaflea.domain.member.exception.*;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.image.ImageCategory;
import com.anabada.fleaflea.global.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageService imageService;

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        String profileImageUrl = imageService.getUrl(
                member.getProfileImageKey()
        );

        return MyProfileResponse.from(member, profileImageUrl);
    }

    @Transactional
    public void updateMyProfile(Long memberId, ProfileUpdateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        boolean nicknameChanged =
                !member.getNickname().equals(request.nickname());

        if (nicknameChanged && memberRepository.existsByNickname(request.nickname())) {
            throw new MemberNicknameDuplicateException();
        }

        boolean imageChanged =
                request.profileImage() != null
                        && !request.profileImage().isEmpty();

        if (request.deleteProfileImage() && imageChanged) {
            throw new InvalidProfileImageRequestException();
        }

        boolean imageDeleted = request.deleteProfileImage()
                && member.getProfileImageKey() != null;


        if (!nicknameChanged && !imageChanged && !imageDeleted) {
            throw new ProfileNotChangedException();
        }


        String profileImageKey = member.getProfileImageKey();

        if (imageDeleted){
            imageService.delete(profileImageKey);
            profileImageKey = null;
        }

        if (imageChanged) {
            if (profileImageKey == null) {
                profileImageKey = imageService.upload(
                        request.profileImage(),
                        ImageCategory.PROFILE
                );
            } else {
                profileImageKey = imageService.replace(
                        profileImageKey,
                        request.profileImage(),
                        ImageCategory.PROFILE
                );
            }
        }

        member.updateProfile(
                request.nickname(),
                profileImageKey
        );
    }

    @Transactional
    public void updatePassword(Long memberId, PasswordUpdateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);
        if (!passwordEncoder.matches(
                request.currentPassword(),
                member.getPassword()
        )) {
            throw new PasswordMismatchException();
        }
        member.updatePassword(
                passwordEncoder.encode(request.newPassword())
        );

    }

    @Transactional
    public void deleteMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);
        memberRepository.delete(member);
    }

    @Transactional(readOnly = true)
    public SearchMemberResponse searchMember(String nickname) {
        Member member = memberRepository.findByNickname(nickname)
                .orElseThrow(MemberNotFoundException::new);

        return SearchMemberResponse.from(
                member.getMemberId(),
                member.getNickname()
        );

    }
}
