# Hi-Level / ATMACA TV hiz ayarlari
# Animasyonlari yariya indirir, Chrome'u devre disi birakir.
# Geri almak icin: animasyonlarda 0.5 yerine 1 yaz; Chrome icin tv-geri-al.ps1
param([string]$Tv = '192.168.1.173:5555')

& adb connect $Tv | Out-Null
& adb -s $Tv shell "settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5"
& adb -s $Tv shell am force-stop com.android.chrome
& adb -s $Tv shell pm disable-user --user 0 com.android.chrome
