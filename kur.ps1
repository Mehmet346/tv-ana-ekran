# Ana ekrani derler ve TV'ye kurar.  Kullanim:  .\kur.ps1   (ya da  .\kur.ps1 -Tv 192.168.1.151:5555)
param([string]$Tv = '192.168.1.173:5555')

$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$env:Path = "$env:JAVA_HOME\bin;" + [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')

Push-Location $PSScriptRoot
try {
    # SDK surum uyarisi zararsiz; sadece cikis koduna bak
    & .\gradlew.bat assembleRelease --console=plain -q 2>$null
    if ($LASTEXITCODE -ne 0) { 'Derleme basarisiz: ayrinti icin  .\gradlew.bat assembleRelease  calistir'; return }
    $apk = 'app\build\outputs\apk\release\app-release.apk'
    Copy-Item $apk ..\TvAnaEkran.apk -Force
    & adb connect $Tv | Out-Null
    & adb -s $Tv install -r $apk
    & adb -s $Tv shell cmd package set-home-activity com.ozdogan.tvanaekran/.MainActivity | Out-Null
    'Kuruldu. Degisikligi gormek icin kumandada HOME tusuna bas.'
} finally {
    Pop-Location
}
