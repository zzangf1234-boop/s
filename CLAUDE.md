# 작업 규칙 (사장님 요청)

- 소통은 한국어. 사장님은 코드를 모르심 → 결과·설치 방법 위주로 쉽게 설명
- **버전 이름** (`VERSION` 파일): 새 기능 추가 = 0.1 올림 (1.2.1 → 1.3.0), 수정·오류 수정 = 0.0.1 올림 (1.2.1 → 1.2.2).
  앱을 바꾸는 커밋마다 VERSION 을 올리고, 한 번 빌드에 기능과 수정이 같이 있으면 기능 기준(0.1)으로 올림.
  versionCode 는 빌드 번호로 자동 (100 + GITHUB_RUN_NUMBER)
- "기록했다가 한번에 고치자" 요청이 있었음: 사장님이 바로 업데이트를 원하지 않으면 BACKLOG.md 에 적어 두고 모아서 빌드
- 빌드는 GitHub Actions (main 푸시). 앱 변경 없는 커밋은 메시지에 `[skip ci]`
- 서명 키 비밀번호(GitHub Secret DID_KEYSTORE_PASSWORD)는 절대 읽거나 출력하지 않음. 공개 저장소이므로 비밀값 커밋 금지
- 공개 GitHub Release 만들지 않음 (사장님 허락 전까지). APK 는 Actions Artifacts 에서 받음
- main 강제 푸시 금지
- 웹 화면 수정: tools/*.html 수정 후 `python3 tools/gen_pages.py` 로 Java 상수 재생성
