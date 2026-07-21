INSERT INTO organizations (name, type, website_url) VALUES
('고용노동부', '중앙부처', 'https://www.moel.go.kr'),
('서울특별시', '지자체', 'https://www.seoul.go.kr'),
('중소벤처기업부', '중앙부처', 'https://www.mss.go.kr'),
('보건복지부', '중앙부처', 'https://www.mohw.go.kr'),
('경기도', '지자체', 'https://www.gg.go.kr');

INSERT INTO regions (code, name) VALUES
('SEOUL', '서울특별시'),
('GYEONGGI', '경기도'),
('BUSAN', '부산광역시'),
('INCHEON', '인천광역시'),
('DAEGU', '대구광역시');

INSERT INTO categories (code, name) VALUES
('HOUSING', '주거'),
('EMPLOYMENT', '취업'),
('STARTUP', '창업'),
('EDUCATION', '교육'),
('WELFARE', '복지'),
('CULTURE', '문화');

INSERT INTO policies (
    organization_id, title, summary, description, target_description, benefit_description,
    application_method, application_type, application_start_date, application_end_date,
    region_scope, status, source_url
) VALUES
((SELECT id FROM organizations WHERE name = '고용노동부'), '국민취업지원제도', '저소득 구직자에게 취업지원서비스와 생계지원을 함께 제공', '취업지원서비스와 소득지원을 결합한 한국형 실업부조 제도입니다.', '만 15세 이상 69세 이하 구직자 중 소득·재산 요건을 충족하는 자', '구직촉진수당 월 50만원씩 최대 6개월 지급', '고용센터 방문 또는 워크넷 온라인 신청', 'PERIOD', '2026-01-01', '2026-12-31', 'NATIONAL', 'OPEN', 'https://www.kua.go.kr'),
((SELECT id FROM organizations WHERE name = '고용노동부'), '구직촉진수당', '국민취업지원제도 참여자 대상 구직활동 지원금', '구직활동을 성실히 이행하는 참여자에게 정기적으로 지급하는 수당입니다.', '국민취업지원제도 1유형 참여자', '월 50만원, 최대 6개월', '고용센터 온라인 신청', 'ALWAYS', NULL, NULL, 'NATIONAL', 'OPEN', 'https://www.kua.go.kr'),
((SELECT id FROM organizations WHERE name = '고용노동부'), '청년내일채움공제', '중소기업 취업 청년의 장기근속과 자산형성을 지원', '청년, 기업, 정부가 공동 적립하여 만기 시 목돈을 지급하는 제도입니다.', '중소기업에 정규직으로 취업한 만 15세 이상 34세 이하 청년', '2년형 만기 시 최대 1,200만원 수령', '워크넷 청년공제 홈페이지 신청', 'PERIOD', '2026-03-01', '2026-11-30', 'NATIONAL', 'OPEN', 'https://www.work.go.kr'),
((SELECT id FROM organizations WHERE name = '고용노동부'), '국민내일배움카드', '직업훈련비용을 지원하는 평생능력개발 카드', '국민 누구나 직업훈련에 참여할 수 있도록 훈련비를 지원합니다.', '구직자 및 재직자 등 대부분의 국민', '5년간 최대 500만원 훈련비 지원', 'HRD-Net 온라인 신청', 'ALWAYS', NULL, NULL, 'NATIONAL', 'ALWAYS_OPEN', 'https://www.hrd.go.kr'),
((SELECT id FROM organizations WHERE name = '고용노동부'), '일자리안정자금', '최저임금 인상에 따른 소상공인·영세사업주 인건비 지원', '영세 사업주의 인건비 부담을 완화하기 위한 지원금입니다.', '30인 미만 사업장을 운영하는 사업주', '노동자 1인당 월 최대 9만원 지원', '근로복지공단 방문 신청', 'PERIOD', '2025-01-01', '2025-12-31', 'NATIONAL', 'CLOSED', 'https://www.moel.go.kr'),
((SELECT id FROM organizations WHERE name = '서울특별시'), '서울시 청년월세지원', '서울 거주 무주택 청년의 주거비 부담 완화', '월세로 거주하는 청년 1인가구에 월세를 지원하는 사업입니다.', '서울 거주 만 19세 이상 39세 이하 무주택 청년', '월 20만원씩 최대 12개월 지원', '청년몽땅정보통 온라인 신청', 'PERIOD', '2026-04-01', '2026-10-31', 'REGIONAL', 'OPEN', 'https://youth.seoul.go.kr'),
((SELECT id FROM organizations WHERE name = '서울특별시'), '서울런 교육지원', '취약계층 청소년 대상 온라인 교육 콘텐츠 무료 제공', '유명 강사 강의를 포함한 온라인 학습 콘텐츠를 무료로 지원합니다.', '서울 거주 중위소득 100% 이하 가구의 만 6세 이상 24세 이하', '온라인 강의 무제한 수강, 멘토링 제공', '서울런 홈페이지 온라인 신청', 'ALWAYS', NULL, NULL, 'REGIONAL', 'OPEN', 'https://slearn.seoul.go.kr'),
((SELECT id FROM organizations WHERE name = '서울특별시'), '서울시 청년수당', '미취업 청년의 구직활동 지원금', '구직활동에 필요한 비용을 지원해 노동시장 진입을 돕는 제도입니다.', '서울 거주 만 19세 이상 34세 이하 미취업 청년', '월 50만원씩 최대 6개월 지급', '청년몽땅정보통 온라인 신청', 'PERIOD', '2026-02-01', '2026-08-31', 'REGIONAL', 'OPEN', 'https://youth.seoul.go.kr'),
((SELECT id FROM organizations WHERE name = '서울특별시'), '안심소득 시범사업', '저소득층 소득격차 완화를 위한 소득보장 시범사업', '기준 중위소득 대비 부족한 금액의 일부를 채워주는 시범사업입니다.', '서울 거주 기준 중위소득 85% 이하 가구', '기준소득 대비 부족분의 50% 지급', '자치구 동주민센터 방문 신청', 'PERIOD', '2025-07-01', '2025-12-31', 'REGIONAL', 'CLOSED', 'https://sf.seoul.go.kr'),
((SELECT id FROM organizations WHERE name = '서울특별시'), '서울형 주택바우처', '저소득 가구의 주거비 부담 완화를 위한 임차료 보조', '민간 임대주택에 거주하는 저소득 가구에 임차료를 보조합니다.', '서울 거주 기준 중위소득 60% 이하 무주택 가구', '가구원 수에 따라 월 최대 12만원 지원', '자치구 동주민센터 방문 신청', 'ALWAYS', NULL, NULL, 'REGIONAL', 'SUSPENDED', 'https://housing.seoul.go.kr'),
((SELECT id FROM organizations WHERE name = '중소벤처기업부'), '청년창업사관학교', '유망 청년 창업기업의 성장을 위한 종합 지원', '창업 준비부터 사업화까지 전 과정을 밀착 지원하는 프로그램입니다.', '만 39세 이하 창업 3년 이내 대표자', '사업화 자금 최대 1억원, 전담 코치 배정', '창업사관학교 홈페이지 온라인 신청', 'PERIOD', '2026-01-15', '2026-03-15', 'NATIONAL', 'OPEN', 'https://start.kosmes.or.kr'),
((SELECT id FROM organizations WHERE name = '중소벤처기업부'), '예비창업패키지', '예비창업자의 초기 사업화 자금 및 멘토링 지원', '창업 전 단계의 아이디어를 사업화할 수 있도록 지원합니다.', '공고일 기준 창업 이력이 없는 예비창업자', '사업화 자금 최대 1억원', 'K-Startup 온라인 신청', 'PERIOD', '2026-02-01', '2026-04-30', 'NATIONAL', 'OPEN', 'https://www.k-startup.go.kr'),
((SELECT id FROM organizations WHERE name = '중소벤처기업부'), '초기창업패키지', '창업 3년 이내 기업의 성장 지원', '초기 창업기업의 사업 안정화를 위한 자금과 프로그램을 지원합니다.', '창업 3년 이내 기업 대표자', '사업화 자금 최대 1억원', 'K-Startup 온라인 신청', 'PERIOD', '2025-03-01', '2025-06-30', 'NATIONAL', 'CLOSED', 'https://www.k-startup.go.kr'),
((SELECT id FROM organizations WHERE name = '중소벤처기업부'), '창업성공패키지', '창업 7년 이내 기업의 후속 성장 지원', '일정 성과를 낸 창업기업의 스케일업을 지원하는 프로그램입니다.', '창업 7년 이내 기업 중 선정 심사를 통과한 기업', '사업화 자금 최대 2억원', 'K-Startup 온라인 신청', 'PERIOD', '2026-05-01', '2026-07-31', 'NATIONAL', 'OPEN', 'https://www.k-startup.go.kr'),
((SELECT id FROM organizations WHERE name = '중소벤처기업부'), '소상공인 정책자금', '소상공인의 경영안정과 성장을 위한 저금리 융자', '자금난을 겪는 소상공인에게 저금리로 사업자금을 융자합니다.', '연매출 10억원 이하 소상공인', '업체당 최대 7천만원 융자, 저금리 적용', '소상공인시장진흥공단 온라인 신청', 'ALWAYS', NULL, NULL, 'NATIONAL', 'ALWAYS_OPEN', 'https://www.semas.or.kr'),
((SELECT id FROM organizations WHERE name = '보건복지부'), '청년마음건강지원사업', '청년의 정서적 어려움 해소를 위한 상담 지원', '전문 심리상담 서비스를 무료 또는 저비용으로 제공합니다.', '만 19세 이상 34세 이하 청년', '상담 비용 회당 최대 7만원, 총 10회 지원', '복지로 온라인 신청', 'ALWAYS', NULL, NULL, 'NATIONAL', 'OPEN', 'https://www.bokjiro.go.kr'),
((SELECT id FROM organizations WHERE name = '보건복지부'), '기초생활수급자 의료급여', '저소득층의 의료비 부담 완화', '기초생활보장 수급자에게 의료서비스 비용을 지원합니다.', '국민기초생활보장법에 따른 수급자', '입원·외래 진료비 대부분 지원', '주민센터 방문 신청', 'ALWAYS', NULL, NULL, 'NATIONAL', 'ALWAYS_OPEN', 'https://www.bokjiro.go.kr'),
((SELECT id FROM organizations WHERE name = '보건복지부'), '저소득층 에너지바우처', '저소득 가구의 냉난방비 부담 완화', '여름철 냉방과 겨울철 난방에 필요한 에너지 비용을 지원합니다.', '생계·의료급여 수급 가구 중 노인, 영유아, 장애인 등 포함 가구', '가구원 수에 따라 연간 최대 60만원 상당', '주민센터 방문 신청', 'PERIOD', '2026-05-01', '2027-04-30', 'NATIONAL', 'OPEN', 'https://www.bokjiro.go.kr'),
((SELECT id FROM organizations WHERE name = '보건복지부'), '자활근로사업', '근로능력이 있는 저소득층의 자립 지원', '자활근로를 통해 자활 능력을 배양하고 자립을 지원합니다.', '만 18세 이상 근로능력이 있는 기초생활수급자 및 차상위자', '근로일수에 따라 급여 지급', '지역 자활센터 방문 신청', 'PERIOD', '2026-01-01', '2026-12-31', 'NATIONAL', 'OPEN', 'https://www.bokjiro.go.kr'),
((SELECT id FROM organizations WHERE name = '보건복지부'), '청년 자립수당', '자립준비청년의 안정적 사회정착 지원', '보호종료 이후 자립을 준비하는 청년에게 정착 자금을 지원합니다.', '아동복지시설 등에서 보호가 종료된 만 18세 이상 24세 이하 청년', '월 40만원씩 최대 5년 지급', '관할 시군구 아동보호전담기관 신청', 'PERIOD', '2025-01-01', '2025-12-31', 'NATIONAL', 'CLOSED', 'https://www.bokjiro.go.kr'),
((SELECT id FROM organizations WHERE name = '경기도'), '경기도 청년기본소득', '경기도 거주 청년에게 지급하는 분기별 정액 지원금', '청년의 사회 진출을 지원하기 위한 경기도 자체 지원사업입니다.', '경기도 거주 만 24세 청년', '분기별 25만원씩 연 100만원 지급', '경기민원24 온라인 신청', 'PERIOD', '2026-01-01', '2026-12-31', 'REGIONAL', 'OPEN', 'https://gg24.gg.go.kr'),
((SELECT id FROM organizations WHERE name = '경기도'), '경기도 청년면접수당', '취업 면접에 참여하는 청년의 비용 부담 완화', '면접에 소요되는 교통비, 의류비 등을 지원합니다.', '경기도 거주 만 18세 이상 34세 이하 미취업 청년', '면접 1회당 5만원, 연 최대 30만원', '잡아바 온라인 신청', 'PERIOD', '2026-01-01', '2026-12-31', 'REGIONAL', 'OPEN', 'https://gg.jobaba.net'),
((SELECT id FROM organizations WHERE name = '경기도'), '경기 일자리 재단 취업지원', '경기도 구직자 대상 맞춤형 취업 컨설팅', '개인별 맞춤형 취업 상담과 알선 서비스를 제공합니다.', '경기도 거주 구직자', '무료 취업 컨설팅 및 채용 연계', '잡아바 온라인 신청', 'ALWAYS', NULL, NULL, 'REGIONAL', 'OPEN', 'https://gg.jobaba.net'),
((SELECT id FROM organizations WHERE name = '경기도'), '따복하우스 공급', '경기도 무주택 서민을 위한 공공임대주택 공급', '시세보다 저렴한 임대료로 공공임대주택을 공급하는 사업입니다.', '경기도 거주 무주택 세대구성원', '시세 대비 60~80% 수준의 임대료', '경기주택도시공사 온라인 접수', 'PERIOD', '2025-09-01', '2025-11-30', 'REGIONAL', 'SUSPENDED', 'https://www.gicoli.or.kr');

