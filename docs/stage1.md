# Stage 1 Local Verification

## Environment

- JDK 17 or higher
- Maven is not required: the project includes Maven Wrapper
- Windows example: `$env:JAVA_HOME='F:\jdk21'`

## 1. Compile and run tests

```powershell
$env:JAVA_HOME='F:\jdk21'
.\mvnw.cmd clean test
```

Expected result:

- `agent-core` unit tests pass
- `agent-server` integration tests pass
- REST, async task and SSE paths are all covered

## 2. Start the server

```powershell
.\mvnw.cmd -pl agent-server -am spring-boot:run
```

Or run the packaged jar:

```powershell
.\mvnw.cmd -q -DskipTests package
java -jar agent-server\target\agent-server-1.0.0-SNAPSHOT.jar
```

Expected startup log:

```text
Started JavaAgentPlatformApplication in 3.5 seconds
```

## 3. Verify health and agents

```powershell
curl.exe http://localhost:8080/api/v1/health
curl.exe http://localhost:8080/api/v1/agents
```

Expected:

- health returns `status: UP`
- agents list contains `demo_agent` with status `ACTIVE`

## 4. Verify register / heartbeat / unregister

```powershell
curl.exe -X POST http://localhost:8080/api/v1/agents/register -H "Content-Type: application/json" -d "{\"agentId\":\"remote_1\",\"name\":\"Remote 1\",\"kind\":\"REMOTE\",\"executionEndpoint\":\"http://localhost:9999/execute\"}"
curl.exe -X POST http://localhost:8080/api/v1/agents/remote_1/heartbeat
curl.exe -X DELETE http://localhost:8080/api/v1/agents/remote_1
```

Expected:

- register returns `agent registered`
- heartbeat returns `ACTIVE`
- unregister returns `agent unregistered`

## 5. Verify async task and logs

```powershell
curl.exe -X POST http://localhost:8080/api/v1/tasks -H "Content-Type: application/json" -d "{\"agentId\":\"demo_agent\",\"input\":\"verify pipeline\",\"autoRun\":true}"
```

Use the returned `taskId`:

```powershell
curl.exe http://localhost:8080/api/v1/tasks/{taskId}
curl.exe http://localhost:8080/api/v1/tasks/{taskId}/logs
```

Expected:

- task reaches `SUCCEEDED` within a few seconds
- logs contain `Demo agent started` and `Task succeeded`

## 6. Verify SSE streaming

```powershell
curl.exe -N http://localhost:8080/api/v1/tasks/{taskId}/events
```

Expected events:

```text
event:connected
event:log
event:status
event:done
```

## 7. Stop the server

Press `Ctrl+C` in the terminal where the server is running.
