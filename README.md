# EnfuserMIT
This is the repository for MIT-licensed ENFUSER model source code, capable for AQ predictions at Helsinki Metropolitan Area. 

**The model in brief**
The ENFUSER (Environmental infromation fusion service)  is an operative local scale air quality model (a combination of Gaussian Plume and Puff modelling with) used in Helsinki Metropolitan area in Finland since 2018. The model predicts hourly pollutant concentrations for NO2, O3, PM2.5 and PM10. The main sources for input are a), HARMONIE NWP b), FMI-SILAM regional chemical transport model c), measurement data from local network of stations. The novelty of the modelling system is the incorporation of data assimilation in urban scale dispersion modelling. The data assimilation also provides a longer lasting, persistent learning mechanism to adjust local emission source modelling.

**The provided source code**
This repository is under development and due to legal limitations not all features have been included in the open source version of ENFUSER.
It still consists of more than 75 000 lines of Java code, including all the features developed for dispersion modelling and data assimilation.

The main limitation is the absence of global information sources, such as GFS meteorology, AQICN and OPENAQ measurements. Also, a creation process for GIS-packages - that are required for every modelling area - is currently not available. The necessary input files for Helsinki Metropolitan Area have been provided, however. With the provided source code anyone can, e.g., setup an idential modelling service that provides data for https://www.hsy.fi/en/air-quality-and-climate/air-quality-now/air-quality-map/.

TODO-list
- Javadoc is to be improved. Not all methods have been documented and in some cases the provided information is outdated.
- A more thorrough user guide is to be written. 

**Quick installation for Helsinki Metropolitan Area**
- Compile/Build Enfuser2.jar
- Copy Enfuser2.jar and installerPackage.zip to any desired installation directory. Unpacking is not needed (automatic when the program launches), but unpacking the installerPackage does not hurt either.
- Launch the model in "miner" runmode: 'java -jar -Xmx1g Enfuser2.jar -runmode=miner'. Keep the instance running in the background.
- Launch 'java -jar -Xmx16g Enfuser2.jar -runmode=area_task -name=hma' to make a modelling task execution. Alternatively, use 'java -jar -Xmx16g Enfuser2.jar -runmode=server' command, scheduled to occur once every hour.  

