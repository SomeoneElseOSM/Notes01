Notes01
=======

Obtain "notes" from OSM from within a bounding box and create a small file that will fit on a Garmin eTrex

Eclipse
-------
You can import it into Eclipse if you want to (although, given that it's only one Java file `Changeset1.java`, there's really no need to).  Just `javac Changeset1.java` should work if you're not in an IDE.


Usage examples
--------------
    java Notes01 -bbox=-1.2022,53.1365,-1.1882,53.1435 -output_gpx=notes.gpx

This obtains open notes from the OSM live API in this bounding box:

http://www.openstreetmap.org/?box=yes&bbox=-1.2022,53.1365,-1.1882,53.1435#map=15/53.1400/-1.2006&layers=N

and creates a GPX file of them which can then be uploaded to a Garmin GPS via either Mapsource or gpsbabel:

gpsbabel -i gpx -f notes.gpx -o garmin -F usb:

Because the number of characters that can be included in Garmin eTrex notes is limited, only the first line is used, so note 57081 becomes:

<pre>
<wpt lat="53.141339" lon="-1.1949134">
<name>D N57081</name>
<cmt>this area of land has been turned into a car park</cmt>
<desc>Shipwreck this area of land has been turned into a car park</desc>
<sym>Shipwreck</sym>
</wpt>
</pre>