INSERT INTO policy_categories (policy_id, category_id)
SELECT p.id, c.id FROM policies p, categories c WHERE (p.title, c.code) IN (
    ('국민취업지원제도', 'EMPLOYMENT'),
    ('구직촉진수당', 'EMPLOYMENT'),
    ('구직촉진수당', 'WELFARE'),
    ('청년내일채움공제', 'EMPLOYMENT'),
    ('국민내일배움카드', 'EDUCATION'),
    ('일자리안정자금', 'EMPLOYMENT'),
    ('서울시 청년월세지원', 'HOUSING'),
    ('서울런 교육지원', 'EDUCATION'),
    ('서울시 청년수당', 'WELFARE'),
    ('안심소득 시범사업', 'WELFARE'),
    ('서울형 주택바우처', 'HOUSING'),
    ('청년창업사관학교', 'STARTUP'),
    ('예비창업패키지', 'STARTUP'),
    ('초기창업패키지', 'STARTUP'),
    ('창업성공패키지', 'STARTUP'),
    ('창업성공패키지', 'EMPLOYMENT'),
    ('소상공인 정책자금', 'STARTUP'),
    ('청년마음건강지원사업', 'WELFARE'),
    ('기초생활수급자 의료급여', 'WELFARE'),
    ('저소득층 에너지바우처', 'WELFARE'),
    ('자활근로사업', 'EMPLOYMENT'),
    ('자활근로사업', 'WELFARE'),
    ('청년 자립수당', 'WELFARE'),
    ('경기도 청년기본소득', 'WELFARE'),
    ('경기도 청년면접수당', 'EMPLOYMENT'),
    ('경기 일자리 재단 취업지원', 'EMPLOYMENT'),
    ('따복하우스 공급', 'HOUSING')
);

