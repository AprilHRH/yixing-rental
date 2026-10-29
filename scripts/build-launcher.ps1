$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
if (-not (Test-Path -LiteralPath $compiler)) {
    $compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework/v4.0.30319/csc.exe'
}
if (-not (Test-Path -LiteralPath $compiler)) { throw 'Windows .NET Framework C# compiler is required.' }
$output = Join-Path $root '易行租车启动器.exe'
& $compiler /nologo /target:winexe /platform:anycpu /optimize+ /codepage:65001 /r:System.Windows.Forms.dll /r:System.Drawing.dll "/out:$output" (Join-Path $PSScriptRoot 'Launcher.cs')
if ($LASTEXITCODE -ne 0) { throw 'Launcher build failed.' }
Write-Output "Built: $output"
