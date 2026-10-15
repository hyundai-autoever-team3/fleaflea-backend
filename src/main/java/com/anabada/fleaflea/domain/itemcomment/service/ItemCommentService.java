package com.anabada.fleaflea.domain.itemcomment.service;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.exception.ItemNotFoundException;
import com.anabada.fleaflea.domain.item.repository.ItemRepository;
import com.anabada.fleaflea.domain.itemcomment.domain.ItemComment;
import com.anabada.fleaflea.domain.itemcomment.dto.ItemCommentCreateRequest;
import com.anabada.fleaflea.domain.itemcomment.dto.ItemCommentResponse;
import com.anabada.fleaflea.domain.itemcomment.dto.ItemCommentUpdateRequest;
import com.anabada.fleaflea.domain.itemcomment.exception.ItemCommentInvalidParentException;
import com.anabada.fleaflea.domain.itemcomment.exception.ItemCommentNotFoundException;
import com.anabada.fleaflea.domain.itemcomment.repository.ItemCommentRepository.ReplyCount;
import com.anabada.fleaflea.domain.itemcomment.repository.ItemCommentRepository;
import com.anabada.fleaflea.domain.market.exception.MarketNotParticipantException;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.dto.PageResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemCommentService {

    private final ItemRepository itemRepository;
    private final ItemCommentRepository commentRepository;
    private final MarketMemberRepository marketMemberRepository;
    private final MemberRepository memberRepository;

    public PageResponse<ItemCommentResponse> findComments(
            Long itemId,
            Long memberId,
            Long parentId,
            int page,
            int size
    ) {
        Item item = findItem(itemId, memberId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt", "commentId").ascending());
        Page<ItemComment> comments;
        if (parentId == null) {
            comments = commentRepository.findRoots(itemId, pageable);
        } else {
            ItemComment parent = findComment(itemId, parentId);
            if (parent.getParentId() != null) {
                throw new ItemCommentInvalidParentException();
            }
            comments = commentRepository.findByItemIdAndParentIdAndDeletedFalse(itemId, parentId, pageable);
        }

        List<Long> authorIds = comments.stream()
                .filter(comment -> !comment.isDeleted())
                .map(ItemComment::getAuthorId)
                .distinct()
                .toList();
        Map<Long, Member> authors = memberRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Member::getMemberId, Function.identity()));
        Map<Long, Long> replyCounts = new HashMap<>();
        if (parentId == null && !comments.isEmpty()) {
            List<Long> parentIds = comments.stream().map(ItemComment::getCommentId).toList();
            for (ReplyCount count : commentRepository.countReplies(parentIds)) {
                replyCounts.put(count.getParentId(), count.getReplyCount());
            }
        }
        return PageResponse.from(comments.map(comment -> ItemCommentResponse.from(
                comment,
                authors.get(comment.getAuthorId()),
                item.getSeller().getMemberId(),
                memberId,
                replyCounts.getOrDefault(comment.getCommentId(), 0L)
        )));
    }

    @Transactional
    public ItemCommentResponse createComment(
            Long itemId,
            Long memberId,
            ItemCommentCreateRequest request
    ) {
        Item item = findLockedItem(itemId, memberId);
        if (request.parentId() != null) {
            findComment(itemId, request.parentId()).validateReplyParent();
        }
        ItemComment comment = commentRepository.save(ItemComment.create(
                itemId,
                memberId,
                request.parentId(),
                request.content().strip()
        ));
        return response(comment, item, memberId);
    }

    @Transactional
    public ItemCommentResponse updateComment(
            Long itemId,
            Long commentId,
            Long memberId,
            ItemCommentUpdateRequest request
    ) {
        Item item = findLockedItem(itemId, memberId);
        ItemComment comment = findComment(itemId, commentId);
        comment.validateOwner(memberId);
        comment.update(request.content().strip());
        commentRepository.flush();
        return response(comment, item, memberId);
    }

    @Transactional
    public void deleteComment(
            Long itemId,
            Long commentId,
            Long memberId
    ) {
        findLockedItem(itemId, memberId);
        ItemComment comment = findComment(itemId, commentId);
        comment.validateOwner(memberId);
        comment.delete();
    }

    private Item findItem(Long itemId, Long memberId) {
        Item item = itemRepository.findById(itemId).orElseThrow(ItemNotFoundException::new);
        validateParticipant(item, memberId);
        return item;
    }

    private Item findLockedItem(Long itemId, Long memberId) {
        // 상품 삭제와 댓글 변경이 같은 행을 잠가 부모 삭제와 답글 작성도 직렬화한다.
        Item item = itemRepository.findLockedByItemId(itemId).orElseThrow(ItemNotFoundException::new);
        validateParticipant(item, memberId);
        return item;
    }

    private void validateParticipant(Item item, Long memberId) {
        if (!marketMemberRepository.existsByMarket_MarketIdAndMember_MemberId(
                item.getMarket().getMarketId(), memberId
        )) {
            throw new MarketNotParticipantException();
        }
    }

    private ItemComment findComment(Long itemId, Long commentId) {
        return commentRepository.findByCommentIdAndItemId(commentId, itemId)
                .orElseThrow(ItemCommentNotFoundException::new);
    }

    private ItemCommentResponse response(
            ItemComment comment,
            Item item,
            Long memberId
    ) {
        Member author = memberRepository.findById(comment.getAuthorId()).orElse(null);
        long replyCount = 0;
        if (comment.getParentId() == null) {
            List<ReplyCount> counts = commentRepository.countReplies(List.of(comment.getCommentId()));
            if (!counts.isEmpty()) {
                replyCount = counts.getFirst().getReplyCount();
            }
        }
        return ItemCommentResponse.from(comment, author, item.getSeller().getMemberId(), memberId, replyCount);
    }
}
