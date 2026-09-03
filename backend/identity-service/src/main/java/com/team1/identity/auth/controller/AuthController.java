package com.team1.identity.auth.controller;

import com.team1.identity.auth.dto.LoginRequest;
import com.team1.identity.auth.dto.LoginResponse;
import com.team1.identity.auth.dto.SignUpRequest;
import com.team1.identity.auth.dto.SignUpResponse;
import com.team1.identity.auth.service.AuthService;
import com.team1.identity.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입 · 로그인 (인증 불필요)")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "회원가입",
            description = """
                    이메일은 앞뒤 공백을 제거하고 소문자로 정규화해 저장한다.
                    비밀번호는 8~64자이며 영문과 숫자를 각각 1자 이상 포함해야 한다.
                    사용자와 USER Role은 한 Transaction에 저장되며, 동시에 같은 이메일로
                    요청해도 한 건만 생성된다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "가입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "INVALID_REQUEST — 이메일 형식 아님·비밀번호 정책 위반·본문 파싱 불가 (미생성)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "DUPLICATE_EMAIL — 이미 가입된 이메일 (미생성)")
    })
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        return ApiResponse.ok(authService.signUp(request));
    }

    @Operation(
            summary = "로그인",
            description = """
                    성공 시 Access Token(수명 1시간)과 만료 시각을 반환한다.
                    Token 클레임은 sub에 userId(문자열), role에 Role 문자열을 담는다.
                    expiresAt은 ISO-8601 UTC 초 단위이며 Token의 exp 클레임과 동일한 시각이다.
                    존재하지 않는 이메일과 틀린 비밀번호는 응답으로 구분할 수 없다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "INVALID_CREDENTIALS — 이메일 미존재·비밀번호 불일치 (두 경우 구분 불가). WWW-Authenticate: Bearer 부착")
    })
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
