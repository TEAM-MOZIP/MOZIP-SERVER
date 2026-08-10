-- 서울 범위 확정으로 경기도 소재 더미 정책은 서비스 범위 밖이 됐다.
-- 서울 자치구로 임의 재배정하면 데이터 의미가 왜곡되므로 삭제한다.
-- policies -> policy_eligibility/policy_categories/policy_regions/bookmarks/notifications는
-- 전부 ON DELETE CASCADE이므로 정책 row만 지우면 연결 데이터가 함께 정리된다.
DELETE FROM policies
WHERE title IN ('경기도 청년기본소득', '경기도 청년면접수당', '경기 일자리 재단 취업지원', '따복하우스 공급');
