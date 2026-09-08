package com.team1.expo.promotion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.common.response.ApiResponse;
import com.team1.expo.promotion.dto.ApplyPromotionRequest;
import com.team1.expo.promotion.dto.ApplyPromotionResponse;
import com.team1.expo.promotion.service.ExpoPromotionService;
import com.team1.expo.promotion.service.ExpoPromotionWebhookService;
import com.team1.expo.promotion.service.ExpoPromotionWebhookService.PortOneWebhookPayload;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/expo-promotions")
@RequiredArgsConstructor
public class ExpoPromotionController {

    private final ExpoPromotionService promotionService;
    private final ExpoPromotionWebhookService webhookService;
    private final ObjectMapper objectMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApplyPromotionResponse> apply(@Valid @RequestBody ApplyPromotionRequest request) {
        AuthenticatedUser user = requireOrganizer();
        return ApiResponse.ok(promotionService.apply(user.userId(), request));
    }

    @PostMapping("/{promotionId}/refund")
    public ApiResponse<Void> refund(@PathVariable Long promotionId) {
        AuthenticatedUser user = requireOrganizer();
        promotionService.refund(user.userId(), promotionId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/webhooks/portone")
    public void portoneWebhook(
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-signature", required = false) String signature,
            @RequestBody String rawBodyStr) throws IOException {

        byte[] rawBody = rawBodyStr.getBytes(StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(rawBody);

        String resolvedWebhookId = webhookId != null ? webhookId
                : node.path("webhook_id").asText(null);
        String pgTransactionId = node.path("pg_transaction_id").asText(
                node.path("imp_uid").asText(null));
        String eventType = node.path("status").asText(
                node.path("event_type").asText("unknown"));

        if (resolvedWebhookId == null || pgTransactionId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        webhookService.handle(rawBody, signature,
                new PortOneWebhookPayload(resolvedWebhookId, pgTransactionId, eventType));
    }

    private AuthenticatedUser requireOrganizer() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!"ORGANIZER".equals(user.role())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return user;
    }
}
