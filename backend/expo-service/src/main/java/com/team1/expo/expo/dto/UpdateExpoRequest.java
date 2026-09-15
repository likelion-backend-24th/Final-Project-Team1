package com.team1.expo.expo.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 박람회 부분 수정(S9-1). <b>보내지 않은 필드(null)는 그대로 둔다.</b>
 * 값을 지우려면 빈 문자열을 보낸다 - title·category 는 필수라 빈 문자열도 거절한다.
 * status·channelId 는 여기서 못 바꾼다. 공개 전환은 POST /expos/{id}/publication 이 담당한다.
 */
public record UpdateExpoRequest(
        @Size(max = 200) String title,
        String description,
        @Size(max = 200) String venue,
        @Size(max = 50) String region,
        @Pattern(regexp = "IT·전자|식품·음료|패션·뷰티|교육·취업|문화·예술|기타") String category,
        @Size(max = 500)
        @Pattern(regexp = "^$|^https?://.+", message = "thumbnailUrl must start with http:// or https://")
        String thumbnailUrl,

        // 행사 소개용 상세 이미지. 순서가 곧 화면 순서다.
        @Size(max = 20, message = "상세 이미지는 20장까지입니다")
        List<@Size(max = 500) @Pattern(regexp = "^https?://.+",
                message = "이미지 주소는 http:// 또는 https:// 로 시작해야 합니다") String> detailImageUrls
) {}
