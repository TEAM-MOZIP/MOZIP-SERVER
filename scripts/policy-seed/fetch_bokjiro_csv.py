#!/usr/bin/env python3
"""한국사회보장정보원_복지서비스정보 (odcloud API) 전량 수집.

사용법:
    export BOKJIRO_API_KEY='공공데이터포털 일반 인증키(Decoding)'
    python3 scripts/policy-seed/fetch_bokjiro_csv.py

결과: scripts/policy-seed/raw/bokjiro_odcloud.json
(raw/*.json 은 .gitignore 처리됨)
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

BASE_URL = "https://api.odcloud.kr/api/15083323/v1"
# 2026-08-19 최신 버전 UDDI
RESOURCE = "uddi:e02ed092-d46d-4bf2-8e7e-c3441e950fef"
PER_PAGE = 1000
MAX_PAGES = 10
RAW_DIR = Path(__file__).resolve().parent / "raw"


def fetch_page(page: int, api_key: str) -> dict:
    query = urllib.parse.urlencode({
        "page": page,
        "perPage": PER_PAGE,
        "returnType": "JSON",
        "serviceKey": api_key,
    })
    url = f"{BASE_URL}/{RESOURCE}?{query}"
    req = urllib.request.Request(url, headers={"User-Agent": "mozip-policy-seed/1.0"})
    last_error = None
    for attempt in range(1, 6):
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                body = json.loads(resp.read().decode("utf-8"))
            if not isinstance(body.get("data"), list):
                raise ValueError(f"data 배열 없음: {str(body)[:200]}")
            return body
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")[:300]
            if e.code in (401, 403):
                sys.exit(f"[fetch] 인증 실패(HTTP {e.code}). 키를 확인하세요.\n{detail}")
            last_error = f"HTTP {e.code}: {detail}"
        except Exception as e:
            last_error = repr(e)
        wait = attempt * 3
        print(f"[fetch] p{page} 실패({attempt}/5), {wait}s 후 재시도: {last_error}")
        time.sleep(wait)
    sys.exit(f"[fetch] p{page} 최종 실패: {last_error}")


def main():
    api_key = os.environ.get("BOKJIRO_API_KEY", "").strip()
    if not api_key:
        sys.exit("[fetch] BOKJIRO_API_KEY 환경변수가 없습니다.")

    RAW_DIR.mkdir(parents=True, exist_ok=True)
    all_rows = []
    total_count = None

    for page in range(1, MAX_PAGES + 1):
        body = fetch_page(page, api_key)
        rows = body["data"]
        if total_count is None:
            total_count = body.get("totalCount", 0)
            print(f"[fetch] 총 {total_count}건, {PER_PAGE}건씩 수집 시작")
        all_rows.extend(rows)
        print(f"[fetch] p{page}: {len(rows)}건 수집 (누계 {len(all_rows)}건)")
        if len(all_rows) >= total_count:
            break
        if len(rows) < PER_PAGE:
            break
        time.sleep(0.5)

    out = {
        "fetchedAt": datetime.now(timezone.utc).isoformat(),
        "totalCount": total_count,
        "data": all_rows,
    }
    out_path = RAW_DIR / "bokjiro_odcloud.json"
    out_path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[fetch] 완료: {len(all_rows)}건 → {out_path}")
    print("[fetch] 컬럼:", list(all_rows[0].keys()) if all_rows else "없음")


if __name__ == "__main__":
    main()
