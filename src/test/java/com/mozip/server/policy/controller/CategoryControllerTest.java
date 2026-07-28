package com.mozip.server.policy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mozip.server.auth.config.CustomAccessDeniedHandler;
import com.mozip.server.auth.config.CustomAuthenticationEntryPoint;
import com.mozip.server.auth.config.SecurityConfig;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import com.mozip.server.policy.dto.CategoryResponse;
import com.mozip.server.policy.service.CategoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void 인증_헤더_없이도_목록을_조회할_수_있다() throws Exception {
        when(categoryService.getCategories()).thenReturn(List.of(new CategoryResponse(1L, "HOUSING", "주거")));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("HOUSING"))
                .andExpect(jsonPath("$[0].name").value("주거"));
    }

    @Test
    void 카테고리가_없으면_빈_배열을_반환한다() throws Exception {
        when(categoryService.getCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
