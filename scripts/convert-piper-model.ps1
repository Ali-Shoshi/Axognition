# Piper and sherpa-onnx use the same ONNX graph. Add only the metadata described
# at https://k2-fsa.github.io/sherpa/onnx/tts/piper.html and derive phoneme IDs.
# ModelProto.metadata_props is field 14 of the official ONNX protobuf schema.
# This avoids needing Python, PyTorch or ONNX tooling just to add metadata.
function Read-ProtoVarint([IO.Stream]$stream) {
    [long]$value = 0
    for ($shift = 0; $shift -lt 64; $shift += 7) {
        $next = $stream.ReadByte()
        if ($next -lt 0) { throw 'Unexpected end of ONNX protobuf.' }
        $value = $value -bor ([long]($next -band 127) -shl $shift)
        if (($next -band 128) -eq 0) { return $value }
    }
    throw 'Invalid ONNX protobuf varint.'
}

function Write-ProtoVarint([IO.Stream]$stream, [long]$value) {
    while ($value -ge 128) { $stream.WriteByte([byte](($value -band 127) -bor 128)); $value = $value -shr 7 }
    $stream.WriteByte([byte]$value)
}

function Write-ProtoString([IO.Stream]$stream, [int]$field, [string]$value) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($value)
    Write-ProtoVarint $stream (($field -shl 3) -bor 2)
    Write-ProtoVarint $stream $bytes.Length
    $stream.Write($bytes, 0, $bytes.Length)
}

function Get-OnnxMetadata([string]$path) {
    $metadata = @{}
    $stream = [IO.File]::OpenRead($path)
    try {
        while ($stream.Position -lt $stream.Length) {
            $tag = Read-ProtoVarint $stream
            $field = $tag -shr 3
            switch ($tag -band 7) {
                0 { $null = Read-ProtoVarint $stream }
                1 { $null = $stream.Seek(8, [IO.SeekOrigin]::Current) }
                5 { $null = $stream.Seek(4, [IO.SeekOrigin]::Current) }
                2 {
                    $length = Read-ProtoVarint $stream
                    if ($field -eq 14) {
                        if ($length -gt 1048576) { throw 'Unexpectedly large ONNX metadata.' }
                        $reader = [IO.BinaryReader]::new($stream, [Text.Encoding]::UTF8, $true)
                        $entry = [IO.MemoryStream]::new($reader.ReadBytes([int]$length), $false)
                        $reader.Dispose()
                        try {
                            $key = ''; $value = ''
                            while ($entry.Position -lt $entry.Length) {
                                $entryTag = Read-ProtoVarint $entry
                                if (($entryTag -band 7) -ne 2) { throw 'Invalid ONNX metadata entry.' }
                                $entryLength = Read-ProtoVarint $entry
                                $bytes = [byte[]]::new([int]$entryLength)
                                if ($entry.Read($bytes, 0, $bytes.Length) -ne $bytes.Length) { throw 'Truncated ONNX metadata.' }
                                if (($entryTag -shr 3) -eq 1) { $key = [Text.Encoding]::UTF8.GetString($bytes) }
                                if (($entryTag -shr 3) -eq 2) { $value = [Text.Encoding]::UTF8.GetString($bytes) }
                            }
                            $metadata[$key] = $value
                        } finally { $entry.Dispose() }
                    } else { $null = $stream.Seek($length, [IO.SeekOrigin]::Current) }
                }
                default { throw 'Unsupported ONNX protobuf wire type.' }
            }
            if ($stream.Position -gt $stream.Length) { throw 'Truncated ONNX protobuf.' }
        }
    } finally { $stream.Dispose() }
    return $metadata
}

function Convert-PiperModel([string]$source, [string]$configuration, [string]$destination) {
    $json = Get-Content -LiteralPath $configuration -Raw -Encoding UTF8
    # IPA token maps can contain both x and X: preserve case-sensitive keys.
    if ($PSVersionTable.PSVersion.Major -ge 6) {
        $config = $json | ConvertFrom-Json -AsHashtable
    } else {
        Add-Type -AssemblyName System.Web.Extensions
        $config = ([Web.Script.Serialization.JavaScriptSerializer]::new()).DeserializeObject($json)
    }
    if ($config.phoneme_type -ne 'espeak' -or $config.num_speakers -ne 1) {
        throw 'This conversion supports single-speaker eSpeak Piper models only.'
    }
    $expected = [ordered]@{
        model_type = 'vits'; comment = 'piper'
        language = $(if ($config.language.name_english) { $config.language.name_english } else { $config.espeak.voice })
        voice = $config.espeak.voice; has_espeak = '1'
        n_speakers = [string]$config.num_speakers; sample_rate = [string]$config.audio.sample_rate
    }
    $existing = Get-OnnxMetadata $source
    $model = Join-Path $destination 'model.onnx'
    Copy-Item -LiteralPath $source -Destination $model -Force
    $output = [IO.File]::Open($model, [IO.FileMode]::Append, [IO.FileAccess]::Write)
    try {
        foreach ($item in $expected.GetEnumerator()) {
            if ($existing.ContainsKey($item.Key)) {
                if ($existing[$item.Key] -ne $item.Value) { throw "Conflicting model metadata: $($item.Key)." }
                continue
            }
            $entry = [IO.MemoryStream]::new()
            try {
                Write-ProtoString $entry 1 $item.Key
                Write-ProtoString $entry 2 $item.Value
                Write-ProtoVarint $output 114
                Write-ProtoVarint $output $entry.Length
                $entry.Position = 0; $entry.CopyTo($output)
            } finally { $entry.Dispose() }
        }
    } finally { $output.Dispose() }
    $tokens = $config.phoneme_id_map.GetEnumerator() | ForEach-Object { "$($_.Key) $($_.Value[0])" }
    # Sherpa's space-token parser requires LF; CRLF makes it read the space ID
    # as a literal digit token and can terminate the native process.
    [IO.File]::WriteAllText((Join-Path $destination 'tokens.txt'),
        (($tokens -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
    $converted = Get-OnnxMetadata $model
    foreach ($item in $expected.GetEnumerator()) {
        if ($converted[$item.Key] -ne $item.Value) { throw "Converted model metadata failed validation: $($item.Key)." }
    }
}
