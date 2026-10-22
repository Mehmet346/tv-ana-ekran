# Devre disi birakilan TUM uygulamalari geri acar.
param([string]$Tv = '192.168.1.173:5555')

& adb connect $Tv | Out-Null
# Eski Zeasn ana ekranini varsayilan yap
& adb -s $Tv shell cmd package set-home-activity cn.zeasn.launcher.vstoresubclient.hikeen/com.zeasn.smart.tv.ui.WelcomeActivity
$kapali = & adb -s $Tv shell pm list packages -d | ForEach-Object { $_ -replace '^package:', '' }
if (-not $kapali) { 'Kapali uygulama yok.'; return }
foreach ($p in $kapali) {
  $r = & adb -s $Tv shell pm enable --user 0 $p
  "{0,-50} {1}" -f $p, $r
}
