package com.campusplus.backend.dto.comment;
public record InteractionSummary(Long postId,long commentCount,long likeCount,boolean likedByMe) {}
