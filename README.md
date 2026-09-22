# WebCraft 멀티플레이 서버 API 문서

Spring Boot 기반 멀티플레이 월드 서버(`game-expert`)의 REST API와 WebSocket 프로토콜 명세입니다.

- Base URL : `http://localhost:8080` (docker-compose 사용 시 `http://localhost:8081`, `http://localhost:8082`)
- WebSocket : `ws://localhost:8080/ws/worlds/{worldId}?nickname={nickname}`
- 날짜 형식 : ISO-8601 `LocalDateTime` (예: `2026-09-11T19:45:55.302165`)

### 공통 에러 응답

모든 REST 에러는 `GlobalExceptionHandler`를 통해 아래 형태로 반환됩니다.

```json
{
  "error": "WORLD_NOT_FOUND"
}
```

| 이름  | 응답 타입 | 설명                    |
|-------|-----------|-------------------------|
| error | String    | 에러 식별 코드 문자열   |

| 상태 코드 | 코드                          | 설명                                            |
|-----------|-------------------------------|-------------------------------------------------|
| 400       | VALIDATION_FAILED             | 요청 값 검증 실패 / 타입 불일치                 |
| 400       | INVALID_REQUEST_BODY          | JSON 본문을 읽을 수 없음                        |
| 404       | NOT_FOUND                     | 매핑된 핸들러가 없음                            |
| 500       | INTERNAL_ERROR                | 처리되지 않은 서버 오류                         |
| 503       | WORLD_BASELINE_INITIALIZING   | 월드 베이스라인 초기화 중이라 요청을 받을 수 없음 |

---

## 1. 플레이어 등록

닉네임으로 플레이어를 등록합니다. WebSocket 접속과 월드 소유권 확인에 사용되는 식별자입니다.

### Request

**POST** `/players`

```json
{
  "nickname": "user"
}
```

| 이름     | 요청 타입 | 설명                                        |
|----------|-----------|---------------------------------------------|
| nickname | String    | 2~12자의 영문 대소문자, 숫자, 밑줄(`_`)만 허용 |

### Response `201 Created`

```json
{
  "id": 1,
  "nickname": "user",
  "createdAt": "2026-09-11T19:18:24.638352"
}
```

| 이름      | 응답 타입     | 설명                |
|-----------|---------------|---------------------|
| id        | Long          | 플레이어 식별 키    |
| nickname  | String        | 플레이어 닉네임     |
| createdAt | LocalDateTime | 등록 시각           |

### 에러 케이스

| 상태 코드 | 코드               | 설명                                      |
|-----------|--------------------|-------------------------------------------|
| 400       | VALIDATION_FAILED  | 닉네임이 비어 있거나 허용 패턴에 맞지 않음 |
| 409       | DUPLICATE_NICKNAME | 이미 등록된 닉네임                        |

---

## 2. 월드 목록 조회

접속 가능한 루트 월드 목록을 조회합니다. `onlineCount`는 Redis에 저장된 접속 정보(90초 TTL)를 기준으로 집계합니다.

### Request

**GET** `/worlds`

### Response `200 OK`

```json
[
  {
    "id": 1,
    "name": "즐거운 월드",
    "seed": 123456789,
    "onlineCount": 3,
    "difficulty": "normal"
  }
]
```

| 이름        | 응답 타입 | 설명                              |
|-------------|-----------|-----------------------------------|
| id          | Long      | 월드 식별 키                      |
| name        | String    | 월드 이름                         |
| seed        | long      | 월드 생성 시드 (signed int32 범위) |
| onlineCount | long      | 현재 접속자 수                    |
| difficulty  | String    | 난이도 (`easy`, `normal`, `hard`) |

### 에러 케이스

| 상태 코드 | 코드                        | 설명                     |
|-----------|-----------------------------|--------------------------|
| 503       | WORLD_BASELINE_INITIALIZING | 월드 베이스라인 준비 전  |

---

## 3. 월드 생성

새 월드를 생성합니다. 루트 월드는 **최대 3개**까지만 존재할 수 있습니다.

### Request

**POST** `/worlds`

```json
{
  "name": "즐거운 월드",
  "difficulty": "normal",
  "nickname": "user",
  "debugSeed": 123456789
}
```

| 이름       | 요청 타입 | 필수 | 설명                                              |
|------------|-----------|------|---------------------------------------------------|
| name       | String    | O    | 1~30자의 월드 이름                                |
| difficulty | String    | X    | `easy`, `normal`, `hard` / 생략 시 기본 난이도    |
| nickname   | String    | X    | 2~12자의 소유자 닉네임 / 생략 시 소유자 없는 월드 |
| debugSeed  | Long      | X    | 재현용 고정 시드 (signed int32 범위)              |

### Response `201 Created`

