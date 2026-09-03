package com.team1.expo.expo.service;

import com.team1.expo.client.RoundClient;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.expo.Expo;
import com.team1.expo.domain.expo.ExpoStatus;
import com.team1.expo.expo.dto.ExpoDetailResponse;
import com.team1.expo.expo.dto.RoundView;
import com.team1.expo.expo.repository.ExpoQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpoQueryServiceTest {

    private static final long EXPO_ID = 100L;

    @Mock
    private ExpoQueryRepository expoQueryRepository;
    @Mock
    private RoundClient roundClient;
    @InjectMocks
    private ExpoQueryService service;

    private Expo expoWithStatus(ExpoStatus status) {
        Expo expo = Expo.create(1L, "박람회", "설명", "장소", "서울", "IT·전자", null);
        ReflectionTestUtils.setField(expo, "id", EXPO_ID);
        ReflectionTestUtils.setField(expo, "status", status);
        return expo;
    }

    @Test
    @DisplayName("허용되지 않은 카테고리 필터 → 400")
    void list_invalidCategory() {
        assertThatThrownBy(() -> service.listPublished(null, "없는카테고리", 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("PUBLISHED 상세 + 회차 병합 성공 → roundsAvailable=true")
    void detail_mergesRounds() {
        Expo expo = expoWithStatus(ExpoStatus.PUBLISHED);
        when(expoQueryRepository.findById(EXPO_ID)).thenReturn(Optional.of(expo));
        List<RoundView> rounds = List.of(new RoundView(5L, Instant.now(), Instant.now(), 100, 40));
        when(roundClient.listByExpo(EXPO_ID)).thenReturn(rounds);

        ExpoDetailResponse res = service.getPublishedExpo(EXPO_ID);

        assertThat(res.roundsAvailable()).isTrue();
        assertThat(res.rounds()).hasSize(1);
        assertThat(res.rounds().get(0).remaining()).isEqualTo(40);
    }

    @Test
    @DisplayName("회차 조회 실패 → 부분 실패 허용(200, roundsAvailable=false)")
    void detail_roundsPartialFailure() {
        Expo expo = expoWithStatus(ExpoStatus.PUBLISHED);
        when(expoQueryRepository.findById(EXPO_ID)).thenReturn(Optional.of(expo));
        when(roundClient.listByExpo(EXPO_ID))
                .thenThrow(new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE));

        ExpoDetailResponse res = service.getPublishedExpo(EXPO_ID);

        assertThat(res.roundsAvailable()).isFalse();
        assertThat(res.rounds()).isNull();
        assertThat(res.expoId()).isEqualTo(EXPO_ID);
    }

    @Test
    @DisplayName("PUBLISHED가 아닌 박람회 상세 → 404")
    void detail_hiddenNotFound() {
        when(expoQueryRepository.findById(EXPO_ID))
                .thenReturn(Optional.of(expoWithStatus(ExpoStatus.HIDDEN)));

        assertThatThrownBy(() -> service.getPublishedExpo(EXPO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 박람회 상세 → 404")
    void detail_notFound() {
        when(expoQueryRepository.findById(EXPO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublishedExpo(EXPO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }
}
