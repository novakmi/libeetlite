(c) Michal Novák, it.novakmi@gmail.com, see LICENSE file

# libeetlite

[![Build and test](https://github.com/novakmi/libeetlite/actions/workflows/ci.yml/badge.svg)](https://github.com/novakmi/libeetlite/actions/workflows/ci.yml)
[![Pipeline status](https://gitlab.com/novakmi/libeetlite/badges/master/pipeline.svg)](https://gitlab.com/novakmi/libeetlite/-/pipelines)
 
Groovy library to support creation and parsing  XML EET (soap) messages.
Groovy knihovna pro podporu vytváření a zpracování XML EET (soap) zpráv.

## Požadavky

Knihovna podporuje:

- Java 11 nebo novější
- Groovy 5.x (při běhu na Java 8 se automaticky použije Groovy 4.x)
- Neoficiálně je testována také podpora Java 8 při použití zdrojových Groovy
  souborů přímo, bez sestaveného JAR artefaktu

Artefakty jsou kompilovány pro Java 11, takže mohou běžet na Java 11 a všech
novějších podporovaných verzích JVM. Jiná hlavní verze Groovy není automaticky
garantována.

Při použití zdrojových souborů `.groovy` přímo z vlastního Groovy skriptu platí
stejná minimální verze Java 11 nebo novější. Při běhu na Java 8 se použije
kompatibilní profil s Groovy 4.x a staršími kompatibilními závislostmi.

## Testování s jinou verzí Groovy

Verze Groovy uvedená v `gradle.properties` je výchozí. Pokud je v aktuálním
shellu nastavena jiná verze Groovy, například pomocí SDKMAN, lze ji předat
Gradlu přes vlastnost `groovyVersion`:

```bash
gradle clean test --no-daemon "-PgroovyVersion=$(basename "$GROOVY_HOME")"
```

Například Java 8 s Groovy 4.x použije automaticky také Java 8 kompatibilní
verze XMLSec, TestNG a Logback. Experimentální Groovy 6 vyžaduje Java 17 nebo
novější a lze jej otestovat stejným způsobem:

```bash
gradle clean test --no-daemon "-PgroovyVersion=6.0.0-RC-2"
```

## Licence

Knihovna i zdrojové kódy jsou k dispozici zdarma pod MIT licencí. 
Autor nenese jakoukoliv odpovědnost za funkčnost a chování, ani neposkytuje jakoukoliv záruku.

Viz soubor `LICENSE`

## Changelog

* 2026-09-15 version 2.0.0 
  * EET 2.0 (rozhraní v4.1)
  * datová věta používá `eic_popl` a `id_jednotky` v namespace v4
  * odstraněny kódy PKP/BKP, rozpad DPH a zjednodušený režim
  * odpověď nyní vrací potvrzovací kód `pok`

* 2020-03-09 version 0.7.0
  * odstranena zavislost na groovy-dateutil
  * mensi refactoring kodu

* 2020-03-03 version 0.6.0
  * aktualizce pro kompatibilitu s JDK 11 a vyssi
  * aktualizace zavislosti
  * mala aktualizace testu
  * vytvoren soubor gradle.properties, jmeno projektu presunuto z settings.gradle

* 2020-02-17 version 0.5.1

  * aktualizace certifikatu (pro automaticke testy)
  * aktualizace gradle build scriptu pro gradle 6.1, maven plugin 
  * aktualizace gitlab CI scriptu
  * odstraneni gradle wrapper
  * aktualizace testů (odstraněna kontrola -ff u FIK z testovacího prostředí - vrací -fa)
  * aktualizace README a LICENSE

* 2017-11-07 version 0.5.0
  * validace oproti EET XSD schema
  * aktualizace závislostí
  * aktualizace gradle wrapper
  * aktualizace kontroly závislostí v gradle skriptu
  * přidány pomocné funkce `isOvereni`, `fixOvereniResponse`

* 2017-03-06 version 0.4.1
  * heslo certifikátu se neloguje 
  
* 2017-03-06 version 0.4.0
  * certikát poplatníka je nyní předáván jako stream (je třeba použít `new FileInputStream(path)` a po volání
   funkce `makeMsg` jej zavřít metodou `close`)
  * do `EetUtil` přidány pomocné funkce
    * `encrypt`, `decrypt`
    * `nowToIso`, `dateToIso`, `isoToDate`
  * ze jména `package` ostraněn `test`
  * rozšířenen interface o příznak `failed`,  pole `warnings` (seznam kódů a textů varování) a 
    pole `errors` (seznam kódů a textů chyb)
   * POZOR! `fik` není již vracen jako návratová hodnota, ale jako atribut `ret.fik`

* 2017-02-20 version 0.3.0
   * knihovna vrací společně se zprávou XML i PKP (které má byt součástí  účtenky), PKP není třeba získávat z XML

* 2016-11-21 version 0.2.0
    * knihovna vrací společně se zprávou XML i BKP (které má byt součástí  účtenky), BKP není třeba získávat z XML
    * vstup většiny parametrů se kontroluje pomocí regexp   

* 2016-11-12 version 0.1.0
    * první verze upravena z eetlite
     
## Další vývoj (TODO)     

* lepší reportování chyb
* zpracování chybové odpovědi
* kontrola podpisu odpovědi
* podpora pro hash (zakódování) hesla v konfiguračním souboru

## Instalace

JCenter (`jcenter.bintray.com`) již není aktivně dostupný, proto se z něj
`libeetlite` nestahuje. Aktuální verze 2.0.0 není v tomto projektu
deklarována jako artefakt dostupný z Maven Central; použijte zdrojové soubory
nebo si knihovnu sestavte lokálně.

### Použití zdrojových souborů

Naklonujte repozitář a přidejte adresář se zdrojovými soubory Groovy na
classpath aplikace:

```bash
git clone https://github.com/novakmi/libeetlite.git
groovy -cp libeetlite/src/main/groovy vaše_aplikace.groovy
```

Skript `eetlite-script` používá tento způsob. Při použití zdrojů je stále
nutné zajistit také závislosti knihovny (`groovy-xml`, `xmlsec` a `slf4j-api`);
jejich stažení zajišťuje Gradle nebo Grape podle způsobu spuštění aplikace.

### Lokální sestavení JAR

Pro vytvoření JAR souboru použijte Gradle 9.6.1 nebo novější s Java 21:

```bash
gradle clean jar
```

Výsledný soubor bude v adresáři `build/libs/`. Pro instalaci do lokálního
Maven repozitáře lze použít vlastní Gradle konfiguraci nebo zkopírovat JAR
do aplikace společně s jeho runtime závislostmi. Závislosti projektu se
stahují z Maven Central, který je deklarován v `build.gradle`.

## Kontakt

Stránky projektu:

https://sites.google.com/view/eetlite

K hlášení chyb, podávání podnětů na zlepšení lze použít:  

https://github.com/novakmi/libeetlite/issues  
https://gitlab.com/novakmi/libeetlite/issues
  
e-mail: it.novakmi@gmail.com

## Podobné projekty a odkazy

http://www.etrzby.cz/cs/technicka-specifikace  

https://github.com/l-ra/openeet    
https://github.com/todvora/eet-client  

## Příklad použití

https://github.com/novakmi/eetlite  
