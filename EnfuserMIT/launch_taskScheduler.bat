ECHO This launches a check for scheduled modelling tasks. This is managed by data/taskTriggers.csv.
ECHO As default, there's a modelling task scheduled to occur once every 4 hours. 
ECHO In order for that to happen, this command should be launced EVERY HOUR once.
java -Xmx16g -jar Enfuser2.jar -runmode=server