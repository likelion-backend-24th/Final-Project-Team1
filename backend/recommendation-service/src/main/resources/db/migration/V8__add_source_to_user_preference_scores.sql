-- source 컬럼 추가 (기존 행은 모두 BEHAVIOR로 채움)
ALTER TABLE user_preference_scores
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'BEHAVIOR' AFTER tag_value;

-- 기존 unique key(user_id, tag_value) 를 (user_id, tag_value, source) 로 교체
ALTER TABLE user_preference_scores
    DROP INDEX uq_ups,
    ADD UNIQUE KEY uq_ups (user_id, tag_value, source);
