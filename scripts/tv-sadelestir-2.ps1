# Hi-Level / ATMACA TV sadelestirme - Asama 2 (Zeasn arayuzu -> Projectivy)
# Uygulamalar SILINMEZ, sadece devre disi birakilir. Geri almak icin: tv-geri-al.ps1
param([string]$Tv = '192.168.1.173:5555')

$paketler = @(
  'com.hikeen.transfer'                           # Acilis yonlendiricisi (her acilista Zeasn'i baslatiyor)
  'cn.zeasn.launcher.vstoresubclient.hikeen'      # Zeasn ana ekrani
  'cn.zeasn.overseas.tv.vstoresubclient.hikeen'   # Zeasn uygulama magazasi
  'com.zeasn.services.general'                    # Zeasn arka plan servisleri
  'com.zeasn.deviceportal.asdprovider.hikeen'     # Zeasn reklam saglayicisi
)

& adb connect $Tv | Out-Null
foreach ($p in $paketler) {
  $r = & adb -s $Tv shell pm disable-user --user 0 $p
  "{0,-50} {1}" -f $p, $r
}
& adb -s $Tv shell cmd package set-home-activity com.spocky.projengmenu/.ui.home.MainActivity
