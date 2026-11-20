# 틈타 서버 (Teumta-Server)

틈타 앱이 쓰는 1호선 역·시간표와 회원 API 서버다. Spring Boot 4, Java 25.

| API | 로그인 | 설명 |
| --- | --- | --- |
| `GET /api/stations` | 필요 없음 | 역 102개 |
| `GET /api/trains?dayType=WEEKDAY` (또는 `HOLIDAY`) | 필요 없음 | 평일·휴일 열차 전체 |
| `POST /api/auth/signup` | 필요 없음 | 회원가입. 가입하면 바로 토큰을 준다 |
| `POST /api/auth/login` | 필요 없음 | 로그인. access token(30분)과 refresh token(14일) |
| `POST /api/auth/refresh` | 필요 없음 | 토큰 갱신. 쓴 refresh token은 다시 못 쓴다 |
| `POST /api/auth/logout` | 필요 없음 | 이 기기의 refresh token을 지운다 |
| `GET /api/members/me` | 필요 | 내 정보(자주 쓰는 두 역 ID 포함, 고르지 않았으면 null) |
| `PUT /api/members/me/stations` | 필요 | 자주 쓰는 두 역 저장. `{originStationId, destinationStationId}`, null이면 지운다 |
| `DELETE /api/members/me` | 필요 | 회원 탈퇴 |
| `GET /actuator/health/readiness` | 필요 없음 | 요청을 받을 준비가 됐는지 (시드를 넣는 동안은 503) |

로그인이 필요한 API는 `Authorization: Bearer <access token>` 헤더를 붙인다. 오류는 모두 `{code, message, fieldErrors}` 모양이다.

## 내 컴퓨터에서 실행

```bash
./gradlew bootRun
```

메모리 DB(H2)로 켜진다. API 문서는 http://localhost:8080/swagger-ui.html 에서 본다.

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
   cp .env.example .env && nano .env   # DOMAIN, DATABASE_PASSWORD, JWT_SECRET을 바꾼다
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

- 시드는 DB가 비어 있을 때만 들어간다. 시간표를 새로 넣으려면 DB를 비워야 한다(`docker compose down --volumes`는 DB 데이터를 지우니 주의).
- jar는 내 컴퓨터에서 만든다. EC2(1GB)에서 Gradle로 빌드하면 메모리가 모자란다.
- `.env`에는 DB 비밀번호와 JWT 서명 키가 있어서 저장소에 올리지 않는다(`.gitignore`).
- `prod` 프로필은 `JWT_SECRET`이 없으면 켜지지 않는다. 개발용 키로 배포되는 일을 막는다.
- 서버는 접속한 사람의 IP를 남기지 않는다. Caddy는 요청 기록(access log)을 켜지 않았고, 오류 기록에서도 IP와 요청 헤더를 지운다(`Caddyfile`). 개인정보처리방침에 약속한 내용이라 로그 설정을 바꾸면 방침도 함께 고친다.
