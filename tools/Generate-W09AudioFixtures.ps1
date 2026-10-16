# Reproducible synthetic audio fixtures. No music files or server configuration are modified.
# Requires the existing Navidrome container's FFmpeg (6.1.1/libmp3lame on the test host).
param([string]$Container = 'navidrome', [string]$OutputDirectory = 'local')
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) { throw 'Output parent missing' }
$temporary = '/tmp/walktape-w09-' + [guid]::NewGuid().ToString('N')
docker exec $Container test -d /tmp
if ($LASTEXITCODE -ne 0) { throw 'Container /tmp unavailable' }
docker exec $Container mkdir $temporary
if ($LASTEXITCODE -ne 0) { throw 'Could not create fixture directory' }
try {
    foreach ($codec in @('flac', 'mp3')) {
        $encoder = if ($codec -eq 'mp3') { 'libmp3lame' } else { 'flac' }
        docker exec $Container ffmpeg -hide_banner -loglevel error -f lavfi -i 'sine=frequency=440:sample_rate=44100:duration=2' -c:a $encoder -metadata 'REPLAYGAIN_TRACK_GAIN=-6.0206 dB' -metadata 'REPLAYGAIN_ALBUM_GAIN=-3.0103 dB' -metadata 'REPLAYGAIN_TRACK_PEAK=0.5' "$temporary/tone.$codec"
        if ($LASTEXITCODE -ne 0) { throw 'Fixture encoding failed' }
        docker cp "${Container}:$temporary/tone.$codec" "$OutputDirectory/w09-tone.$codec"
        if ($LASTEXITCODE -ne 0) { throw 'Fixture copy failed' }
    }
} finally {
    docker exec $Container rm -r $temporary
}
