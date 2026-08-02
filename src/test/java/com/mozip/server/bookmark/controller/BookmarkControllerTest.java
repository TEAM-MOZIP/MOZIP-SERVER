package com.mozip.server.bookmark.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mozip.server.auth.config.CustomAccessDeniedHandler;
import com.mozip.server.auth.config.CustomAuthenticationEntryPoint;
import com.mozip.server.auth.config.SecurityConfig;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import com.mozip.server.bookmark.dto.BookmarkCreateRequest;
import com.mozip.server.bookmark.dto.BookmarkResponse;
import com.mozip.server.bookmark.exception.BookmarkAlreadyExistsException;
import com.mozip.server.bookmark.service.BookmarkService;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.dto.PolicyAvailabilityResponse;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookmarkController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class BookmarkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private BookmarkService bookmarkService;

    @Test
    void 인증_없이_등록하면_401이다() throws Exception {
        mockMvc.perform(post("/api/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookmarkCreateRequest(1L))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 등록에_성공하면_201이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        BookmarkResponse response = new BookmarkResponse(
                10L, 1L, "청년내일채움공제", "고용노동부",
                LocalDate.now(), LocalDate.now().plusMonths(3),
                new PolicyAvailabilityResponse(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD, false),
                LocalDateTime.now());
        when(bookmarkService.addBookmark(eq(1L), eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/bookmarks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookmarkCreateRequest(1L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookmarkId").value(10))
                .andExpect(jsonPath("$.policyId").value(1))
                .andExpect(jsonPath("$.title").value("청년내일채움공제"))
                .andExpect(jsonPath("$.availability.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.availability.closingSoon").value(false));
    }

    @Test
    void 중복_등록이면_409다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(bookmarkService.addBookmark(eq(1L), eq(1L)))
                .thenThrow(new BookmarkAlreadyExistsException(1L, 1L));

        mockMvc.perform(post("/api/bookmarks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookmarkCreateRequest(1L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKMARK_ALREADY_EXISTS"));
    }

    @Test
    void 존재하지_않는_정책이면_404다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(bookmarkService.addBookmark(eq(1L), eq(999L)))
                .thenThrow(new PolicyNotFoundException(999L));

        mockMvc.perform(post("/api/bookmarks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookmarkCreateRequest(999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
    }

    @Test
    void policyId가_없으면_400이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");

        mockMvc.perform(post("/api/bookmarks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookmarkCreateRequest(null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 목록_조회에_성공한다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        BookmarkResponse item = new BookmarkResponse(
                10L, 1L, "청년내일채움공제", "고용노동부",
                LocalDate.now(), LocalDate.now().plusMonths(3),
                new PolicyAvailabilityResponse(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD, true),
                LocalDateTime.now());
        PageResponse<BookmarkResponse> page = new PageResponse<>(List.of(item), 0, 20, 1, 1, true, true);
        when(bookmarkService.getMyBookmarks(eq(1L), org.mockito.ArgumentMatchers.any())).thenReturn(page);

        mockMvc.perform(get("/api/bookmarks")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].policyId").value(1))
                .andExpect(jsonPath("$.content[0].availability.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[0].availability.closingSoon").value(true));
    }

    @Test
    void 해제에_성공하면_204다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");

        mockMvc.perform(delete("/api/bookmarks/1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }
}
