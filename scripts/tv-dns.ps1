# TV'de kablolu baglantinin IPv6'sini kapatir; boylece DNS olarak sadece AdGuard (modemden gelen) kalir.
# TV yeniden baslayinca (elektrik kesintisi vb.) sifirlanir, o zaman bu betigi tekrar calistir.
# Geri almak icin: .\tv-dns.ps1 -GeriAl   (ya da TV'yi yeniden baslat)
param([string]$Tv = '192.168.1.173:5555', [switch]$GeriAl)

$deger = if ($GeriAl) { 0 } else { 1 }
& adb connect $Tv | Out-Null
& adb -s $Tv root | Out-Null
Start-Sleep 5
& adb disconnect $Tv | Out-Null; & adb connect $Tv | Out-Null; Start-Sleep 2
& adb -s $Tv shell "echo $deger > /proc/sys/net/ipv6/conf/eth0/disable_ipv6"
& adb -s $Tv unroot | Out-Null
Start-Sleep 5
& adb disconnect $Tv | Out-Null; & adb connect $Tv | Out-Null; Start-Sleep 4
'TV DNS: ' + ((& adb -s $Tv shell 'getprop net.dns1; getprop net.dns2; getprop net.dns3') -join ' ').Trim()
