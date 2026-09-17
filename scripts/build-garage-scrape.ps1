$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$serviceDir = Join-Path $repoRoot "Services\garage-scrape"
$buildDir = Join-Path $repoRoot "build\garage-scrape"
$zipPath = Join-Path $buildDir "function.zip"
$bootstrapPath = Join-Path $buildDir "bootstrap"

if (Test-Path $buildDir) {
    $resolvedBuildDir = Resolve-Path $buildDir
    $resolvedRepoRoot = Resolve-Path $repoRoot

    if (-not $resolvedBuildDir.Path.StartsWith($resolvedRepoRoot.Path)) {
        throw "Refusing to clean build directory outside repo: $resolvedBuildDir"
    }

    Remove-Item -LiteralPath $buildDir -Recurse -Force
}

New-Item -ItemType Directory -Path $buildDir | Out-Null

docker build `
    --platform linux/amd64 `
    --output "type=local,dest=$buildDir" `
    $serviceDir

if ($LASTEXITCODE -ne 0) {
    throw "docker build failed with exit code $LASTEXITCODE"
}

if (-not (Test-Path $bootstrapPath)) {
    throw "Docker build completed, but bootstrap was not exported to $bootstrapPath"
}

Compress-Archive -Path $bootstrapPath -DestinationPath $zipPath -Force

Write-Host "Built Lambda package: $zipPath"
