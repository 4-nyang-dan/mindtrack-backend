# MindTrack Backend (Spring Boot)

사용자의 화면 스크린샷을 업로드 받아 **유사도 기반 샘플링** → **AI 재분석 트리거** → **결과 캐시/재사용** → **SSE 구독·발행으로 프론트 실시간 반영** 흐름을 제공하는 백엔드 서비스입니다.  
인증은 **JWT**, 데이터 저장은 **PostgreSQL**, 캐시는 **Redis**를 사용합니다.

---

## 목차
- [백엔드 개요](#백엔드-개요)
- [프로젝트 구조](#프로젝트-구조)
- [요구사항](#요구사항)
- [주요 구현 내용](#주요-구현-내용)
  - [1. 인증(요약)](#1-인증요약)
  - [2. 샘플링](#2-샘플링)
  - [3. Redis 캐시 구성 & 키 설계](#3-redis-캐시-구성--키-설계)
  - [4. SSE 기반 실시간 전송](#4-sse-기반-실시간-전송)
- [API 명세](#api-명세)
- [Appendix – Sampling Details](#appendix--sampling-details)

---

## 백엔드 개요

- **Back Server (Spring Boot)**
  - `/upload-screenshot`에서 이미지 수신
  - **2·3차 샘플링**(dHash 해밍거리 기반 후보 탐색 → SSIM 재검증)
  - **Redis 캐시**(최근 해시/썸네일/원본)와 **DB(ScreenshotImage)** 업데이트
  - `PENDING` 상태 이미지를 AI 워커가 집도록 준비
  - `/api/suggestions/latest`에서 `created_at` 기준 **가장 최신** 질문/답변 묶음 조회 (프론트 최초 1회)
  - `/api/suggestions/stream`에서 **최초 1회 이후 생성되는 결과**를 **SSE**로 실시간 스트리밍

---

## 프로젝트 구조
~~~
main
└─ java
   └─ com.example.mindtrack
      ├─ Config
      ├─ Controller
      ├─ Domain
      ├─ DTO
      ├─ Enum
      ├─ Repository
      ├─ Service
      ├─ SSE
      └─ Util
resources
└─ db.migration
└─ application.properties
~~~

---

## 요구사항

- JDK 17+
- PostgreSQL, Redis
- Gradle 또는 Maven

---

# 주요 구현 내용

## 1. 인증(요약)

- **SecurityConfig + JWT + JWTFilter** 기반
- 기본 플로우
  1) `POST /api/auth/signup` : 회원가입  
  2) `POST /api/auth/login` : 로그인(JWT 발급)  
  3) 보호된 API 호출 시 `Authorization: Bearer <access_token>`

---

## 2. 샘플링

### ScreenshotImage (JPA Entity)

~~~java
@Entity
public class ScreenshotImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "user_id")
    private Users user;

    private Long imageHash;
    private int visitCnt;
    private LocalDateTime capturedAt;
    private LocalDateTime lastVisitedAt;

    @Column(columnDefinition = "TEXT")
    private String analysisResult;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private AnalysisStatus analysisStatus;
}
~~~

### AnalysisStatus

| 상태 | 의미 | 전이(예시) |
|---|---|---|
| `PENDING` | 분석 대기 | 업로드 직후, 또는 유사 이미지 **재분석 예약** 시 |
| `IN_PROGRESS` | 분석 중 | AI 워커가 집었을 때 |
| `DONE` | 분석 완료 | AI 결과 저장 완료 후 |
| `FAILED` | 분석 실패 | 오류 발생 시 |

---

### 유사도 샘플링 파이프라인 처리 흐름 (2·3차) (요약)

### 처리 흐름(요약)

~~~mermaid
sequenceDiagram
    participant FE as Electron Frontend
    participant BE as Spring Boot
    participant R as Redis
    participant DB as Postgres
    participant AI as FastAPI Worker

    FE->>BE: POST /upload-screenshot (multipart image + userId, JWT)
    BE->>BE: dHash 계산
    BE->>R: 캐시에서 근접 후보 탐색 (해밍<=6)
    alt 후보 있음
        R-->>BE: imageId, hash, thumb
        BE->>BE: SSIM 재검증
        alt 0.85 이상
            BE->>DB: 기존 행 visitCnt 증가 및 PENDING 전환
            BE->>R: 기존 imageId로 원본 덮어쓰기
            BE-->>FE: prevImageId, similarity, message
            AI->>DB: PENDING 집어서 IN_PROGRESS → DONE
        else 0.85 미만
            BE->>DB: 새 행 INSERT PENDING
            BE->>R: recent 해시 + 썸네일 + 원본 캐시
            BE-->>FE: currentImageId
        end
    else 후보 없음
        BE->>DB: 새 행 INSERT PENDING
        BE->>R: recent 해시 + 썸네일 + 원본 캐시
        BE-->>FE: currentImageId
    end
~~~

---

## 샘플링 알고리즘 개요 (dHash + SSIM)

- **2차 후보 탐색(dHash + 해밍거리)**  
  - 입력 이미지를 `17×16` 그레이스케일로 리사이즈 후, 인접 픽셀 차이를 이진 비트로 누적 → `imageHash`  
  - Redis의 `recentImageHashes`에서 **해밍거리 ≤ 6** 이면서 **similarity ≥ 0.97**(비트 기반)인 후보만 선별

- **3차 정밀 검증(SSIM)**  
  - 후보의 썸네일(캐시)과 새 이미지 간 **SSIM ≥ 0.85** 이면 “사실상 동일”로 판단  
  - 동일 판단 시: **기존 ScreenshotImage 재사용**, `visitCnt++`, `status=PENDING` 재분석 예약, **기존 imageId로 원본 덮어쓰기**  
  - 불일치/후보 없음: **새 행 INSERT(PENDING)** + 캐시 추가

- **튜닝 포인트(운영 값)**  
  - dHash: `WIDTH=17`, `HEIGHT=16`, `threshold=5`  
  - 해밍 임계치: `6`  
  - 비트 유사도: `0.97`  
  - SSIM 임계치: `0.85`  

---

## 3. Redis 캐시 구성 & 키 설계

### Redis 템플릿
- `RedisTemplate<String, String>`: 리스트/문자열 키(최근 해시 목록 등)
- `RedisTemplate<String, byte[]>` (`@Bean(name="redisBytesTemplate")`): **바이너리 바이트** 저장(원본/썸네일) — `StringRedisSerializer` + `RedisSerializer.byteArray()` 사용

### TTL & 보관 개수
- 썸네일: **1h**, 원본: **1h**, 최근 해시 리스트: **12h**
- 최근 해시 리스트 최대 길이: **50**
- 캐시 스캐닝 유사도 기준: **similarity ≥ 0.97** (해밍 상한은 호출부 인자, 예: `maxDistance=6`)

### 키 네이밍
- 썸네일: `user:{userId}:thumb:{hash}`
- 원본: `user:{userId}:img:{imageId}`
- 최근 해시 리스트: `user:{userId}:recentImageHashes`  *(요소 형식: `imageId:hash`)*

---

## 캐시 동작 상세

### 1) 최근 해시 목록 + 썸네일 캐시
- `cacheRecentImageHash(userId, imageId, hash, pngThumbBytes)`
  1. **중복 방지**: 리스트에서 동일 해시(`...:hash`) 제거
  2. **LPUSH**로 맨 앞 추가: `imageId:hash`
  3. **리스트 TTL 갱신**: `EXPIRE(listKey, TTL_RECENT)`
  4. 썸네일 바이트 저장(**TTL 적용**)
  5. 길이 초과 시 **RPOP** → POP된 항목의 **썸네일 삭제**

### 2) 원본 이미지 캐시
- `cacheOriginalImage(userId, imageId, originalBytes)`
- 재분석 예약 시 **기존 imageId에 최신 원본 덮어쓰기** (워커 입력 소스 일원화)

### 3) 캐시 기반 유사 후보 탐색
- `findMostSimilarFromCache(userId, newHash, maxDistance)`
  1. `recentImageHashes` 순회
  2. **해밍거리 ≤ maxDistance** AND **similarity ≥ 0.97**
  3. 가장 높은 similarity 후보를 `Candidate(imageId, hash)`로 반환

> `similarityCheckService`  
> - `hammingDistance(long, long)` : 비트 XOR 카운트  
> - `similarity(long h1, long h2)` : `1 - (Hamming / bitLength)`

---

## 샘플링 파이프라인과의 연결

- **AdaptiveSamplingService**에서 새 이미지 dHash 계산 후
  1) `findMostSimilarFromCache(...)`로 **근접 후보** 조회  
  2) 후보가 있으면 **썸네일 복구 → SSIM 재검증**  
  3) SSIM `≥ 0.85`면 **기존 ScreenshotImage 재사용 & 재분석 예약**
     - DB: `visitCnt++`, `lastVisitedAt`, `analysisStatus = PENDING`
     - Redis: `cacheOriginalImage(userId, prevImageId, 최신 원본)` (**기존 imageId 덮어쓰기**)
  4) 그 외에는 **새 행 INSERT(PENDING)** + **썸네일/원본 캐시**

### Redis 상호작용 다이어그램(요약)

~~~mermaid
flowchart LR
  A[upload-screenshot] --> B[compute dHash]
  B --> C{recentImageHashes 탐색}
  C -->|후보 있음| D[썸네일 복구]
  D --> E[SSIM 재검증]
  E -->|0.85 이상| F[DB 기존행 PENDING <br>전환 및 visitCnt 증가</br>]
  F --> G[Redis cacheOriginalImage <br>기존 imageId 원본 덮어쓰기</br>]
  E -->|0.85 미만| H[DB 새 행 INSERT - PENDING]
  C -->|후보 없음| H
  H --> I[Redis cacheRecentImageHash + cacheOriginalImage]
~~~

---

## 4. SSE 기반 실시간 전송

### 전체 흐름
1. **AI 서버(FastAPI)**가 분석 결과를 `suggestions`, `suggestion_items`에 INSERT  
2. Postgres 트리거가 `pg_notify('suggestions_channel', payload)` 발행  
3. **PgSuggestionsListener**가 수신 → `SuggestionRepository`로 payload + items 조회  
4. **SuggestionSseHub**가 해당 userId 구독자들에게 SSE publish  
5. 프론트는 `/api/suggestions/stream` 구독 중 실시간 이벤트 수신 → UI 표시

~~~mermaid
flowchart LR
  AI[FastAPI AI 서버] -->|INSERT| DB[(Postgres)]
  DB -->|NOTIFY suggestions_channel| Listener[PgSuggestionsListener]
  Listener --> Repo[SuggestionRepository]
  Repo --> Hub[SuggestionSseHub]
  Hub --> FE[Frontend SSE /api/suggestions/stream]
~~~

### SSE 컴포넌트 요약

- **SuggestionSseHub**
  - `subscribe(userId)`: 무제한 타임아웃 Emitter 등록, `heartbeat` 전송
  - `publish(userId, payload, eventId)`: 해당 userId의 모든 Emitter로 `suggestions` 이벤트 push

- **PgSuggestionsListener**
  - 전용 커넥션으로 `LISTEN suggestions_channel`
  - 알림 수신 → `SuggestionRepository`로 payload + items 조회 → Hub.publish

- **SuggestionsController**
  - `GET /api/suggestions/latest` : 최신 SuggestionPayload 1건 반환
  - `GET /api/suggestions/stream?token=<JWT>` : 토큰에서 userId 추출 후 SSE 구독 시작

---

## API 명세

### 스크린샷 업로드 & 샘플링
- **URL**: `POST /upload-screenshot`
- **Auth**: `Authorization: Bearer <JWT>`
- **Consumes**: `multipart/form-data`

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `image` | file | ✅ | 업로드할 스크린샷 |
| `userId` | string | ✅ | 사용자 ID(Users.userId) |

- **성공 응답 (신규 저장)**
~~~json
{ "success": true, "currentImageId": 123 }
~~~

- **성공 응답 (유사 → 재분석 예약)**
~~~json
{
  "success": true,
  "similarity": 0.93,
  "prevImageId": 45,
  "message": "저장된 적 있음! AI 재분석 요청: visitCnt=10"
}
~~~

- **오류 예시**
~~~json
{ "error": "에러 발생: <상세 메시지>" }
~~~

### SSE API 명세

#### 1) 최신 질문/답변 조회
- **URL**: `GET /api/suggestions/latest`
- **Auth**: JWT (Spring Security Authentication)
- **Response 예시**
~~~json
{
  "id": 101,
  "userId": "42",
  "createdAt": "2025-08-21T10:20:30Z",
  "suggestions": [
    { "id": "uuid-1", "suggestion_id": "101", "question": "이 화면에서 할 수 있는 작업은?", "answer": "파일 업로드", "confidence": 0.92 },
    { "id": "uuid-2", "suggestion_id": "101", "question": "단축키는?", "answer": "Ctrl+U", "confidence": 0.85 }
  ]
}
~~~

#### 2) 실시간 스트리밍
- **URL**: `GET /api/suggestions/stream?token=<JWT>`
- **Produces**: `text/event-stream`
- **Events**
  - `heartbeat` (연결 확인, 3s)
  - `suggestions` (`SuggestionPayload` 전체)

---

<details>
<summary>Appendix – Sampling Details</summary>

### dHash(차분 해시) 절차(요약)
1) 입력 이미지를 그레이스케일로 변환 후 `WIDTH×HEIGHT`(`17×16`)로 리사이즈  
2) 각 행에서 인접한 픽셀 쌍 `(left, right)` 비교  
3) `left - right > threshold(=5)`면 비트 `1`, 아니면 `0`  
4) (행 기준) 비트를 누적하여 64비트(이상) 정수 해시를 구성  
5) 두 해시의 **해밍거리**로 근접 후보를 빠르게 선별

### 비트 기반 유사도
- `similarity(h1, h2) = 1 - ( Hamming(h1, h2) / bitLength )`  
- 본 프로젝트에서는 **`bitLength = (WIDTH - 1) * HEIGHT`**, 임계 **`similarity ≥ 0.97`** 사용

### SSIM(Structural Similarity) 사용 맥락
- 구조(밝기/대비/공분산)를 비교하여 **0~1** 범위의 유사도를 반환  
- 본 프로젝트에서는 `256×256` 그레이스케일로 정규화 후 SSIM 계산  
- **임계값 `0.85`** 이상이면 동일 화면으로 간주 → 기존 레코드 재사용

</details>
