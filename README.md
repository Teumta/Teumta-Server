# 틈타 서버 (Teumta-Server)

틈타 앱이 쓰는 1호선 역·시간표 API 서버다. Spring Boot 4, Java 25.

| API | 설명 |
| --- | --- |
| `GET /api/stations` | 역 102개 |
| `GET /api/trains?dayType=WEEKDAY` (또는 `HOLIDAY`) | 평일·휴일 열차 전체 |
| `GET /actuator/health/readiness` | 요청을 받을 준비가 됐는지 (시드를 넣는 동안은 503) |

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
   cp .env.example .env && nano .env   # DOMAIN, DATABASE_PASSWORD를 바꾼다
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

### 알아 둘 것

- 시드는 DB가 비어 있을 때만 들어간다. 시간표를 새로 넣으려면 DB를 비워야 한다(`docker compose down --volumes`는 DB 데이터를 지우니 주의).
- jar는 내 컴퓨터에서 만든다. EC2(1GB)에서 Gradle로 빌드하면 메모리가 모자란다.
- `.env`에는 DB 비밀번호가 있어서 저장소에 올리지 않는다(`.gitignore`).
- 서버에 접속하면 접속 기록(IP)이 남는다. 앱 출시 전에 개인정보처리방침을 맞춘다.
