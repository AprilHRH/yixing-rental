param(
    [ValidateSet('Dev', 'Run', 'Build', 'BackendTest', 'FrontendTest', 'FrontendBuild', 'Check')]
    [string]$Action = 'Dev'
)

$ErrorActionPreference = 'Stop'
# 为当前 VS Code 任务设置项目 JDK 和 Maven，不修改系统全局环境变量。
$root = Split-Path -Parent $PSScriptRoot
$jdk = Get-ChildItem -LiteralPath (Join-Path $root '.tools') -Directory |
    Where-Object { $_.Name -like 'jdk*' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe')) } |
    Sort-Object Name |
    Select-Object -First 1
if (-not $jdk) {
    throw 'Project JDK not found under .tools. Java 21 is required.'
}
$env:JAVA_HOME = $jdk.FullName
$mavenBin = Join-Path $root '.tools\apache-maven-3.9.9\bin'
$env:PATH = "$($jdk.FullName)\bin;$mavenBin;$env:PATH"
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
Push-Location -LiteralPath $root
try {
    # 所有命令从项目根目录运行，结束后还原终端原来的工作目录。
    switch ($Action) {
        'Dev' { & python scripts/run.py --dev --open }
        'Run' { & python scripts/run.py --open }
        'Build' { & python scripts/run.py --build --dev --open }
        'BackendTest' { & (Join-Path $mavenBin 'mvn.cmd') -f backend/pom.xml test }
        'FrontendTest' { & npm.cmd --prefix frontend test }
        'FrontendBuild' { & npm.cmd --prefix frontend run build }
        'Check' { & python scripts/run.py --check }
    }
    $result = $LASTEXITCODE
}
finally {
    Pop-Location
}
exit $result
