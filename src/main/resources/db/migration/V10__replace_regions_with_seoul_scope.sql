-- SEOUL은 testdata(V5)에서 이미 생성됐을 수 있으므로 멱등하게 처리한다.
INSERT INTO regions (code, name)
VALUES ('SEOUL', '서울특별시')
ON CONFLICT (code) DO NOTHING;

-- 서울 25개 자치구, parent = SEOUL
INSERT INTO regions (code, name, parent_id)
SELECT district.code, district.name, (SELECT id FROM regions WHERE code = 'SEOUL')
FROM (VALUES
    ('SEOUL_JONGNO', '종로구'),
    ('SEOUL_JUNG', '중구'),
    ('SEOUL_YONGSAN', '용산구'),
    ('SEOUL_SEONGDONG', '성동구'),
    ('SEOUL_GWANGJIN', '광진구'),
    ('SEOUL_DONGDAEMUN', '동대문구'),
    ('SEOUL_JUNGNANG', '중랑구'),
    ('SEOUL_SEONGBUK', '성북구'),
    ('SEOUL_GANGBUK', '강북구'),
    ('SEOUL_DOBONG', '도봉구'),
    ('SEOUL_NOWON', '노원구'),
    ('SEOUL_EUNPYEONG', '은평구'),
    ('SEOUL_SEODAEMUN', '서대문구'),
    ('SEOUL_MAPO', '마포구'),
    ('SEOUL_YANGCHEON', '양천구'),
    ('SEOUL_GANGSEO', '강서구'),
    ('SEOUL_GURO', '구로구'),
    ('SEOUL_GEUMCHEON', '금천구'),
    ('SEOUL_YEONGDEUNGPO', '영등포구'),
    ('SEOUL_DONGJAK', '동작구'),
    ('SEOUL_GWANAK', '관악구'),
    ('SEOUL_SEOCHO', '서초구'),
    ('SEOUL_GANGNAM', '강남구'),
    ('SEOUL_SONGPA', '송파구'),
    ('SEOUL_GANGDONG', '강동구')
) AS district(code, name)
ON CONFLICT (code) DO NOTHING;

-- 서울 범위 밖 광역 지역을 참조하는 데이터를 먼저 정리한다(FK RESTRICT 대비).
-- 참조가 없는 환경에서는 0건 삭제로 안전하게 통과한다.
DELETE FROM policy_regions
WHERE region_id IN (SELECT id FROM regions WHERE code IN ('GYEONGGI', 'BUSAN', 'INCHEON', 'DAEGU'));

-- 사용자 row/프로필 자체는 보존하고 서비스 범위 밖 지역 참조만 끊는다.
UPDATE user_profiles
SET region_id = NULL
WHERE region_id IN (SELECT id FROM regions WHERE code IN ('GYEONGGI', 'BUSAN', 'INCHEON', 'DAEGU'));

-- 서울 범위 밖 광역 지역 제거
DELETE FROM regions WHERE code IN ('GYEONGGI', 'BUSAN', 'INCHEON', 'DAEGU');
