# 운영 관측성

## 운영 결정

현재 운영 환경은 OCI 단일 VM과 AWS 단일 EC2로 분리되어 있다. 이 구조에서 Kubernetes를 추가해도 노드 장애를 견디는 고가용성이 생기지 않으며 제어 평면, 네트워크, 영속 볼륨 장애 지점만 늘어난다. 따라서 각 호스트는 Docker Compose와 systemd로 운영한다. 두 개 이상의 장애 도메인에 노드를 확보하고 관리형 Kubernetes 비용을 감당할 시점에 다시 검토한다.

OCI에는 Prometheus, Grafana, Loki, Alloy, node-exporter, blackbox-exporter를 컨테이너로 실행한다. Prometheus는 OmniConnect API와 Hannah AI 메트릭을 수집하고 Alloy는 Docker 로그를 Loki로 전달한다. Blackbox exporter는 `hanaomni.cloud`와 `api.hanaomni.cloud`의 HTTPS 상태를 실제 외부 경로로 검사한다.

`/actuator/prometheus`는 내부 Docker 네트워크의 Prometheus 수집을 위해 애플리케이션 인증에서 제외하지만, Nginx가 외부 요청에는 `404`를 반환한다.

Grafana 컨테이너는 `127.0.0.1:3300`에만 바인딩하고 Nginx HTTPS 프록시를 통해 `https://api.hanaomni.cloud/grafana/`로 제공한다. `admin`과 `GRAFANA_ADMIN_PASSWORD`로 로그인하며 익명 접근은 허용하지 않는다. Grafana 공식 외부 URL도 같은 주소로 고정해 Omni-Connect와 Hannah Discord 오류 알림의 링크가 `localhost`를 가리키지 않게 한다. Prometheus와 Loki는 호스트 포트를 공개하지 않는다.

## 알림 경로

- API·AI 프로세스 중단, 공개 HTTPS 장애, ERROR 로그, 디스크 여유 공간 10% 미만: Grafana Alerting이 서비스별 Discord 웹훅으로 전송한다.
- 신규 회원, API 이용 신청·승인·검토, 세무 신청·승인, 금융 용어 설명 클릭: 데이터 저장이 성공한 후 전용 큐에서 비동기로 전송한다.
- AI 모델 재학습 시작·완료: Hannah AI 웹훅으로 전송한다.
- 알림 본문에는 비밀번호, 전화번호, API 키, 세무 문서 내용, 금액을 넣지 않는다.

## 필수 GitHub Secrets

기존 운영 secret에 다음을 추가한다.

- `OMNI_CONNECT_DISCORD_WEBHOOK_URL`
- `HANNAH_DISCORD_WEBHOOK_URL`
- `GRAFANA_ADMIN_PASSWORD`: 32자 이상의 무작위 값

웹훅 URL은 저장소 파일, 로그, PR 본문에 넣지 않는다. 유출이 의심되면 Discord에서 즉시 재발급하고 GitHub Secret만 갱신한다.

## 운영 명령

```bash
sudo systemctl status hana-omni-connect-monitoring
sudo systemctl reload hana-omni-connect-monitoring
docker compose --env-file /opt/hana-omni-connect-api/monitoring.env \
  -f /opt/hana-omni-connect-api/deploy/monitoring/compose.yml ps
```

메트릭은 15일/8GB, 로그는 14일 보관한다. 영속 볼륨은 서버 백업 대상에 포함한다.
