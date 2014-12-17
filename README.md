Notes01
=======

With the "-do_notes" command line argument it obtains the opening comments of "notes" from OSM from within a bounding box and creates a small file from them that will fit 
on a Garmin eTrex.

With the "-do_fixmes" command line argument it obtains "fixme" tags for nodes and ways from the Overpass API.

Although the notes API http://wiki.openstreetmap.org/wiki/API_v0.6#Map_Notes_API supports a "notes.gpx" endpoint that 
allows a GPX file to be downloaded directly, it's not formatted in a way that can be understood by old Garmin GPSs.


Building
--------
You can import it into Eclipse if you want to (although, given that it's only a couple of .java files, there's really no 
need to).  If you're not using an IDE, install a JDK (e.g. 
from http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html), then just ensure that the `javac` 
that you have just installed is on the PATH, and `javac Notes01.java` in order to 
create `Notes01.class`.  `Notes01Test.class` contains some unit tests and isn't needed to export notes.  
You can either `java Notes01 ...` or export `Notes01.jar`.
A `build.xml` file is also provided for `ant` users - `ant build` will create the .jar file.  To include support for 
the unit tests in the jar, place a `junit.jar` and a hamcrest jar into the "lib" directory and use `ant buildtest` instead.   


Usage examples
--------------
    java -jar Notes01.jar -do_notes -bbox=-1.2022,53.1365,-1.1882,53.1435 -output_gpx=notes.gpx

This obtains only open notes from the OSM live API in this bounding box:

http://owl.apis.dev.openstreetmap.org/?box=yes&bbox=-1.2022,53.1365,-1.1882,53.1435#map=15/53.1400/-1.2006&layers=N

and creates a GPX file of them which can then be uploaded to a Garmin GPS via either Mapsource or gpsbabel:

    gpsbabel -i gpx -f notes.gpx -o garmin -F usb:

Because the number of characters that can be included in Garmin eTrex notes is limited, only the first line is used, 
so note 57081 becomes:

```xml
<wpt lat="53.141339" lon="-1.1949134">
<name>S N0057081</name>
<cmt>this area of land has been turned into a car park</cmt>
<desc>this area of land has been turned into a car park</desc>
<sym>Shipwreck</sym>
</wpt>
```
The name is currently hardcoded to "S N" + the note number and the default Garmin symbol used is "Shipwreck".

Other supported parameters include `-notes_symbol=W` (to use Garmin symbol W), `-notes_limit=X` (to change the download limit from the 
API default of 100) to X, `-notes_closed=Y` (to download also notes closed in the last Y days), `-output_txt=` 
(to output a text file as well to complement the short Garmin comments) and `-notes_display_name=Z` 
(to download only notes opened or commented on by the user with display name Z).  Only valid Garmin symbols without spaces in 
(such as `Shipwreck` or `Forest`) are currently supported.  Therefore:

    java -jar Notes01.jar -do_notes -bbox=-1.2022,53.1365,-1.1882,53.1435 -notes_symbol=Forest -notes_closed=7 -notes_limit=3 -notes_display_name=SomeoneElse -output_gpx=notes2.gpx -output_txt=notes2.txt

This obtains up to 3 notes (open, and those closed in the last 7 days) from the OSM live API in the same bounding box, 
opened or commented on by user "SomeoneElse".  It writes a GPX file that can be sent to a Garmin device and also a text 
file that you can e.g. also email yourself for reference.

    java -jar Notes01.jar -do_fixmes -bbox=-1.2022,53.1365,-1.1882,53.1435 -notes_symbol=Forest -output_gpx=notes2.gpx -output_txt=notes2.txt

Similar to the above, but creates waypoints for nodes with "fixme" tags and for the first node of each way that has a fixme tag.
It doesn't process differently spelt tags (e.g. FIXME or Fixme) and doesn't process relations with "fixme" tags.


Supported Parameters
---------------------
### -input=some_input_file.txt
Process notes from an input file Notes XML previously obtained from a server rather than making an API call.

### -output_gpx=some_output_file.gpx
Specifies an output file name for the GPX file to be sent to the device.

### -output_txt=some_output_file.txt
Specifies an output file into which a text about the notes can be written.  Designed to be printed out or emailed, 
it contains more information than the GPX because it's not subject to the size limits of GPX (or GPI) comments. 

### -notes_display_name="Some User Name"
Specifies a user's display name to search for notes created by.  It will be URLencoded before being passed to the API

### -do_notes
Fetch OSM notes from the specified API and process them

### -do_fixmes
Fetch OSM "fixme" tags from `http://overpass-api.de/api/` and create waypoints for nodes with fixme tags, and the last node of every way that has a fixme tag.

### -notes_uid=112
Specifies a user's userid to search for notes created by.  
Useful for when display names change, or when they contain characters that can't easily be passed from the command line.

### -notes_dev
Use the dev server (`api06.dev.openstreetmap.org`) instead of the live one to retrieve OSM notes from.
 
### -debug=0
A number between 0 and 8, used to control the amount of debug written to stdout as processing occurs.  
The higher the number, the more debug.

### -notes_closed=0
Specifies the number of days a bug needs to be closed to no longer be returned.  Defaults to 0. 

### -notes_limit=100
Specifies the maximum number of entries returned. A value of between 1 and 10000 is valid; 100 is the default. 

### -notes_symbol=Forest
Specifies the symbol to use when creating Garmin waypoints.

### -bbox=-2.123,52.809,-0.331,53.521
The bounding box to check notes against, in normal OSM format (west, south, east, north).  



