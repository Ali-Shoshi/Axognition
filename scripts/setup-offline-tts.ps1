# One-time build setup. All synthesis happens on Android, never on a server.
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$destination = Join-Path $workspace 'offline-tts'
$downloads = Join-Path $destination 'downloads'
$unpacked = Join-Path $destination 'unpacked'
$assets = Join-Path $destination 'assets/tts'
$manifest = Get-Content (Join-Path $PSScriptRoot 'offline-tts-artifacts.json') -Raw | ConvertFrom-Json
. (Join-Path $PSScriptRoot 'convert-piper-model.ps1')
New-Item -ItemType Directory -Force -Path $downloads, $unpacked, $assets | Out-Null

function Get-VerifiedArtifact($artifact) {
    $file = Join-Path $downloads $artifact.name
    if (!(Test-Path -LiteralPath $file) -or (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $artifact.sha256) {
        Write-Host "Downloading $($artifact.name)..."
        Invoke-WebRequest -Uri $artifact.url -OutFile "$file.partial"
        if ((Get-FileHash -LiteralPath "$file.partial" -Algorithm SHA256).Hash.ToLowerInvariant() -ne $artifact.sha256) {
            throw "Checksum mismatch for $($artifact.name). The downloaded file was not installed."
        }
        Move-Item -LiteralPath "$file.partial" -Destination $file -Force
    }
    return $file
}

$runtime = Get-VerifiedArtifact $manifest.runtime
Copy-Item -LiteralPath $runtime -Destination (Join-Path $destination $manifest.runtime.name) -Force
foreach ($voice in $manifest.voices) {
    if ($voice.role -notin @('lecture', 'assistant') -or $voice.language -notin @('en', 'sq')) { throw 'Invalid voice profile.' }
    $voiceAssets = Join-Path $assets "$($voice.role)/$($voice.language)"
    New-Item -ItemType Directory -Force -Path $voiceAssets | Out-Null
    $archive = Get-VerifiedArtifact $voice
    if ($voice.format -eq 'piper-onnx') {
        $configuration = Get-VerifiedArtifact $voice.config
        Convert-PiperModel $archive $configuration $voiceAssets
        continue
    }
    # Official, checksum-verified archives each contain one model directory.
    $entries = & tar -tf $archive
    if ($LASTEXITCODE -ne 0 -or @($entries | Where-Object { $_ -match '(^/|^[A-Za-z]:|(^|/)\.\.(/|$))' }).Count -gt 0) {
        throw "Invalid archive paths in $($voice.name)."
    }
    & tar -xf $archive -C $unpacked
    if ($LASTEXITCODE -ne 0) { throw "Could not extract $($voice.name)." }
    $modelDirectory = Join-Path $unpacked ($voice.name -replace '\.tar\.bz2$', '')
    $model = @(Get-ChildItem -LiteralPath $modelDirectory -Filter '*.onnx')
    if ($model.Count -ne 1) { throw "Expected exactly one ONNX model in $modelDirectory." }
    Copy-Item -LiteralPath $model[0].FullName -Destination (Join-Path $voiceAssets 'model.onnx') -Force
    Copy-Item -LiteralPath (Join-Path $modelDirectory 'tokens.txt') -Destination $voiceAssets -Force
    # Phonemizer data is shared by all four neural voices.
    if ($voice.role -eq 'lecture' -and $voice.language -eq 'en') {
        Copy-Item -LiteralPath (Join-Path $modelDirectory 'espeak-ng-data') -Destination $assets -Recurse -Force
    }
}
# Remove only the obsolete, generated two-voice layout after the four-voice
# layout is complete. Resolve and verify each target before recursive removal.
foreach ($language in @('en', 'sq')) {
    $obsolete = [IO.Path]::GetFullPath((Join-Path $assets $language))
    $assetRoot = [IO.Path]::GetFullPath($assets).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (!$obsolete.StartsWith($assetRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid obsolete voice path.' }
    if (Test-Path -LiteralPath $obsolete) { Remove-Item -LiteralPath $obsolete -Recurse -Force }
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'offline-tts-artifacts.json') -Destination (Join-Path $assets 'artifacts.json') -Force
Write-Output 'Offline lecture and assistant voices are ready in English and Albanian. Future builds can run offline.'
