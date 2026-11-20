param([switch]$Seed)
$ErrorActionPreference='Stop'
$TaskFlowRoot=Split-Path $PSScriptRoot -Parent
$EnvPath=Join-Path $TaskFlowRoot '.env'
if(Test-Path -LiteralPath $EnvPath){Write-Output 'Existing .env preserved. Edit it to change configuration.';exit 0}
function New-Secret {
    $Bytes=New-Object byte[] 48
    $Random=[System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {$Random.GetBytes($Bytes)} finally {$Random.Dispose()}
    return [Convert]::ToBase64String($Bytes)
}
$Text=Get-Content -LiteralPath (Join-Path $TaskFlowRoot '.env.example') -Raw
$Text=$Text.Replace('replace-with-a-random-password',(New-Secret)).Replace('replace-with-base64-encoded-32-or-more-random-bytes',(New-Secret)).Replace('replace-with-a-development-only-password',(New-Secret))
if($Seed){$Text=$Text.Replace('SEED_ENABLED=false','SEED_ENABLED=true')}
[System.IO.File]::WriteAllText($EnvPath,$Text,[System.Text.UTF8Encoding]::new($false))
Write-Output 'Created .env with random local secrets. Values are not printed.'
Write-Output 'Run docker compose --profile full up -d --build from the project directory.'
