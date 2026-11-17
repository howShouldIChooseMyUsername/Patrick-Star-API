# Two REAL, separately loaded Fabric mod JARs. Their compiled classes live in
# src/test/java and must never be included in the distributed API JAR.
# Run AFTER: ..\fabric-amper.bat build
param(
    [switch] $NoInstall
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$classesDir = Join-Path $root 'build\classes\java\test\comvyne\patrickstar\consumers'
$buildRoot = Join-Path $root 'build\test-consumers'
$outputRoot = Join-Path $buildRoot 'jars'
$stageRoot = Join-Path $buildRoot 'staging'
$runMods = Join-Path $root 'run\mods'

if (-not (Test-Path -LiteralPath $classesDir -PathType Container)) {
    throw "First compile test sources with ..\fabric-amper.bat build: $classesDir"
}

[void](New-Item -ItemType Directory -Force -Path $outputRoot)
[void](New-Item -ItemType Directory -Force -Path $stageRoot)
if (-not $NoInstall) {
    [void](New-Item -ItemType Directory -Force -Path $runMods)
}

$mods = @(
    @{ id = 'patrick_probe_alpha'; className = 'AlphaConsumerMod'; name = 'Patrick Probe Alpha' },
    @{ id = 'patrick_probe_beta'; className = 'BetaConsumerMod'; name = 'Patrick Probe Beta' }
)

foreach ($mod in $mods) {
    $id = $mod.id
    $sourceClass = Join-Path $classesDir ($mod.className + '.class')
    if (-not (Test-Path -LiteralPath $sourceClass -PathType Leaf)) {
        throw "Test consumer $($mod.className) was not compiled: $sourceClass"
    }
    $staging = Join-Path $stageRoot $id
    if (Test-Path -LiteralPath $staging) {
        # The fixed staging path is strictly beneath this project's build directory.
        Remove-Item -LiteralPath $staging -Recurse -Force
    }
    $pkg = Join-Path $staging 'comvyne\patrickstar\consumers'
    [void](New-Item -ItemType Directory -Force -Path $pkg)
    Get-ChildItem -LiteralPath $classesDir -Filter ($mod.className + '*.class') -File |
        ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $pkg }

    $metadata = [ordered]@{
        schemaVersion = 1
        id = $id
        version = '1.0.0'
        name = $mod.name
        environment = 'server'
        entrypoints = @{ main = @('comvyne.patrickstar.consumers.' + $mod.className) }
        depends = [ordered]@{
            fabricloader = '>=0.20.1'
            minecraft = '26.3'
            'fabric-api' = '>=0.166.2'
            patrick_star_api = '>=2.0.0+26.3'
        }
    }
    $json = $metadata | ConvertTo-Json -Depth 8
    [System.IO.File]::WriteAllText(
        (Join-Path $staging 'fabric.mod.json'),
        $json,
        [System.Text.UTF8Encoding]::new($false)
    )

    $jar = Join-Path $outputRoot ($id + '-1.0.0.jar')
    & jar.exe --create --file $jar -C $staging .
    if ($LASTEXITCODE -ne 0) { throw "jar.exe failed packaging $id" }
    Write-Host "Packaged separate Fabric consumer: $jar"
    if (-not $NoInstall) {
        $target = Join-Path $runMods ($id + '-1.0.0.jar')
        Copy-Item -LiteralPath $jar -Destination $target -Force
        Write-Host "Installed only into isolated dev server: $target"
    }
}
