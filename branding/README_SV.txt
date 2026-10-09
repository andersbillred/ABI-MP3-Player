ABI MP3 Player – Android ikonpaket

INSTALLATION
1. Packa upp ZIP-filen.
2. Kopiera innehållet i app/src/main/res/ till motsvarande res-katalog i ditt Android Studio-projekt.
3. Kontrollera att AndroidManifest.xml har:
   android:icon="@mipmap/ic_launcher"
   android:roundIcon="@mipmap/ic_launcher_round"
4. Bygg om appen. Vid behov avinstallera den gamla versionen för att tömma launcherns ikoncache.

INNEHÅLL
- mipmap-mdpi till mipmap-xxxhdpi: vanliga launcher-ikoner (48–192 px) och runda varianter.
- mipmap-anydpi-v26: adaptiva ikoner för Android 8+.
- drawable-nodpi/ic_launcher_foreground.png: transparent förgrund för adaptiv ikon.
- drawable/ic_launcher_monochrome.xml: enkel enfärgad symbol för Android 13+ tematiska ikoner.
- values/ic_launcher_background.xml: mörk bakgrundsfärg.
- play_store_512.png: 512 x 512 px för Google Play.
- icon_master_1024.png: redigeringsmaster i PNG-format.

OBS: Den adaptiva ikonen använder en nedskalad version av hela designen för att bevara ABI-texten. På små skärmar kan texten bli svårläst. Den tematiska ikonen använder därför en förenklad hörlur/play-symbol.
