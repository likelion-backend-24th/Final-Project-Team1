package com.team1.expo.expo.service;

import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.domain.expo.Expo;
import com.team1.expo.domain.expo.ExpoRepository;
import com.team1.expo.expo.dto.CreateExpoRequest;
import com.team1.expo.expo.dto.ExpoResponse;
import com.team1.expo.expo.dto.UpdateExpoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpoService {

    private final ExpoRepository expoRepository;
    private final ChannelRepository channelRepository;

    @Transactional
    public ExpoResponse create(Long requesterId, Long channelId, CreateExpoRequest request) {
        channelRepository.findById(channelId)
                .filter(c -> c.getOwnerId().equals(requesterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        Expo expo = Expo.create(
                channelId,
                request.title(),
                request.description(),
                request.venue(),
                request.region(),
                request.category(),
                request.thumbnailUrl()
        );
        expo.replaceDetailImages(request.detailImageUrls());
        return ExpoResponse.from(expoRepository.save(expo));
    }

    /**
     * 주최자용 목록. 공개 목록과 달리 HIDDEN·CLOSED 도 내려준다.
     * 이게 없으면 등록 직후의 박람회가 주최자 화면에서도 보이지 않는다.
     */
    @Transactional(readOnly = true)
    public List<ExpoResponse> listForOrganizer(Long requesterId, Long channelId) {
        requireOwnChannel(requesterId, channelId);

        return expoRepository.findByChannelIdOrderByCreatedAtDesc(channelId)
                .stream()
                .map(ExpoResponse::from)
                .toList();
    }

    /** 주최자용 단건. 수정 화면이 현재 값을 읽는 경로다. */
    @Transactional(readOnly = true)
    public ExpoResponse getForOrganizer(Long requesterId, Long channelId, Long expoId) {
        return ExpoResponse.from(findOwnExpo(requesterId, channelId, expoId));
    }

    @Transactional
    public ExpoResponse update(Long requesterId, Long channelId, Long expoId, UpdateExpoRequest request) {
        Expo expo = findOwnExpo(requesterId, channelId, expoId);
        try {
            expo.update(request.title(), request.description(), request.venue(),
                    request.region(), request.category(), request.thumbnailUrl());
            // null 은 "그대로", 빈 배열은 "전부 지우기" 다.
            if (request.detailImageUrls() != null) {
                expo.replaceDetailImages(request.detailImageUrls());
            }
        } catch (IllegalStateException e) {   // CLOSED
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return ExpoResponse.from(expo);
    }

    // 남의 채널·남의 박람회는 전부 404 다. 403 으로 나누면 존재 여부가 새어 나간다.
    private Expo findOwnExpo(Long requesterId, Long channelId, Long expoId) {
        requireOwnChannel(requesterId, channelId);

        return expoRepository.findById(expoId)
                .filter(e -> e.getChannelId().equals(channelId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void requireOwnChannel(Long requesterId, Long channelId) {
        channelRepository.findById(channelId)
                .filter(c -> c.getOwnerId().equals(requesterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