```json
{
  "id": 1,
  "name": "즐거운 월드",
  "seed": 123456789,
  "difficulty": "normal",
  "ownerNickname": "user"
}
```

| 이름          | 응답 타입 | 설명                              |
|---------------|-----------|-----------------------------------|
| id            | Long      | 월드 식별 키                      |
| name          | String    | 월드 이름                         |
| seed          | long      | 확정된 월드 시드                  |
| difficulty    | String    | 난이도 (`easy`, `normal`, `hard`) |
| ownerNickname | String    | 소유자 닉네임 (없으면 `null`)     |

### 에러 케이스

| 상태 코드 | 코드                        | 설명                                     |
|-----------|-----------------------------|------------------------------------------|
| 400       | VALIDATION_FAILED           | 이름 길이 위반, 닉네임 길이 위반, 시드 범위 초과 |
| 404       | PLAYER_NOT_FOUND            | `nickname`에 해당하는 플레이어가 없음    |
| 409       | WORLD_LIMIT_REACHED         | 루트 월드가 이미 3개                     |
| 503       | WORLD_BASELINE_INITIALIZING | 월드 베이스라인 준비 전                  |

---

## 4. 월드 삭제

월드를 삭제합니다. 요청자 닉네임으로 삭제 권한을 확인합니다.

- 소유자가 지정된 월드 : 소유자 본인만 삭제 가능
- 소유자가 없는 월드 : 참여자가 없으면 누구나, 참여자가 있으면 **참여 이력이 있는 플레이어만** 삭제 가능

### Request

**DELETE** `/worlds/{id}?nickname={nickname}`

| 이름     | 위치        | 설명                    |
|----------|-------------|-------------------------|
| id       | Path        | 삭제할 월드 식별 키     |
| nickname | Query       | 요청자 닉네임           |

### Response `204 No Content`

월드가 정상적으로 삭제되었습니다. 본문은 없습니다.

### 에러 케이스

| 상태 코드 | 코드                        | 설명                                                |
|-----------|-----------------------------|-----------------------------------------------------|
| 403       | NOT_WORLD_OWNER             | 닉네임 누락, 미등록 플레이어, 삭제 권한 없음        |
| 404       | WORLD_NOT_FOUND             | 해당 월드가 없거나 차원(하위) 월드임                |
| 503       | WORLD_BASELINE_INITIALIZING | 월드 베이스라인 준비 전                             |

---

## 5. 조건부 월드 삭제

삭제 시점의 월드 정보가 요청 본문과 **정확히 일치할 때만** 삭제합니다. 조회 이후 월드가 교체되는 경합 상황을 막기 위한 API로, 대상 행을 비관적 락(`findByIdForUpdate`)으로 잠근 뒤 비교합니다.

### Request

**DELETE** `/worlds/{id}/if-matches?nickname={nickname}`

```json
{
  "name": "즐거운 월드",
  "seed": 123456789,
  "difficulty": "normal",
  "ownerNickname": "user"
}
```

| 이름          | 요청 타입 | 설명                                        |
|---------------|-----------|---------------------------------------------|
| name          | String    | 최대 30자, 월드 이름과 일치해야 함          |
| seed          | Long      | signed int32 범위, 월드 시드와 일치해야 함  |
| difficulty    | String    | `easy`, `normal`, `hard` 중 하나            |
| ownerNickname | String    | 2~12자 영문/숫자/밑줄, 소유자와 일치해야 함 |

### Response `204 No Content`

조건이 모두 일치하여 삭제되었습니다.

### Response 유형 `409 Conflict`

```json
{
  "error": "WORLD_IDENTITY_MISMATCH"
}
```

### 에러 케이스

| 상태 코드 | 코드                        | 설명                                          |
|-----------|-----------------------------|-----------------------------------------------|
| 400       | VALIDATION_FAILED           | 본문 필드 검증 실패                           |
| 403       | NOT_WORLD_OWNER             | 삭제 권한 없음                                |
| 404       | WORLD_NOT_FOUND             | 해당 월드 없음                                |
| 409       | WORLD_IDENTITY_MISMATCH     | 이름·시드·난이도·소유자 중 하나라도 불일치    |
| 503       | WORLD_BASELINE_INITIALIZING | 월드 베이스라인 준비 전                       |

---

## 6. 최근 채팅 조회

월드의 최근 채팅을 **오래된 순서**로 조회합니다. 동일 `worldId`·`limit` 조합은 Redis에 5초간 캐시되며, 새 채팅이 저장되면(커밋 이후) 캐시가 무효화됩니다.

### Request

**GET** `/worlds/{worldId}/chats?limit=50`

| 이름    | 위치  | 기본값 | 설명                                        |
|---------|-------|--------|---------------------------------------------|
| worldId | Path  | -      | 월드 식별 키                                |
| limit   | Query | 50     | 조회 개수 / 1 미만·100 초과 값은 범위로 보정 |

