(c) Michal Novák, it.novakmi@gmail.com, see LICENSE file

# libeetlite
 
Groovy library to support creation and parsing  XML EET (soap) messages.
Groovy knihovna pro podporu vytváření a zpracování XML EET (soap) zpráv.

## Licence

Knihovna i zdrojové kódy jsou k dispozici zdarma pod MIT licencí. 
Autor nenese jakoukoliv odpovědnost za funkčnost a chování, ani neposkytuje jakoukoliv záruku.

Viz soubor `LICENSE`

## Changelog

* 2017-03-06 version 0.4.0
  * certikát poplatníka je nyní předáván jako stream (je třeba použít `new FileInputStream(path)` a po volání
   `makeMsg` jej zavřít metodou `close`)
  * do `EetUtil` přidány pomocné funkce
    * `encrypt`, `decrypt`
    * `nowToIso`, `dateToIso`, `isoToDate`
  * ze jména `package` ostraněn `test`
  * rozšířenen interface o `failed`, `warnings` (seznam kódů a textů varování) a `errors` (seznam kódů a textů chyb)
  * pozor, `fik` není vracen jako návratová hodnota, ale jako attribut `ret.fik`   

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
* validace XML zprávy oproti XML schématu
* kontrola podpisu odpovědi
* podpora pro hash (zakódování) hesla v konfiguračním souboru

## Instalace

`libeetlite` je dostupná v JCenter Maven repository (`jcenter.bintray.com`)

group: `com.github.novakmi`, name: `libeetlite`, version: `<požadovaná verze`

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
