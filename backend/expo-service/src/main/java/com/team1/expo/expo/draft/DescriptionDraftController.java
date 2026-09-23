package com.team1.expo.expo.draft;

import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.common.response.ApiResponse;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소개글 초안 API. 주최자 본인 채널만 쓸 수 있다.
 *
 * <p>공개로 열지 않는 이유는 돈이다. 누구나 부를 수 있으면 하루 상한을 소진시킬 수 있고,
 * 그러면 태깅·알림·자연어 검색이 같이 멈춘다. common-ai 의 사용량 집계에서
 * {@code expo-description} 으로 잡힌다.
 *
 * <p>{@code ExpoController} 와 경로 앞부분이 같지만 파일을 나눴다 - 같은 파일에서 작업 중인
 * 다른 사람과 충돌하지 않게 하려는 것이고, 자연어 검색을 분리한 것과 같은 이유다.
 */
@RestController
@RequestMapping("/api/v1/channels/{channelId}/expos")
public class DescriptionDraftController {

    private final DescriptionDraftService descriptionDraftService;

    public DescriptionDraftController(DescriptionDraftService descriptionDraftService) {
        this.descriptionDraftService = descriptionDraftService;
    }

    @PostMapping("/description-draft")
    public ApiResponse<DescriptionDraftResponse> draft(
            @PathVariable Long channelId,
            @Valid @RequestBody DescriptionDraftRequest request) {

        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (!"ORGANIZER".equals(user.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return ApiResponse.ok(descriptionDraftService.draft(user.userId(), channelId, request));
    }
}
