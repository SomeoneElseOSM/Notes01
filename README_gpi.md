Sending notes to a device as a GPI file
=======================================

Another option is to convert the GPX files created by this utility to GPI files and send those to the 
device instead.  The advantage of this is that GPI files allow more data per point of interest; 
the disadvantage is that they can't easily be deleted from the device itself (although you can create 
waypoints from GPI POIs in order to keep track of notes against them), 
and you have to go into mass storage mode to transfer the data.

Among other methods, it's possible to use GPSBabel to do the conversion:

    gpsbabel -i gpx -f generate_notes.gpx -o garmin_gpi,category="Some Name",notes -F generate_notes.gpi 

+ Put the Garmin into mass storage mode.

+ Create a directory "Poi" below "Garmin" on the SD Card on your device

+ Copy the notes file (in this case `generate_notes.gpi`) to that directory.

Where the resulting POIs appear on the menu will depend on the device.
On my eTrex Vista HCx "Find / Custom Points of Interest" will find them.



