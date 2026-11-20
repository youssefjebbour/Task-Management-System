$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
if (Test-Path -LiteralPath "$ProjectRoot\.env") {
    Get-Content -LiteralPath "$ProjectRoot\.env" | ForEach-Object {
        if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
    }
}
$LocalJdk = Get-ChildItem -LiteralPath "$ProjectRoot\.tools" -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($LocalJdk) { $env:JAVA_HOME = $LocalJdk.FullName; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
