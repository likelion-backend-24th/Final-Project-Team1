package com.team1.recommendation.internal.controller;

import com.team1.recommendation.activity.entity.EventType;
import com.team1.recommendation.activity.service.UserActivityService;
import com.team1.recommendation.common.ApiResponse;
import com.team1.recommendation.expo.service.ExpoTagService;
import com.team1.recommendation.internal.dto.BehaviorEventRequest;
import com.team1.recommendation.internal.dto.ExpoPublishedRequest;
import com.team1.recommendation.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/recommendations")
public class InternalRecommendationController {

    private final ExpoTagService expoTagService;
    private final UserActivityService userActivityService;
    private final NotificationService notificationService;

    public InternalRecommendationController(ExpoTagService expoTagService,
                                            UserActivityService userActivityService,
                                            NotificationService notificationService) {
        this.expoTagService = expoTagService;
        this.userActivityService = userActivityService;
        this.notificationService = notificationService;
    }

    @PostMapping("/expo-published")
    public ApiResponse<Void> expoPublished(@RequestBody ExpoPublishedRequest request) {
        expoTagService.handleExpoPublished(request);
        return ApiResponse.ok(null);
    }

    /** UNTAGGED 상태이거나 태그가 없는 박람회 강제 재태깅 */
    @PostMapping("/expos/{expoId}/retag")
    public ApiResponse<Void> retag(@PathVariable Long expoId,
                                   @RequestBody ExpoPublishedRequest request) {
        expoTagService.retag(expoId, request.title(), request.description());
        return ApiResponse.ok(null);
    }

    @PostMapping("/events")
    public ApiResponse<Void> behaviorEvent(@RequestBody BehaviorEventRequest request) {
        userActivityService.record(request);
        if (request.eventType() == EventType.RESERVATION_CONFIRMED && request.reservationId() != null) {
            notificationService.notifyReservationConfirmed(
                    request.userId(), request.expoId(), request.reservationId(), request.reservationNo());
        }
        return ApiResponse.ok(null);
    }
}
