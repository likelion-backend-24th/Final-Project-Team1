-- 요구사항: 주최자 1명당 채널 1개.
-- 서비스 레이어의 existsByOwnerId 검사만으로는 동시 요청 두 건이 나란히 통과할 수 있어
-- DB 제약으로 최종 보장한다. (uk_channels_name 과 같은 방식)
ALTER TABLE channels
    ADD CONSTRAINT uk_channels_owner_id UNIQUE (owner_id);
