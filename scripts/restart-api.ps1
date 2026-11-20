$ErrorActionPreference='Stop'
$TaskFlowRoot=Split-Path $PSScriptRoot -Parent
. "$PSScriptRoot\env.ps1"
$PidFile=Join-Path $TaskFlowRoot '.runtime\api.pid'
if(Test-Path -LiteralPath $PidFile){
 $ApiProcessId=[int](Get-Content -LiteralPath $PidFile)
 $Process=Get-CimInstance Win32_Process -Filter "ProcessId = $ApiProcessId" -ErrorAction SilentlyContinue
 if($Process -and $Process.CommandLine -like '*taskflow-api*'){Stop-Process -Id $ApiProcessId}
}
New-Item -ItemType Directory -Force -Path "$TaskFlowRoot\.runtime" | Out-Null
Copy-Item -LiteralPath "$TaskFlowRoot\backend\target\taskflow-api-1.0.0-SNAPSHOT.jar" -Destination "$TaskFlowRoot\.runtime\taskflow-api.jar" -Force
$Api=Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList '-jar',"$TaskFlowRoot\.runtime\taskflow-api.jar" -WorkingDirectory "$TaskFlowRoot\backend" -WindowStyle Hidden -RedirectStandardOutput "$TaskFlowRoot\.runtime\api.stdout.log" -RedirectStandardError "$TaskFlowRoot\.runtime\api.stderr.log" -PassThru
$Api.Id | Set-Content -LiteralPath $PidFile
Write-Output "TaskFlow API process: $($Api.Id)"
