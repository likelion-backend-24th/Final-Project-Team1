package com.team1.recommendation.notification.controller;

import com.team1.recommendation.common.ApiException;
import com.team1.recommendation.common.ApiResponse;
import com.team1.recommendation.common.ErrorCode;
import com.team1.recommendation.notification.dto.NotificationListResponse;
import com.team1.recommendation.notification.dto.UnreadCountResponse;
import com.team1.recommendation.notification.service.NotificationService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService.list(currentUserId(), unreadOnly, page, size));
    }

    /** 헤더 뱃지용. 목록보다 가볍게 자주 부른다. */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(new UnreadCountResponse(notificationService.unreadCount(currentUserId())));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(currentUserId(), id);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead(currentUserId());
        return ApiResponse.ok(null);
    }

    private static Long currentUserId() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        return user.userId();
    }
}