INSERT INTO policy_regions (policy_id, region_id)
SELECT p.id, r.id FROM policies p, regions r WHERE (p.title, r.code) IN (
    ('서울시 청년월세지원', 'SEOUL'),
    ('서울런 교육지원', 'SEOUL'),
    ('서울시 청년수당', 'SEOUL'),
    ('안심소득 시범사업', 'SEOUL'),
    ('서울형 주택바우처', 'SEOUL'),
    ('경기도 청년기본소득', 'GYEONGGI'),
    ('경기도 청년면접수당', 'GYEONGGI'),
    ('경기 일자리 재단 취업지원', 'GYEONGGI'),
    ('따복하우스 공급', 'GYEONGGI')
);

INSERT INTO policy_eligibility (
    policy_id, minimum_age, maximum_age, gender_condition, income_type,
    minimum_income_value, maximum_income_value,
    allowed_employment_statuses, allowed_household_types, additional_conditions
) VALUES
((SELECT id FROM policies WHERE title = '국민취업지원제도'), 15, 69, NULL, 'MEDIAN_PERCENTAGE', NULL, 60,
    '["JOB_SEEKER", "UNEMPLOYED"]'::jsonb, NULL, '{"note": "재산 요건 별도 충족 필요"}'::jsonb),
((SELECT id FROM policies WHERE title = '청년내일채움공제'), 15, 34, NULL, NULL, NULL, NULL,
    '["EMPLOYED"]'::jsonb, NULL, '{"note": "중소기업 정규직 취업자에 한함"}'::jsonb),
