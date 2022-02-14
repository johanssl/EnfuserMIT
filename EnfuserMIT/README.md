# Enfuser2
New version for FMI-ENFUSER as Maven. This development version is intellectual property of The Finnish Meteorological Insitute (FMI)

Installation: Build the project and copy-rename Enfuser2.jar (the one approx. 50Mb that has all dependencies) to any installation directory.
Then, unzip the content of installationPackage.zip to this directory. Take a look at the documentation portal (Confluence) for quick installation guides.
In particular, check documentation for globOps.txt before using.

Notes:
- The zip-file "WindowsExternals" is only for Windows OS.
- The included API-key package (crypts.dat) has FMI personal API-keys attatched. Each user should build their own according to instructions (ask the author).
- Due to a namespace change, previous osmLayer files are unfortunately may NOT combatible with this version. Use e.g., the "headless_mapf" to create them again.
