
########################################################################################
# ARGUMENTS
########################################################################################

Param (
    [switch] $system,
    [Alias('o')][switch] $overwrite,
    [Alias('i')][string] $identity,
    [Alias('j')][string] $jar
)

########################################################################################
#  DEFAULTS
########################################################################################

if (!$identity) {
    $identity = "clojupyter-$(bin\version)"
}

if (!$jar) {
    $jar = "target\clojupyter-$(bin\version).jar"
}

if ($system) {
    $datadir = "$env:ProgramData\jupyter\kernels"
    $libdir = "$env:ProgramFiles"
}
elseif ($env:JUPYTER_DATA_DIR) {
    $datadir = "$env:JUPYTER_DATA_DIR\kernels"
    $libdir = "$env:LocalAppData\Programs"
}
else {
    $datadir = "$env:AppData\jupyter\kernels"
    $libdir = "$env:LocalAppData\Programs"
}

#########################################################################################
# FAIL CONDITIONS
#########################################################################################
# TODO
#

#########################################################################################
# INSTALLING
#########################################################################################

Write-Host Installing $identity
mkdir $libdir\$identity\plugins\enabled >$null
mkdir $datadir\$identity >$null
cp $jar $libdir\$identity\$identity.jar
cp -r target\lib $libdir\$identity

pushd $libdir\$identity\lib
New-Item -ItemType SymbolicLink -Path "$identity.jar" -Target "../$identity.jar" >$null
popd

cp resources\clojupyter\assets\logo-64x64.png $datadir\$identity
$kernel = bin/kernel.json.ps1 -i $identity -libdir $libdir
$UTF8Only = New-Object System.Text.Utf8Encoding($false)
[System.IO.File]::WriteAllLines("$datadir\$identity\kernel.json", $kernel, $UTF8Only)
