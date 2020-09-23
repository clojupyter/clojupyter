# TODO: Search for system folders to uninstall

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


foreach ($file in $(Get-ChildItem -Path "$env:AppData\jupyter\kernels").name) {
    if ("$file" -match $target) {
        echo "Uninstalling $file"
        rm -r "$env:AppData\jupyter\kernels\$file"
        if (test-path "$env:LocalAppData\Programs\$file") {
            rm -r "$env:LocalAppData\Programs\$file"
        }
    }
}
