-- 행사 소개용 상세 이미지(S9-4 확장). 여러 장을 순서대로 보여준다.
--
-- 왜 별도 테이블인가: 장수가 정해져 있지 않고 순서가 의미를 가진다.
-- expos 에 컬럼을 늘리거나 JSON 문자열로 넣으면 순서 바꾸기가 문자열 편집이 된다.
--
-- 이미지는 박람회에 종속된 값이고 독립적으로 조회되지 않으므로 @ElementCollection 으로 매핑한다.
-- PK 가 (expo_id, sort_order) 인 것도 그 때문이다 - 행 자체의 식별자가 필요 없다.

CREATE TABLE expo_images (
    expo_id    BIGINT       NOT NULL,
    sort_order INT          NOT NULL COMMENT '0부터. 화면에 이 순서대로 세로로 쌓인다',
    url        VARCHAR(500) NOT NULL,
    PRIMARY KEY (expo_id, sort_order),
    CONSTRAINT fk_expo_images_expo FOREIGN KEY (expo_id) REFERENCES expos (id)
);
