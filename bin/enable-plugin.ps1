
Param (
    [Alias('k')][string] $kernel,
    [Parameter(Mandatory=$true, Position=0)][string] $target
)

if (!$kernel) {
    $kernel = "clojupyter-$(bin/version)"
}

$kt = bin/list ^${kernel}$

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-Dependencies {
    Param (
        $jar
    )
    $manifest = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName) |
                Select-Object -ExpandProperty Entries |
                Where-Object {$_.Name -eq "MANIFEST.MF"}
    $file = New-TemporaryFile
    $file.Delete()
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($manifest, $file.FullName)
    $manifest.Dispose()
    $CP_key = $false
    $deps = foreach ($line in $(Get-Content $file)) {
                Switch -Regex ($line) {
                    "^Class-Path: (.+)$" {
                        $CP_key = $true
                        Write-Output $matches[1]
                    }
                    "^[^:]+$" {
                        if ($CP_key) {
                                Write-Output $line.Substring(1)
                            }
                    }
                    default {
                        $CP_key = $false
                    }
                }
            }
    foreach ($dep in $($deps -join '').split(" ")) {
        $file = Get-ChildItem "$($jar.Directory)\$dep"
        if ($file.target) {
            Get-ChildItem $file.target
        }
        else {
            $file
        }
    }
}

function enable {
    Param (
        $plugin
    )
    Write-Host "Enabling plugin $plugin"
    Push-Location "$($kt.LibPath)\plugins\enabled"
    New-Item -ItemType SymbolicLink -Path "$plugin.jar" -Target "../$plugin.jar" | Out-Null
    Pop-Location
}

$match = @()
foreach ($plugin in $kt.Plugins) {
    if (! ($kt.EnabledPlugins -Contains $plugin)) {
        if ($plugin -match $target) {
            $match += $plugin
            Get-Dependencies $(Get-ChildItem "$($kt.LibPath)\plugins" "$plugin.jar") |
                Where-Object { $kt.Plugins -contains $_.Basename } |
                Where-Object { !($kt.EnabledPlugins -contains $_.Basename) } |
                foreach { $match += $_.Basename }
        }
    }
}

foreach ($m in $($match | Sort-Object | Get-Unique)) {
    enable $m
}
