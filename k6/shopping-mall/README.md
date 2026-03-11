# shopping-mall k6 테스트

## 비관적 락 테스트 결과

`coupon-only-load.js` 기준으로 쿠폰 발행 API의 `issue_coupon_pessimistic_lock` 시나리오를 측정했다.

### 결과 요약

- `http_req_duration{phase:measure,kind:issue_coupon_pessimistic_lock}` 의 `p(95)` 는 `50.47s` 로, 임계값 `500ms` 를 크게 초과했다.
- `http_req_failed{phase:measure,kind:issue_coupon_pessimistic_lock}` 실패율은 `72.77%` 로, 목표 임계값 `1%` 를 만족하지 못했다.
- 쿠폰 발행 응답 허용 체크 성공률은 `24%` (`19,646 / 80,981`) 수준이었다.
- 전체 HTTP 처리량은 `190.17 req/s`, iteration 처리량은 `89.70 it/s` 였다.
- `iteration_duration` 의 `p(95)` 는 `2m 49s` 로 측정되어 요청 단위뿐 아니라 전체 실행 시간도 크게 증가했다.

### 상세 지표

```text
THRESHOLDS

http_req_duration{phase:measure,kind:issue_coupon_pessimistic_lock}
✗ 'p(95) < 500' p(95)=50.47s

http_req_failed{phase:measure,kind:issue_coupon_pessimistic_lock}
✗ 'rate < 0.01' rate=72.77%

TOTAL RESULTS

checks_total.......: 244074 538.794638/s
checks_succeeded...: 74.87% 182739 out of 244074
checks_failed......: 25.12% 61335 out of 244074

쿠폰 발행 응답 허용: 24% — 19646 / 61335
쿠폰 발행 성공 시 ID 존재: 성공
쿠폰 발행 성공 시 storeId 일치: 성공
쿠폰 발행 성공 시 userId 일치: 성공
쿠폰 발행 성공 시 issuedAt 존재: 성공

http_req_duration........................................: avg=8.08s  min=0s     med=65.84ms max=2m12s p(90)=28.93s p(95)=50.47s
  { expected_response:true }.............................: avg=20.22s min=3.88ms med=20.25s  max=2m12s p(90)=35.59s p(95)=45.4s
  { phase:measure,kind:issue_coupon_pessimistic_lock }...: avg=8.08s  min=0s     med=65.71ms max=2m12s p(90)=28.93s p(95)=50.47s
http_req_failed..........................................: 72.76% 62687 out of 86145
  { phase:measure,kind:issue_coupon_pessimistic_lock }...: 72.77% 62687 out of 86134
http_reqs................................................: 86145 190.16554/s

iteration_duration.......................................: avg=1m28s  min=1.22ms med=1m9s    max=3m59s p(90)=2m40s p(95)=2m49s
iterations...............................................: 40632 89.695354/s
vus......................................................: 297   min=0     max=73240
vus_max..................................................: 100000 min=14331 max=100000

data_received............................................: 7.2 MB 16 kB/s
data_sent................................................: 11 MB 25 kB/s
```

### 해석

- 비관적 락 경쟁이 심해지면서 상당수 요청이 실패했고, 성공 요청조차 응답 시간이 길게 늘어났다.
- 중앙값은 비교적 낮지만 `p(90)` 과 `p(95)` 가 급격히 증가해 긴 꼬리 지연이 매우 큰 상태다.
- 임계값 두 개가 모두 실패했고 전체 실패율도 높아 현재 조건에서는 안정적인 쿠폰 발행 처리로 보기 어렵다.

### 결과 이미지

#### 애플리케이션 지표

![비관적 락 애플리케이션 지표](./img/pessmistic-app.png)

#### DB 지표

![비관적 락 DB 지표](./img/pessmistic-db.png)

### 후속 확인 포인트

- 실패 요청의 실제 상태 코드와 예외 유형을 애플리케이션 로그에서 우선 확인한다.
- DB 락 대기 시간, 커넥션 풀, 스레드 풀 고갈 여부를 함께 점검한다.
- VU 증가 구간별로 응답 시간과 실패율이 어떻게 악화되는지 단계적으로 재측정한다.

## Redis Cluster 연결 가이드

- Redis Cluster 연결 가이드는 프로젝트 루트 `README.md`를 참고한다.
