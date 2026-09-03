package com.team1.expo.expo.service;

import com.team1.expo.client.RoundClient;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.channel.Channel;
import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.domain.expo.Expo;
import com.team1.expo.domain.expo.ExpoRepository;
import com.team1.expo.domain.expo.ExpoStatus;
import com.team1.expo.expo.dto.ExpoPublicationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpoPublicationServiceTest {

    private static final long OWNER_ID = 10L;
    private static final long CHANNEL_ID = 1L;
    private static final long EXPO_ID = 100L;

    @Mock
    private ExpoRepository expoRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private RoundClient roundClient;
    @InjectMocks
    private ExpoPublicationService service;

    private Expo expoWithStatus(ExpoStatus status) {
        Expo expo = Expo.create(CHANNEL_ID, "박람회", "설명", "장소", "서울", "IT·전자", null);
        ReflectionTestUtils.setField(expo, "id", EXPO_ID);
        ReflectionTestUtils.setField(expo, "status", status);
        return expo;
    }

    private void ownerChannelExists() {
        Channel channel = Channel.create("채널", OWNER_ID, "설명");
        ReflectionTestUtils.setField(channel, "id", CHANNEL_ID);
        when(channelRepository.findById(CHANNEL_ID)).thenReturn(Optional.of(channel));
    }

    @Test
    @DisplayName("HIDDEN + 회차 존재 → PUBLISHED로 전이")
    void publish_hiddenWithRounds() {
        Expo expo = expoWithStatus(ExpoStatus.HIDDEN);
        when(expoRepository.findById(EXPO_ID)).thenReturn(Optional.of(expo));
        ownerChannelExists();
        when(roundClient.existsByExpo(EXPO_ID)).thenReturn(true);

        ExpoPublicationResponse res = service.publish(EXPO_ID, OWNER_ID);

        assertThat(res.status()).isEqualTo("PUBLISHED");
        assertThat(expo.getStatus()).isEqualTo(ExpoStatus.PUBLISHED);
    }

    @Test
    @DisplayName("HIDDEN + 회차 없음 → 400, 상태는 HIDDEN 유지")
    void publish_hiddenWithoutRounds() {
        Expo expo = expoWithStatus(ExpoStatus.HIDDEN);
        when(expoRepository.findById(EXPO_ID)).thenReturn(Optional.of(expo));
        ownerChannelExists();
        when(roundClient.existsByExpo(EXPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.publish(EXPO_ID, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(expo.getStatus()).isEqualTo(ExpoStatus.HIDDEN);
    }

    @Test
    @DisplayName("이미 PUBLISHED → 멱등, 상태 변경 없음")
    void publish_alreadyPublishedIdempotent() {
        Expo expo = expoWithStatus(ExpoStatus.PUBLISHED);
        when(expoRepository.findById(EXPO_ID)).thenReturn(Optional.of(expo));
        ownerChannelExists();

        ExpoPublicationResponse res = service.publish(EXPO_ID, OWNER_ID);

        assertThat(res.status()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("CLOSED 재공개 → 409 INVALID_STATE_TRANSITION")
    void publish_closedConflict() {
        Expo expo = expoWithStatus(ExpoStatus.CLOSED);
        when(expoRepository.findById(EXPO_ID)).thenReturn(Optional.of(expo));
        ownerChannelExists();

        assertThatThrownBy(() -> service.publish(EXPO_ID, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("채널 소유자가 아니면 403")
    void publish_notOwnerForbidden() {
        Expo expo = expoWithStatus(ExpoStatus.HIDDEN);
        when(expoRepository.findById(EXPO_ID)).thenReturn(Optional.of(expo));
        ownerChannelExists();

        assertThatThrownBy(() -> service.publish(EXPO_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("회차 확인 실패(503 전파) → 상태는 HIDDEN 유지")
    void publish_dependencyUnavailable() {
        Expo expo = expoWithStatus(ExpoStatus.HIDDEN);
        when(expoRepository.findById(EXPO_ID)).thenReturn(Optional.of(expo));
        ownerChannelExists();
        when(roundClient.existsByExpo(EXPO_ID))
                .thenThrow(new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE));

        assertThatThrownBy(() -> service.publish(EXPO_ID, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE);
        assertThat(expo.getStatus()).isEqualTo(ExpoStatus.HIDDEN);
    }
}
