# 틈타 서버 (Teumta-Server)

틈타 앱이 쓰는 1호선 역·시간표, 실시간 열차, 회원 API 서버다. Spring Boot 4, Java 25.

- 앱: [Teumta-Android](https://github.com/Teumta/Teumta-Android)
- 배포: `https://teumta.duckdns.org` (EC2 + Docker Compose + Caddy)
- 웹 문서: [개인정보처리방침](https://teumta.github.io/Teumta-Server/privacy.html) · [계정 삭제 안내](https://teumta.github.io/Teumta-Server/delete-account.html)

출발 시각 계산은 앱이 휴대폰 안에서 한다. 서버는 데이터(역·시간표), 서울시 실시간 위치 중계, 회원 기능만 맡는다.

| API | 로그인 | 설명 |
| --- | --- | --- |
| `GET /api/stations` | 필요 없음 | 역 102개 |
| `GET /api/trains?dayType=WEEKDAY` (또는 `HOLIDAY`) | 필요 없음 | 평일·휴일 열차 전체 |
| `GET /api/realtime/arrivals?stationId=…&towardStationId=…` | 필요 없음 | 탈 역으로 오는 열차(서울시 실시간 열차 위치). `towardStationId`(내릴 역)를 주면 그 역까지 가는 열차만. 볼 수 없으면 503 `REALTIME_UNAVAILABLE` |
| `POST /api/auth/signup` | 필요 없음 | 회원가입. 가입하면 바로 토큰을 준다 |
| `POST /api/auth/login` | 필요 없음 | 로그인. access token(30분)과 refresh token(14일) |
| `POST /api/auth/refresh` | 필요 없음 | 토큰 갱신. 쓴 refresh token은 다시 못 쓴다 |
| `POST /api/auth/logout` | 필요 없음 | 이 기기의 refresh token을 지운다 |
| `GET /api/members/me` | 필요 | 내 정보(자주 쓰는 두 역 ID 포함, 고르지 않았으면 null) |
| `PUT /api/members/me/stations` | 필요 | 자주 쓰는 두 역 저장. `{originStationId, destinationStationId}`, null이면 지운다 |
| `DELETE /api/members/me` | 필요 | 회원 탈퇴 |
| `GET /actuator/health/readiness` | 필요 없음 | 요청을 받을 준비가 됐는지 (시드를 넣는 동안은 503) |

로그인이 필요한 API는 `Authorization: Bearer <access token>` 헤더를 붙인다. 오류는 모두 `{code, message, fieldErrors}` 모양이다.

## 기술 스택

| 분류 | 쓴 것 |
| --- | --- |
| 언어·프레임워크 | Java 25, Spring Boot 4.1.2 (Web MVC, Data JPA, Validation, Actuator) |
| 인증 | Spring Security + OAuth2 Resource Server(JWT 확인). access token 30분, refresh token 14일·한 번 쓰면 바뀜(회전) |
| DB | 개발·테스트 H2(메모리), 배포 PostgreSQL 17 |
| API 문서 | springdoc-openapi (내 컴퓨터에서만 켬) |
| 배포 | Docker Compose, Caddy(HTTPS 자동 인증서), DuckDNS |

## 구조

```
src/main/java/com/teumta/server/
  station/     역 102개 조회
  timetable/   평일·휴일 열차와 정차 시각 조회 (열차와 정차역을 한 번에 가져와 N+1을 막음)
  realtime/    서울시 1호선 열차 위치를 받아, 탈 역으로 오는 열차만 골라 줌
  auth/        회원가입·로그인·토큰 갱신·로그아웃, refresh token 저장
  member/      내 정보, 자주 쓰는 두 역 저장, 탈퇴
  seed/        서버가 켜질 때 DB가 비어 있으면 역·시간표 넣기
  common/      오류 응답 모양, 보안 설정, 시계(Clock) 주입
src/main/resources/seed/   stations.json, timetable-2026-11-06.json (TAGO 지하철정보, 2026-11-06 기준)
docs/                      GitHub Pages로 게시하는 웹 문서
```

## 내 컴퓨터에서 실행

```bash
./gradlew bootRun
```

메모리 DB(H2)로 켜진다. API 문서는 http://localhost:8080/swagger-ui.html 에서 본다.

실시간 API까지 보려면 서울 열린데이터광장 인증키를 환경 변수 `SEOUL_SUBWAY_API_KEY`로 넣고 켠다. 없으면 실시간만 503이고 나머지는 그대로 동작한다.

## 테스트

```bash
./gradlew test
```

48개. H2로 실제 API를 불러 보는 테스트가 중심이다.

| 테스트 | 확인하는 것 |
| --- | --- |
| `TimetableApiTest`, `TrainQueryCountTest` | 역·시간표 응답, 열차 전체를 가져올 때 쿼리 수(N+1이 아닌지) |
| `AuthApiTest` | 가입·로그인·갱신·로그아웃·탈퇴, 쓴 refresh token 다시 쓰기 막기, 오류 모양 |
| `MemberStationsApiTest` | 자주 쓰는 두 역 저장·지우기, 없는 역·같은 두 역 거절, 탈퇴하면 같이 지움 |
| `RealtimeArrivalApiTest`, `RealtimeArrivalServiceTest` | 탈 역으로 오는 열차 고르기, 90초 캐시, 하루 950번 제한, 받는 중 동시 요청 |
| `SeoulSubwayClientTest` | 서울시 응답(정상·데이터 없음·한도 초과) 읽기. 응답은 `src/test/resources/realtime/`에 저장해 둔 파일 |
| `SeedLoaderTest` | 시각을 운행일 0시 기준 초로 바꾸기, 자정 넘긴 시각(24시 이후) |

테스트는 시각을 `Clock`으로 주입받아, 90초·2분 뒤 같은 상황을 기다리지 않고 만든다.

## 배포 (EC2 + Docker Compose + Caddy)

`compose.yaml`이 세 컨테이너를 함께 띄운다.

| 컨테이너 | 하는 일 |
| --- | --- |
| `db` | PostgreSQL 17. 데이터는 `db-data` 볼륨에 남는다 |
| `app` | 이 서버(`prod` 프로필). 밖으로 포트를 열지 않는다 |
| `caddy` | HTTPS 앞단. `.env`의 `DOMAIN`으로 Let's Encrypt 인증서를 자동으로 받는다 |

### 처음 한 번

1. **EC2 만들기** (AWS 콘솔)
   - Ubuntu 24.04, 프리 티어 대상 인스턴스(메모리 1GB), 디스크 20GB
   - 키 페어를 새로 만들고 `.pem` 파일을 받아 둔다(다시 받을 수 없다. 잃어버리면 접속할 수 없다)
   - 보안 그룹: 22(SSH, 내 IP만), 80, 443(모두)
   - 고정 주소가 필요하면 탄력적 IP를 붙인다(인스턴스를 껐다 켜도 주소가 바뀌지 않는다)
2. **도메인** (DuckDNS): 이름을 하나 만들고(예: `teumta`), EC2의 공인 IP를 넣는다
3. **내 컴퓨터에 접속 설정** (한 번만)
   ```bash
   mv ~/Downloads/<키 이름>.pem ~/.ssh/teumta.pem && chmod 400 ~/.ssh/teumta.pem
   ```
   `~/.ssh/config`에 아래를 덧붙인다.
   ```
   Host teumta
     HostName <EC2 공인 IP>
     User ubuntu
     IdentityFile ~/.ssh/teumta.pem
     IdentitiesOnly yes
   ```
   이제 `ssh teumta`로 접속하고, `scp 파일 teumta:경로`로 올린다.
   - 키를 지정하지 않으면 `Permission denied (publickey)`가 난다. ssh가 EC2 키 대신 기본 키로 접속하려 하기 때문이다.
   - `chmod 400`: 키 파일을 나만 읽게 한다. 다른 사람도 읽을 수 있으면 ssh가 키를 쓰지 않는다.
   - 키 파일은 저장소 폴더 안에 두지 않는다(실수로 커밋될 수 있다).
4. **EC2에 Docker 설치와 스왑** (`ssh teumta`로 접속해서)
   ```bash
   sudo apt update && sudo apt install -y docker.io docker-compose-v2
   sudo usermod -aG docker $USER   # 다시 접속하면 sudo 없이 docker를 쓴다
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```
   메모리 1GB에 서버·DB·Caddy를 함께 띄우므로, 잠깐 몰릴 때를 대비해 스왑(디스크를 메모리처럼 쓰는 공간)을 둔다.
5. **파일 올리기** (내 컴퓨터에서)
   ```bash
   ./gradlew bootJar
   ssh teumta 'mkdir -p ~/teumta/build/libs'
   scp Dockerfile compose.yaml Caddyfile .env.example teumta:~/teumta/
   scp build/libs/teumta-server.jar teumta:~/teumta/build/libs/
   ```
6. **비밀값 넣고 띄우기** (EC2에서)
   ```bash
   cd ~/teumta
   cp .env.example .env && nano .env   # DOMAIN, DATABASE_PASSWORD, JWT_SECRET, SEOUL_SUBWAY_API_KEY를 채운다
   docker compose up -d --build
   ```
7. **확인**: `https://<DOMAIN>/api/stations`가 역 102개를 돌려주면 된다

### 코드를 바꿨을 때

```bash
./gradlew bootJar
scp build/libs/teumta-server.jar teumta:~/teumta/build/libs/
ssh teumta 'cd ~/teumta && docker compose up -d --build app'
```

### 자주 쓰는 명령 (EC2에서)

```bash
docker compose ps              # 세 컨테이너 상태 (app이 healthy여야 한다)
docker compose logs -f app     # 서버 로그
docker compose down            # 내리기 (데이터 볼륨은 남는다)
```

### 메일로 계정 삭제 요청이 오면

앱을 쓸 수 없는 사람은 [계정 삭제 안내](https://teumta.github.io/Teumta-Server/delete-account.html)(`docs/delete-account.html`)를 보고 메일로 요청한다. Play 정책상 이 웹 페이지가 있어야 하고, 개인정보처리방침에 10일 안에 처리한다고 약속했다.

1. **본인 확인**: 보낸 주소가 가입한 이메일과 같은지 본다. 다르면 가입한 주소로 확인 메일을 보내고 답을 받은 뒤에 지운다.
2. **지우기**: 앱의 탈퇴(`DELETE /api/members/me`)와 같은 순서로 로그인 정보부터 지운다. 이메일은 소문자로 저장돼 있다.
   ```bash
   ssh teumta
   cd ~/teumta && docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
   ```
   ```sql
   BEGIN;
   DELETE FROM refresh_token WHERE member_id = (SELECT id FROM member WHERE email = '가입한 이메일(소문자)');
   DELETE FROM member WHERE email = '가입한 이메일(소문자)';
   -- 두 번째 DELETE가 "DELETE 1"이면 COMMIT, 0이면 없는 계정이므로 ROLLBACK
   COMMIT;
   ```
3. **답장**: 지웠다고 알린다. 요청 메일과 답장은 처리가 끝나고 1년 안에 지운다(개인정보처리방침 7장).

### 알아 둘 것

- 시드는 DB가 비어 있을 때만 들어간다. 시간표를 새로 넣으려면 DB를 비워야 한다(`docker compose down --volumes`는 DB 데이터를 지우니 주의). 회원 정보도 같이 지워지므로, 시간표만 바꾸는 절차는 아직 없다.
- DB 테이블은 JPA가 만든다(`ddl-auto: update`). 칸 이름을 바꿀 때는 미리 복사본 DB에서 해 보고 배포한다.
- jar는 내 컴퓨터에서 만든다. EC2(1GB)에서 Gradle로 빌드하면 메모리가 모자란다.
- `.env`에는 DB 비밀번호와 JWT 서명 키, 서울시 인증키가 있어서 저장소에 올리지 않는다(`.gitignore`).
- 실시간(서울 열린데이터광장 열차 위치)은 하루 1,000번까지다. 서버는 1호선 전체 위치를 90초에 한 번만 받아 모든 역에 같이 쓰고, 하루 950번이 되면 그날은 더 묻지 않는다(503). `SEOUL_SUBWAY_API_KEY`가 비어 있으면 실시간만 꺼지고 나머지는 그대로 동작한다.
- 여러 사람이 동시에 물어도 서울시에는 한 번만 묻는다(`ReentrantLock`). 누가 이미 받는 중이면, 받은 지 2분 안의 성공한 값은 기다리지 않고 바로 준다(`tryLock`). 2분이 넘었거나 지난번에 실패했으면 기다렸다가 새 값을 같이 쓴다. 받은 지 5분이 넘은 위치는 버린다.
- 서울시 위치 정보에는 기점에서 출발을 기다리는 열차가 빠질 때가 있다. 광명은 셔틀이 금천구청까지 온 것까지만, 서동탄은 서 있는 열차가 보이는 동안만 나온다.
- `prod` 프로필은 `JWT_SECRET`이 없으면 켜지지 않는다. 개발용 키로 배포되는 일을 막는다.
- 서버는 접속한 사람의 IP를 남기지 않는다. Caddy는 요청 기록(access log)을 켜지 않았고, 오류 기록에서도 IP와 요청 헤더를 지운다(`Caddyfile`). 개인정보처리방침에 약속한 내용이라 로그 설정을 바꾸면 방침도 함께 고친다.

## 웹 문서 (GitHub Pages)

`main`의 `docs/` 폴더가 https://teumta.github.io/Teumta-Server/ 로 게시된다. Play 스토어와 앱이 이 주소를 본다.

| 파일 | 내용 |
| --- | --- |
| `privacy.html` | 지금 개인정보처리방침 (0.3.1, 2026-11-29 시행) |
| `privacy-0.1.0.html` ~ `privacy-0.3.0.html` | 이전 방침 보관본. 개정할 때 지금 본문을 새 보관본으로 옮긴 뒤 고친다 |
| `delete-account.html` | 앱을 쓸 수 없는 사람을 위한 계정 삭제 안내 (Play 요구 사항) |
| `index.html`, `style.css`, `icon.png` | 첫 화면과 공통 모양 |

방침을 바꾸면 앱 안의 방침 안내(Teumta-Android)도 같은 문구로 맞춘다.

## 작업 규칙

- 이슈 → `dev`에서 브랜치(`feat/…`, `fix/…`, `refactor/…`, `docs/…`) → `dev`로 PR → 스프린트 끝에 `[Release]` PR로 `main`.
- 커밋은 `[#이슈]- 유형: 내용` (예: `[#38]- refactor: 실시간 위치를 받는 중이면 지난 값을 바로 주기`).
- `Closes #번호`는 `main`에 머지될 때만 이슈를 닫는다. `[Release]` PR 본문에 모아 적는다.
- `main`에 머지한 뒤 EC2에 배포한다(위 "코드를 바꿨을 때").
