$ErrorActionPreference = 'Stop'

$bundledPython = 'C:\Users\mosior\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$python = Get-Command python -ErrorAction SilentlyContinue

if (Test-Path -LiteralPath $bundledPython) {
    & $bundledPython -B (Join-Path $PSScriptRoot 'webvpn_probe.py')
} elseif ($python) {
    & $python.Source -B (Join-Path $PSScriptRoot 'webvpn_probe.py')
} else {
    throw 'Python 3 was not found.'
}