### Response `200 OK`

```json
[
  {
    "sender": "user",
    "content": "안녕하세요",
    "createdAt": "2026-09-11T19:45:55.302165"
  }
]
```

| 이름      | 응답 타입     | 설명            |
|-----------|---------------|-----------------|
| sender    | String        | 보낸 사람 닉네임 |
| content   | String        | 채팅 내용       |
| createdAt | LocalDateTime | 저장 시각       |

### 에러 케이스

| 상태 코드 | 코드            | 설명                   |
|-----------|-----------------|------------------------|
| 404       | WORLD_NOT_FOUND | 해당 월드가 존재하지 않음 |

---

## 7. 채팅 히스토리 커서 조회

`(createdAt, id)` 커서로 과거 채팅을 거슬러 올라가며 조회합니다. 커서 두 값은 **함께 보내거나 함께 생략**해야 합니다.

### Request

**GET** `/worlds/{worldId}/chats/history?beforeCreatedAt=2026-09-11T19:45:55.302165&beforeId=120&limit=20`

| 이름            | 위치  | 기본값 | 설명                                        |
|-----------------|-------|--------|---------------------------------------------|
| worldId         | Path  | -      | 월드 식별 키                                |
| beforeCreatedAt | Query | null   | 커서 기준 시각 (ISO-8601)                   |
| beforeId        | Query | null   | 커서 기준 메시지 ID                         |
| limit           | Query | 20     | 1~100 사이의 정수                           |

### Response `200 OK`

```json
{
  "items": [
    {
      "id": 119,
      "sender": "user",
      "content": "안녕하세요",
      "createdAt": "2026-09-11T19:45:55.302165"
    }
  ],
  "hasNext": true,
  "nextCreatedAt": "2026-09-11T19:45:55.302165",
  "nextId": 119
}
```

| 이름          | 응답 타입            | 설명                                         |
|---------------|----------------------|----------------------------------------------|
| items         | ChatHistoryEntry[]   | 조회된 채팅 목록                             |
| hasNext       | boolean              | 다음 페이지 존재 여부                        |
| nextCreatedAt | LocalDateTime        | 다음 요청에 쓸 커서 시각 (없으면 `null`)     |
| nextId        | Long                 | 다음 요청에 쓸 커서 ID (없으면 `null`)       |

**ChatHistoryEntry**

| 이름      | 응답 타입     | 설명             |
|-----------|---------------|------------------|
| id        | Long          | 채팅 식별 키     |
| sender    | String        | 보낸 사람 닉네임 |
| content   | String        | 채팅 내용        |
| createdAt | LocalDateTime | 저장 시각        |

### 에러 케이스

| 상태 코드 | 코드              | 설명                                                    |
|-----------|-------------------|---------------------------------------------------------|
| 400       | VALIDATION_FAILED | 커서 두 값 중 하나만 전달, 또는 `limit`이 1~100 범위 밖  |
| 404       | WORLD_NOT_FOUND   | 해당 월드가 존재하지 않음                               |

---

## 8. 채팅 저장 롤백 (검증 전용)

`assignment-checks` 프로파일에서만 노출되는 과제 검증용 엔드포인트입니다. 채팅을 저장하고 이벤트를 발행한 뒤 트랜잭션을 의도적으로 롤백하여, 커밋되지 않은 저장이 캐시·브로드캐스트에 새어나가지 않는지 확인합니다.

### Request

**POST** `/practice/worlds/{worldId}/chats/rollback?nickname={nickname}&content={content}`

| 이름     | 위치  | 설명                                  |
|----------|-------|---------------------------------------|
| worldId  | Path  | 월드 식별 키                          |
| nickname | Query | 2~12자 영문/숫자/밑줄                 |
| content  | Query | 최대 200자, 공백만으로 이루어질 수 없음 |

### Response `204 No Content`

저장은 롤백되며 본문은 없습니다.

---

# WebSocket 프로토콜

## 연결

```
ws://{host}/ws/worlds/{worldId}?nickname={nickname}
```

핸드셰이크 시 `NicknameHandshakeInterceptor`가 닉네임으로 플레이어를, 경로 변수로 월드를 조회해 세션 속성에 저장합니다. 연결이 성립되면 접속 정보가 Redis(`world:{worldId}:presence`, ZSet)에 등록되고 `ping` 메시지로 갱신됩니다.

| 상황                                              | 처리                                   |
|---------------------------------------------------|----------------------------------------|
| 월드 베이스라인 초기화 중                          | 핸드셰이크 거부 `503 Service Unavailable` |
| 닉네임 누락 / 등록되지 않은 플레이어 / 연결 정보 누락 | Close `4000`                           |
| 월드 없음 / 잘못된 경로 / 차원(하위) 월드          | Close `4001`                           |
| 같은 월드에 동일 닉네임이 이미 접속 중              | Close `4002`                           |
| 접속 처리 중 서버 예외                             | Close `1011 (SERVER_ERROR)`            |

