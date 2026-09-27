#!/usr/bin/env python3
"""정부24 공공서비스(혜택) API 전량 수집 스크립트.

표준 라이브러리만 사용하므로 별도 설치 없이 macOS 기본 python3로 실행된다.

사용법:
    export GOV24_API_KEY='공공데이터포털 일반 인증키(Decoding)'
    python3 scripts/policy-seed/fetch_gov24.py

결과: scripts/policy-seed/raw/{serviceList,serviceDetail,supportConditions}.json
(raw/는 .gitignore 처리되어 커밋되지 않는다)
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

BASE_URL = "https://api.odcloud.kr/api/gov24/v3"
ENDPOINTS = ["serviceList", "serviceDetail", "supportConditions"]
PER_PAGE = 1000
MAX_PAGES = 50
RAW_DIR = Path(__file__).resolve().parent / "raw"


def fetch_page(endpoint: str, page: int, api_key: str) -> dict:
    query = urllib.parse.urlencode({"page": page, "perPage": PER_PAGE, "serviceKey": api_key})
    url = f"{BASE_URL}/{endpoint}?{query}"
    request = urllib.request.Request(url, headers={"User-Agent": "mozip-policy-seed/1.0"})
    last_error = None
    for attempt in range(1, 6):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                body = json.loads(response.read().decode("utf-8"))
            if isinstance(body.get("code"), int) and body["code"] < 0:
                # 인증키 오류 등은 재시도해도 소용없으므로 즉시 중단
                sys.exit(f"[fetch] API 오류 {body['code']}: {body.get('msg')}")
            if not isinstance(body.get("data"), list):
                raise ValueError(f"data 배열 없음: {str(body)[:200]}")
            return body
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", "replace")[:300]
            if error.code in (401, 403):
                sys.exit(f"[fetch] 인증 실패(HTTP {error.code}). 키 승인 직후라면 1~2시간 뒤 다시 시도하세요.\n{detail}")
            last_error = f"HTTP {error.code} {detail}"
        except Exception as error:  # 네트워크 일시 오류 재시도
            last_error = repr(error)
        wait = attempt * 3
        print(f"[fetch] {endpoint} p{page} 실패({attempt}/5), {wait}s 후 재시도: {last_error}")
        time.sleep(wait)
    sys.exit(f"[fetch] {endpoint} p{page} 최종 실패: {last_error}")


def fetch_all(endpoint: str, api_key: str) -> list:
    rows, total = [], None
    for page in range(1, MAX_PAGES + 1):
        body = fetch_page(endpoint, page, api_key)
        total = total if total is not None else body.get("totalCount")
        rows.extend(body["data"])
        print(f"[fetch] {endpoint} p{page}: 누적 {len(rows)}/{total}")
        if not body["data"] or (total is not None and len(rows) >= total):
            break
        time.sleep(0.3)
    return rows


def main() -> None:
    api_key = os.environ.get("GOV24_API_KEY", "").strip()
    if not api_key:
        sys.exit("[fetch] GOV24_API_KEY 환경변수를 설정하세요 (일반 인증키 Decoding 값)")
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    fetched_at = datetime.now(timezone.utc).isoformat()
    for endpoint in ENDPOINTS:
        rows = fetch_all(endpoint, api_key)
        out = RAW_DIR / f"{endpoint}.json"
        out.write_text(json.dumps({"fetchedAt": fetched_at, "data": rows}, ensure_ascii=False), encoding="utf-8")
        print(f"[fetch] 저장: {out} ({len(rows)}건)")
    print("[fetch] 완료")


if __name__ == "__main__":
    main()
