$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    & .\mvnw.cmd -pl agent-server -am -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    $java = if ($env:JAVA_HOME) {
        Join-Path $env:JAVA_HOME 'bin\java.exe'
    } else {
        'java.exe'
    }
    & $java -jar agent-server\target\agent-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=local @args
}
finally {
    Pop-Location
}