((SELECT id FROM policies WHERE title = '서울시 청년월세지원'), 19, 39, NULL, 'MEDIAN_PERCENTAGE', NULL, 150,
    NULL, '["SINGLE"]'::jsonb, '{"residencyPeriod": "서울 거주 1년 이상"}'::jsonb),
((SELECT id FROM policies WHERE title = '서울시 청년수당'), 19, 34, NULL, NULL, NULL, NULL,
    '["UNEMPLOYED", "JOB_SEEKER"]'::jsonb, NULL, '{"note": "졸업 후 2년 이내 우대"}'::jsonb),
((SELECT id FROM policies WHERE title = '청년창업사관학교'), NULL, 39, NULL, NULL, NULL, NULL,
    NULL, NULL, '{"note": "창업 3년 이내 대표자에 한함"}'::jsonb),
((SELECT id FROM policies WHERE title = '예비창업패키지'), NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, '{"note": "공고일 기준 창업 이력이 없는 자"}'::jsonb),
((SELECT id FROM policies WHERE title = '창업성공패키지'), NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, '{"note": "창업 7년 이내 기업, 서면·발표 심사 통과자"}'::jsonb),
((SELECT id FROM policies WHERE title = '청년마음건강지원사업'), 19, 34, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL),
((SELECT id FROM policies WHERE title = '저소득층 에너지바우처'), NULL, NULL, NULL, 'ABSOLUTE', NULL, NULL,
    NULL, '["ELDERLY", "SINGLE_PARENT", "DISABLED"]'::jsonb, '{"note": "생계·의료급여 수급 가구에 한함"}'::jsonb),
((SELECT id FROM policies WHERE title = '청년 자립수당'), 18, 24, NULL, NULL, NULL, NULL,
    NULL, NULL, '{"note": "아동복지시설 등 보호종료청년에 한함"}'::jsonb),
((SELECT id FROM policies WHERE title = '경기도 청년기본소득'), 24, 24, NULL, NULL, NULL, NULL,
    NULL, NULL, '{"residencyPeriod": "경기도 3년 이상 거주 또는 합산 8년 이상 거주"}'::jsonb),
((SELECT id FROM policies WHERE title = '경기도 청년면접수당'), 18, 34, NULL, NULL, NULL, NULL,
    '["UNEMPLOYED", "JOB_SEEKER"]'::jsonb, NULL, NULL);
