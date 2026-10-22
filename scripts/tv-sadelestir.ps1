# Hi-Level / ATMACA TV sadelestirme - Asama 1 (guvenli liste)
# Uygulamalar SILINMEZ, sadece devre disi birakilir. Geri almak icin: tv-geri-al.ps1
param([string]$Tv = '192.168.1.173:5555')

$paketler = @(
  'com.accuweather.android'                       # Hava durumu
  'com.adups.fota'                                # Adups FOTA (arka planda veri toplayan guncelleyici)
  'com.adups.fota.sysoper'
  'com.hikeen.hotelmenu'                          # Otel modu
  'com.android.dreams.phototable'                 # Foto ekran koruyucu
  'com.android.wallpaper.livepicker'              # Canli duvar kagidi
  'com.android.gallery3d'                         # Galeri (USB medya icin rtk.mediabrowser kaliyor)
  'com.android.printspooler'                      # Yazdirma
  'com.google.android.printservice.recommendation'
  'com.android.providers.calendar'                # Takvim
  'com.google.android.syncadapters.calendar'
  'com.google.android.syncadapters.contacts'
  'com.google.android.feedback'                   # Google geri bildirim
  'com.google.android.tv.bugreportsender'         # Hata raporu gonderici
  'com.google.android.instantapps.supervisor'     # Anlik uygulamalar
  'com.google.android.apps.turbo'                 # Cihaz sagligi
  'com.svox.pico'                                 # Eski metin okuma motoru
)

& adb connect $Tv | Out-Null
foreach ($p in $paketler) {
  $r = & adb -s $Tv shell pm disable-user --user 0 $p
  "{0,-50} {1}" -f $p, $r
}