## 클라이언트 → 서버

모든 메시지는 `type` 필드를 가진 JSON 오브젝트입니다.

### `ping` — 연결 유지

```json
{ "type": "ping" }
```

접속 정보의 만료 시각을 갱신(heartbeat)하고 `pong`으로 응답합니다. 접속 정보 TTL은 90초이므로 그보다 짧은 주기로 전송해야 합니다.

### `chat` — 채팅 전송

```json
{
  "type": "chat",
  "content": "안녕하세요"
}
```

| 이름    | 요청 타입 | 설명                                |
|---------|-----------|-------------------------------------|
| content | String    | 1~200자 / 공백만 입력할 수 없음      |

- 전송자는 **핸드셰이크에서 확정된 세션의 닉네임**을 사용합니다. 메시지에 담긴 `nickname`, `worldId`는 무시됩니다.
- 플레이어당 **10초에 5회**로 제한됩니다(Redis Lua 스크립트, 키 `chat:limit:{playerId}`).
- 저장 후 같은 월드의 모든 세션에 브로드캐스트됩니다. `webcraft.chat.pubsub-enabled=true`이면 Redis Pub/Sub 채널 `webcraft:chat`을 거쳐 다중 서버 인스턴스에 전파됩니다.

### `move` — 이동

```json
{
  "type": "move",
  "x": 0.0,
  "y": 0.0,
  "z": 1.0,
  "yaw": 90.0,
  "pitch": 0.0,
  "crouching": false,
  "gliding": false
}
```

| 이름                | 요청 타입 | 필수 | 설명                              |
|---------------------|-----------|------|-----------------------------------|
| x, y, z             | double    | O    | 이동 방향 성분 (유한한 수)        |
| yaw, pitch          | float     | O    | 시선 각도 (유한한 수)             |
| crouching           | boolean   | O    | 웅크리기 여부                     |
| gliding             | boolean   | O    | 활강 여부                         |
| finalSceneActionId  | String    | X    | 최종 연출 액션 식별자             |

이동 요청은 엔진의 액션 큐에 적재됩니다. 큐가 가득 차면 `QUEUE_FULL` 에러가 반환됩니다.

### `onlineUsers` — 접속자 목록 조회

```json
{ "type": "onlineUsers" }
```

요청한 세션에만 응답합니다.

## 서버 → 클라이언트

### `pong`

```json
{ "type": "pong" }
```

### `chat`

```json
{
  "type": "chat",
  "sender": "user",
  "content": "안녕하세요",
  "timestamp": "2026-09-11T19:45:55.302165"
}
```

| 이름      | 응답 타입     | 설명                          |
|-----------|---------------|-------------------------------|
| type      | String        | 고정값 `chat`                 |
| sender    | String        | 저장된 보낸 사람 닉네임       |
| content   | String        | 저장된 채팅 내용              |
| timestamp | LocalDateTime | 저장 시각                     |

### `onlineUsers`

```json
{
  "type": "onlineUsers",
  "users": ["alice", "Bob"],
  "count": 2
}
```

| 이름  | 응답 타입 | 설명                                  |
|-------|-----------|---------------------------------------|
| type  | String    | 고정값 `onlineUsers`                  |
| users | String[]  | 현재 열려 있는 연결의 닉네임 (오름차순) |
| count | int       | 닉네임 개수                           |

### 에러

메시지 처리 실패 시 요청한 세션에만 엔진 제공 DTO(`WsMessages.Error`)가 전송되며, 아래 코드 중 하나를 담습니다.

| 코드           | 발생 상황                                         |
|----------------|---------------------------------------------------|
| INVALID_JSON   | JSON 파싱 실패                                    |
| INVALID_MESSAGE| 오브젝트가 아니거나 필드 값이 규격에 맞지 않음     |
| UNKNOWN_TYPE   | 지원하지 않는 `type`                              |
| QUEUE_FULL     | 엔진 액션 큐 상한 초과                            |
| CHAT_COOLDOWN  | 채팅 속도 제한(10초 5회) 초과                     |
| INTERNAL_ERROR | 처리 중 예기치 못한 서버 오류                     |

---

## 참고

- `difficulty`의 JSON 표기는 엔진의 `Difficulty` 타입이 결정합니다. 요청 검증 패턴(`easy|normal|hard`)과 `Difficulty.key()` 비교 로직을 기준으로 위와 같이 표기했습니다.
- 에러 메시지(`WsMessages.Error`)의 정확한 필드 이름은 `webcraft-engine` 라이브러리에 정의되어 있어 이 문서에서는 코드 목록만 명시했습니다.
