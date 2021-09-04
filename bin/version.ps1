Select-String -Pattern '\(defproject .+"(.+)"' -Path project.clj | % { $_.Matches.Groups[1].Value }
