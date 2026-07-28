package com.mozip.server.region.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mozip.server.auth.config.CustomAccessDeniedHandler;
import com.mozip.server.auth.config.CustomAuthenticationEntryPoint;
import com.mozip.server.auth.config.SecurityConfig;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import com.mozip.server.region.dto.RegionResponse;
import com.mozip.server.region.service.RegionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegionController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class RegionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionService regionService;

    @Test
    void 인증_헤더_없이도_목록을_조회할_수_있다() throws Exception {
        when(regionService.getRegions()).thenReturn(List.of(new RegionResponse(1L, "SEOUL", "서울특별시")));

        mockMvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("SEOUL"))
                .andExpect(jsonPath("$[0].name").value("서울특별시"));
    }

    @Test
    void 지역이_없으면_빈_배열을_반환한다() throws Exception {
        when(regionService.getRegions()).thenReturn(List.of());

        mockMvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
