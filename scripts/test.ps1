$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    & .\mvnw.cmd clean test @args
}
finally {
    Pop-Location
}
