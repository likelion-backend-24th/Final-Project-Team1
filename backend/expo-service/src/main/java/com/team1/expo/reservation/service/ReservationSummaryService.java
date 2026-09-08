package com.team1.expo.reservation.service;

import com.team1.expo.client.ReservationClient;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.domain.expo.ExpoRepository;
import com.team1.expo.reservation.dto.ReservationSummaryResponse;
import com.team1.expo.reservation.dto.ReservationSummaryResponse.RoundSummary;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ReservationSummaryService.class);

    private final ExpoRepository expoRepository;
    private final ChannelRepository channelRepository;
    private final ReservationClient reservationClient;

    public ReservationSummaryResponse getSummary(Long requesterId, Long expoId) {
        verifyOwnership(expoId, requesterId);

        List<ReservationClient.ReservationSummaryItem> summaries = reservationClient.getSummary(expoId);

        List<RoundSummary> rounds = summaries.stream()
                .map(s -> new RoundSummary(s.roundId(), s.capacity(), s.confirmed(), s.cancelled(), null))
                .collect(Collectors.toList());

        return new ReservationSummaryResponse(expoId, rounds);
    }

    private void verifyOwnership(Long expoId, Long requesterId) {
        Long ownerId = expoRepository.findById(expoId)
                .flatMap(expo -> channelRepository.findById(expo.getChannelId()))
                .map(ch -> ch.getOwnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!ownerId.equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
