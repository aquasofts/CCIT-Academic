$ErrorActionPreference = 'Stop'

$bundledNode = 'C:\Users\mosior\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
$node = Get-Command node -ErrorAction SilentlyContinue

if ($node) {
    & $node.Source (Join-Path $PSScriptRoot 'server.mjs')
} elseif (Test-Path -LiteralPath $bundledNode) {
    & $bundledNode (Join-Path $PSScriptRoot 'server.mjs')
} else {
    throw '未找到 Node.js。请先安装 Node.js 20 或更高版本。'
}
