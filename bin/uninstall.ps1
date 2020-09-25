
########################################################################################
#  DEFAULTS
########################################################################################

$UserDataPath = if ($env:JUPYTER_DATA_DIR) {
                    "$env:JUPYTER_DATA_DIR\kernels"
                 }
                 else {
                    "$env:AppData\jupyter\kernels"
                 }

$UserLibPath = "$env:LocalAppData\Programs"

$SystemDataPath = "$env:ProgramData\jupyter\kernels"

$SystemLibPath = "$env:ProgramFiles"

########################################################################################
# ARGUMENTS & FAIL CONDITIONS
########################################################################################

if ($args.count -gt 1) {
    echo ("Wrong number of arguments (" + $args.count + ") passed to program")
    exit 1
}

if ($args.count -eq 0 -or !$args[0]) {
    $target = "clojupyter-$(bin\version)"
}
else {
    $target = $args[0]
}

#########################################################################################
# UNINSTALLING
#########################################################################################

if (Test-Path $UserDataPath) {
    foreach ($file in $(Get-ChildItem -Path $UserDataPath).name) {
        if ("$file" -match $target) {
            echo "Uninstalling $file"
            rm -r "$UserDataPath\$file"
            if (Test-Path "$UserLibPath\$file") {
                rm -r "$UserLibPath\$file"
            }
        }
    }
}

if (Test-Path $SystemDataPath) {
    foreach ($file in $(Get-ChildItem -Path $SystemDataPath).name) {
        if ("$file" -match $target) {
            echo "Uninstalling $file"
            rm -r "$SystemDataPath\$file"
            if (test-path "$SystemLibPath\$file") {
                rm -r "$SystemLibPath\$file"
            }
        }
    }
}
