Notes01
=======

Obtain "notes" from OSM from within a bounding box and create a small file that will fit on a Garmin eTrex.

Although the notes API http://wiki.openstreetmap.org/wiki/API_v0.6#Map_Notes_API supports a "notes.gpx" endpoint that allows a GPX file to be downloaded directly, it's not formatted in a way that can be understood by old Garmin GPSs.


Eclipse
-------
You can import it into Eclipse if you want to (although, given that it's only one Java file `Notes01.java`, there's really no need to).  Just `javac Notes01.java` should work if you're not in an IDE.


Usage examples
--------------
    java Notes01 -bbox=-1.2022,53.1365,-1.1882,53.1435 -output_gpx=notes.gpx

This obtains only open notes from the OSM live API in this bounding box:

http://www.openstreetmap.org/?box=yes&bbox=-1.2022,53.1365,-1.1882,53.1435#map=15/53.1400/-1.2006&layers=N

and creates a GPX file of them which can then be uploaded to a Garmin GPS via either Mapsource or gpsbabel:

    gpsbabel -i gpx -f notes.gpx -o garmin -F usb:

Because the number of characters that can be included in Garmin eTrex notes is limited, only the first line is used, so note 57081 becomes:

```xml
<wpt lat="53.141339" lon="-1.1949134">
<name>D N57081</name>
<cmt>this area of land has been turned into a car park</cmt>
<desc>this area of land has been turned into a car park</desc>
<sym>Shipwreck</sym>
</wpt>
```
The name is currently hardcoded to "D N" + the note number and the default Garmin symbol used is "Shipwreck".

Other supported parameters include `=symbol=W` (to use Garmin symbol W), `=limit=X` (to change the download limit from the API default of 100) to X, `-closed=Y` (to download also notes closed in the last Y days) and `-display-name=Z` (to download only notes opened or commented on by the user with display name Z).  Only valid Garmin symbols without spaces in (such as `Shipwreck` or `Forest`) are currenly supported.  Therefore:

    java Notes01 -bbox=-1.2022,53.1365,-1.1882,53.1435 -symbol=Forest -closed=7 -limit=3 -display_name=SomeoneElse -output_gpx=notes2.gpx

This obtains up to 3 notes (open, and those closed in the last 7 days) from the OSM live API in the same bounding box, opened or commented on by user "SomeoneElse".
