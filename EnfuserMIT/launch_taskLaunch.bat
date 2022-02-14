ECHO This can be used to achieve the same outcome as with launch_taskScheduler.
ECHO This will not check any scheduled run hours, but simply launches a modelling task for HMA.
ECHO Note: Input data provided by the DataMiner must exist (i.e., DataMiner is (and has been) running in the background).
java -Xmx16g -jar Enfuser2.jar -runmode=area_task -name=hma