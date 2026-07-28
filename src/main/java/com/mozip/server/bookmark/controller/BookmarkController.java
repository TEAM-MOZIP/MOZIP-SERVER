package com.mozip.server.bookmark.controller;

import com.mozip.server.bookmark.dto.BookmarkCreateRequest;
import com.mozip.server.bookmark.dto.BookmarkResponse;
import com.mozip.server.bookmark.service.BookmarkService;
import com.mozip.server.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bookmark", description = "정책 북마크 API")
@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @Operation(summary = "정책 북마크 등록", description = "인증된 사용자가 정책을 북마크에 등록한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookmarkResponse addBookmark(@AuthenticationPrincipal String userId,
                                         @RequestBody @Valid BookmarkCreateRequest request) {
        return bookmarkService.addBookmark(Long.valueOf(userId), request.policyId());
    }

    @Operation(summary = "내 북마크 목록 조회", description = "인증된 사용자 본인의 북마크 목록을 페이지 단위로 조회한다.")
    @GetMapping
    public PageResponse<BookmarkResponse> getMyBookmarks(
            @AuthenticationPrincipal String userId,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return bookmarkService.getMyBookmarks(Long.valueOf(userId), pageable);
    }

    @Operation(summary = "정책 북마크 해제", description = "인증된 사용자 본인의 정책 북마크를 해제한다. 대상이 없어도 동일하게 처리한다.")
    @DeleteMapping("/{policyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeBookmark(@AuthenticationPrincipal String userId, @PathVariable Long policyId) {
        bookmarkService.removeBookmark(Long.valueOf(userId), policyId);
    }
}
