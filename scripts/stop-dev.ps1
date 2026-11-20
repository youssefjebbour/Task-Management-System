$ErrorActionPreference='Stop'
$TaskFlowRoot=Split-Path $PSScriptRoot -Parent
foreach($Service in @('api','web')){
    $PidFile=Join-Path $TaskFlowRoot ".runtime\$Service.pid"
    if(!(Test-Path -LiteralPath $PidFile)){continue}
    $ServiceProcessId=[int](Get-Content -LiteralPath $PidFile)
    $Process=Get-CimInstance Win32_Process -Filter "ProcessId = $ServiceProcessId" -ErrorAction SilentlyContinue
    if($Process -and ($Process.CommandLine -like '*taskflow-api*' -or $Process.CommandLine -like "*$TaskFlowRoot*frontend*vite*")){
        Stop-Process -Id $ServiceProcessId
        Write-Output "Stopped TaskFlow $Service process."
    }
}
Write-Output 'Database, mail and Docker volumes remain intact.'
