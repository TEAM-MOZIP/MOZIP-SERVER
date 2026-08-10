package com.mozip.server.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mozip.server.auth.config.CustomAccessDeniedHandler;
import com.mozip.server.auth.config.CustomAuthenticationEntryPoint;
import com.mozip.server.auth.config.SecurityConfig;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import com.mozip.server.user.dto.UserProfileResponse;
import com.mozip.server.user.dto.UserProfileUpdateRequest;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.Gender;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import com.mozip.server.user.exception.UserProfileAlreadyExistsException;
import com.mozip.server.user.exception.UserRegionNotSelectableException;
import com.mozip.server.user.service.UserProfileService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserProfileController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserProfileService userProfileService;

    @Test
    void 인증_없이_조회하면_401이다() throws Exception {
        mockMvc.perform(get("/api/users/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증된_사용자는_자신의_프로필을_조회한다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(userProfileService.getMyProfile(eq(1L))).thenReturn(new UserProfileResponse(
                1L, LocalDate.of(1998, 5, 14), 3L, "서울특별시", Gender.FEMALE,
                IncomeType.MEDIAN_PERCENTAGE, 80, EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE
        ));

        mockMvc.perform(get("/api/users/me/profile").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.incomeValue").value(80));
    }

    @Test
    void 인증_없이_등록하면_401이다() throws Exception {
        mockMvc.perform(put("/api/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 미래_생년월일이면_400이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        UserProfileUpdateRequest invalidRequest = new UserProfileUpdateRequest(
                LocalDate.now().plusDays(1), 3L, Gender.FEMALE, IncomeType.MEDIAN_PERCENTAGE, 80,
                EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE
        );

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 소득값이_음수면_400이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        UserProfileUpdateRequest invalidRequest = new UserProfileUpdateRequest(
                LocalDate.of(1998, 5, 14), 3L, Gender.FEMALE, IncomeType.MEDIAN_PERCENTAGE, -1,
                EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE
        );

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 정상_요청이면_등록에_성공한다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        UserProfileUpdateRequest request = validRequest();
        when(userProfileService.upsertMyProfile(eq(1L), any())).thenReturn(new UserProfileResponse(
                1L, request.birthDate(), request.regionId(), "서울특별시", request.gender(),
                request.incomeType(), request.incomeValue(), request.employmentStatus(), request.householdType()
        ));

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.incomeValue").value(80));
    }

    @Test
    void enum_필드에_허용되지_않은_값을_보내면_400이고_내부_정보를_노출하지_않는다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        String requestBody = "{"
                + "\"birthDate\":\"1998-05-14\","
                + "\"regionId\":3,"
                + "\"gender\":\"FEMALE\","
                + "\"incomeType\":\"NOT_A_REAL_VALUE\","
                + "\"incomeValue\":80,"
                + "\"employmentStatus\":\"JOB_SEEKER\","
                + "\"householdType\":\"SINGLE\"}";

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("incomeType"))
                .andExpect(jsonPath("$.message", not(containsStringIgnoringCase("jackson"))))
                .andExpect(jsonPath("$.message", not(containsString("MEDIAN_PERCENTAGE"))))
                .andExpect(jsonPath("$.fieldErrors[0].reason", not(containsString("MEDIAN_PERCENTAGE"))));
    }

    @Test
    void 허용되지_않은_성별_값을_보내면_400이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        String requestBody = "{"
                + "\"birthDate\":\"1998-05-14\","
                + "\"regionId\":3,"
                + "\"gender\":\"OTHER\","
                + "\"incomeType\":\"MEDIAN_PERCENTAGE\","
                + "\"incomeValue\":80,"
                + "\"employmentStatus\":\"JOB_SEEKER\","
                + "\"householdType\":\"SINGLE\"}";

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("gender"));
    }

    @Test
    void 성별이_빈_문자열이면_400이고_내부_정보를_노출하지_않는다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        String requestBody = "{"
                + "\"birthDate\":\"1998-05-14\","
                + "\"regionId\":3,"
                + "\"gender\":\"\","
                + "\"incomeType\":\"MEDIAN_PERCENTAGE\","
                + "\"incomeValue\":80,"
                + "\"employmentStatus\":\"JOB_SEEKER\","
                + "\"householdType\":\"SINGLE\"}";

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("gender"))
                .andExpect(jsonPath("$.message", not(containsStringIgnoringCase("jackson"))))
                .andExpect(jsonPath("$.message", not(containsString("MALE"))));
    }

    @Test
    void JSON_문법_자체가_잘못되면_400이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        String malformedJson = "{\"birthDate\":\"1998-05-14\", this is not valid json";

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 사용자_지역으로_선택할_수_없는_지역이면_400이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        UserProfileUpdateRequest request = validRequest();
        when(userProfileService.upsertMyProfile(eq(1L), any()))
                .thenThrow(new UserRegionNotSelectableException(request.regionId()));

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_REGION_NOT_SELECTABLE"));
    }

    @Test
    void 동시_최초_등록_충돌이면_409다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        UserProfileUpdateRequest request = validRequest();
        when(userProfileService.upsertMyProfile(eq(1L), any()))
                .thenThrow(new UserProfileAlreadyExistsException(1L));

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_PROFILE_ALREADY_EXISTS"));
    }

    private UserProfileUpdateRequest validRequest() {
        return new UserProfileUpdateRequest(
                LocalDate.of(1998, 5, 14), 3L, Gender.FEMALE, IncomeType.MEDIAN_PERCENTAGE, 80,
                EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE
        );
    }
}
