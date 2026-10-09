# ABI MP3 Player

Android-spelare för lokalt sparade poddar, med ett mörkt Winamp-inspirerat gränssnitt på svenska och engelska.

## Användning

- Tryck **Lägg till** och välj en eller flera MP3-filer i Androids filväljare.
- Tryck på en podd i spellistan för att spela den.
- Använd play/paus, föregående/nästa, −10/+10 och tidsreglaget.
- Tider visas som minuter:sekunder, inklusive återstående tid. Långa poddar visar exempelvis 125:04.
- Dra i **≡** för att flytta en fil uppåt eller nedåt. Tryck på handtaget för alternativet Flytta upp/Flytta ner.
- Tryck **×** för att ta bort en fil från spellistan. Ljudfilen raderas inte från telefonen.

Varje fil har en egen sparad position. Positionen sparas varje sekund under uppspelning och vid paus, sökning och filbyte. Spellista, ordning och vald fil finns kvar när appen öppnas efter telefonens omstart. Appen öppnar den valda filen pausad vid sparad position; tryck play för att fortsätta. En abrupt avstängning kan förlora ungefär den senaste sekunden.

Bakgrundsuppspelning använder en foreground service, wake lock och Androids mediesession. Uppspelningen pausas när hörlurar kopplas bort eller annan uppspelning tar ljudfokus. Filväljaren ger beständig åtkomst till valda filer utan bred åtkomst till telefonens lagring.

## Bygga och kontrollera

Projektets befintliga SDK-krav är Android 14 eller senare (minSdk 34), compile/target SDK 37.

Öppna projektet i Android Studio och kör app-modulen, eller kör:

~~~powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
~~~

Med ansluten emulator eller telefon:

~~~powershell
.\gradlew.bat :app:connectedDebugAndroidTest
~~~

PlaylistPersistenceTest kontrollerar återläst spellista, vald fil, individuell position och borttagning/återläggning. Verifiera även följande på telefonen:

1. Lägg till minst tre MP3-filer, spela och sök till en position mitt i en fil.
2. Pausa, byt fil och gå tillbaka. Kontrollera separat sparad position.
3. Dra om ordningen, ta bort en fil och stäng/öppna appen.
4. Starta om telefonen och kontrollera samma ordning och position.
5. Spela med släckt skärm och testa kontroller från medienotisen.
6. Koppla ur hörlurar och kontrollera att uppspelningen pausas.
7. Kontrollera porträtt, landskap, lång spellista och filer som flyttats eller raderats.

Androids dokumentation: [MediaPlayer](https://developer.android.com/media/platform/mediaplayer/basics), [foreground-tjänster](https://developer.android.com/develop/background-work/services/fgs/service-types), [beständig filåtkomst](https://developer.android.com/training/data-storage/shared/documents-files).

## Verifiering i denna arbetsmiljö

Appens tre Kotlin-filer och PlaylistPersistenceTest har kompilerats utan fel direkt mot det installerade Android SDK:t. Fullständig Gradle-kompilering, lint och testkörning kunde inte slutföras: Gradles generering av beroendeaccessorer stoppades av AccessDeniedException när Java stängde en ZIP/JAR-fil. Felet uppstod även med en isolerad kopia av Gradle i projektmappen, innan appens byggsteg kördes.

ADB stoppades också av miljöns filåtkomstfel, så uppspelning, omstartsåterställning och layout behöver verifieras på emulator eller telefon. Ingen färdig APK har producerats i denna miljö.

## Appikon

Ikonpaketet från E:\ABI_MP3_Player_Android_Icons är integrerat i app/src/main/res. Paketet innehåller ikoner för mdpi–xxxhdpi, adaptiva ikoner och en enfärgad variant för tematiska ikoner. De tidigare standardikonerna är borttagna och manifestet använder @mipmap/ic_launcher och @mipmap/ic_launcher_round. Alla 15 importerade resurser matchar originalfilerna; Androids aapt2 har kompilerat och länkat ikonpaketet utan fel.

Google Play-bilden och redigeringsmastern finns i branding/play_store_512.png och branding/icon_master_1024.png. Dessa ligger utanför appens resurser och packas inte i appen.

Bygg och installera appen över den befintliga installationen för att behålla spellista och positioner. Om telefonens hemskärm fortfarande visar den gamla ikonen efter uppdateringen kan du starta om telefonen.

## Språk

Appen följer telefonens språk: svenska för svenska inställningar och engelska som standardspråk för övriga språk. Androids inställningar för appens språk erbjuder svenska och engelska via locales_config.xml. Alla gränssnittstexter, tillgänglighetsetiketter, felmeddelanden och medienotiser använder språkresurser. Filnamnen i spellistan behålls som de är.

Engelska texter finns i app/src/main/res/values/strings.xml och svenska i app/src/main/res/values-sv/strings.xml. Fler översättningar kan läggas till i motsvarande values-katalog och listas i res/xml/locales_config.xml.

Språkresurserna har kompilerats och länkats med aapt2 och Kotlin-koden inklusive LocalizationTest har direktkompilerats utan fel. LocalizationTest kontrollerar svenska, engelska och återgång till engelska för franska, inklusive formaterade texter och tillgänglighetsetiketter. Testerna har inte körts på en enhet i denna miljö.
