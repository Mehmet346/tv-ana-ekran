# Hi-Level / ATMACA TV sadelestirme - Asama 3 (kullanilmayan giris servisleri + guncelleyici)
# Uygulamalar SILINMEZ, sadece devre disi birakilir. Geri almak icin: tv-geri-al.ps1
param([string]$Tv = '192.168.1.173:5555')

$paketler = @(
  'com.realtek.tv.atv'          # Analog TV girisi
  'com.realtek.tv.avtvinput'    # AV girisi
  'com.realtek.tv.vgatvinput'   # VGA girisi
  'com.realtek.tv.ypptvinput'   # Komponent (YPbPr) girisi
  'com.atmaca.otaupdater'       # Atmaca yazilim guncelleyici
)

& adb connect $Tv | Out-Null
foreach ($p in $paketler) {
  $r = & adb -s $Tv shell pm disable-user --user 0 $p
  "{0,-34} {1}" -f $p, $r
}
