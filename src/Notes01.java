import java.io.*;
import java.net.*;
import java.util.Hashtable;

import javax.xml.parsers.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

// See also https://github.com/SomeoneElseOSM/Notes01 for more details

public class Notes01 
{
    static final String live_api_path = "http://openstreetmap.org/api/0.6/";
    static final String dev_api_path = "http://api06.dev.openstreetmap.org/api/0.6/";

    static final String de_overpass_path = "http://overpass-api.de/api/";
    static final String ru_overpass_path = "http://overpass.osm.rambler.ru/cgi/";
    static final String fr_overpass_path = "http://api.openstreetmap.fr/oapi/interpreter/";
    static final String ch_overpass_path = "http://overpass.osm.ch/api/";

    final static int Log_Debug_Off = 0;			// Used to turn debug off
    final static int Log_Serious = 1;			// A serious error has occurred, or we always want to output something. 
    final static int Log_Error = 2;				// An error that we can work around has occurred
    final static int Log_Warning = 3;			// Not currently used
    final static int Log_Return = 4; 			// Return values from top-level subroutines
    final static int Log_Informational_1 = 5;	// Important informational stuff
    final static int Log_Top_Routine_Start = 6;	// top-level routine start code
    final static int Log_Low_Routine_Start = 7; // low-level routing start code
    final static int Log_Informational_2 = 8;	// Any other informational stuff

    /* ------------------------------------------------------------------------------
     * Parameters common to both API notes and "fixme" tags
     * ------------------------------------------------------------------------------ */
    final static String param_debug = "-debug=";
    final static String param_output_gpx = "-output_gpx=";
    final static String param_output_txt = "-output_txt=";
    final static String param_bbox = "-bbox=";	// Unlike Changeset1 the is passed to the API
    final static String param_do_notes = "-do_notes";
    final static String param_do_fixmes = "-do_fixmes";

    /* ------------------------------------------------------------------------------
     * Parameters only relevant to API notes
     * ------------------------------------------------------------------------------ */
    final static String param_notes_input = "-notes_input=";
    final static String param_notes_closed = "-notes_closed=";
    final static String param_notes_limit = "-notes_limit=";
    final static String param_notes_symbol = "-notes_symbol=";
    final static String param_display_name = "-notes_display_name=";
    final static String param_notes_uid = "-notes_uid=";
    final static String param_notes_dev = "-notes_dev";

    /* ------------------------------------------------------------------------------
     * Parameters only relevant to "fixme" tags
     * ------------------------------------------------------------------------------ */
    final static String param_fixmes_api = "-fixmes_api=";

    /* ------------------------------------------------------------------------------
     * Other settings, including default values for the above.
     * ------------------------------------------------------------------------------ */
    static String actual_api_path = live_api_path;			// Defaults to live API
    static String arg_fixmes_api = "de";				// Defaults to Germany
    static String actual_overpass_path = de_overpass_path;		// Defaults to Germany

    static boolean arg_do_notes = false;
    static boolean arg_do_fixmes = false;

    static String arg_in_file = "";				// -input=       Default to no input file
    static String arg_out_gpx_file = "";		// -output_gpx=  No output GPX file default
    static String arg_out_txt_file = "";		// -output_txt=  No output TXT file default
    static int arg_debug = Log_Serious;			// -debug=       Default to Log_Serious
    static String arg_closed = "0";				// -closed=  	 Default to only showing open notes
    static String arg_limit = "100";			// -limit=   	 Default to returning up to 100 notes
    static String arg_symbol = "Shipwreck";		// -symbol=   	 The default Garmin symbol to use for created waypoints
    static String arg_bbox = "";				// -bbox=		 No bounding box default
	
    static String arg_min_lat_string = "";
    static String arg_min_lon_string = "";
    static String arg_max_lat_string = "";
    static String arg_max_lon_string = "";

    static File myFile;
    static OutputStream myoutput_gpxStream;
    static OutputStream myoutput_txtStream;
    static PrintStream myGpxPrintStream;
    static PrintStream myTxtPrintStream;

    private static Hashtable<String, String> osmLatHash;
    private static Hashtable<String, String> osmLonHash;
	
	private static String myGetNodeValue( Node passed_node )
	{
		String return_string = "";
		
		NodeList childNodes = passed_node.getChildNodes();
		int childCount = childNodes.getLength();
		
/* ------------------------------------------------------------------------------
* The actual text value is stored in a "#text" node
* ------------------------------------------------------------------------------ */
		for ( int i=0; i<childCount; i++ ) 
		{
			String childNodeName = "";
			
			try
			{
				childNodeName = childNodes.item(i).getNodeName();
			}
			catch( Exception ex )
			{
				// Carry on with a blank childNodeName
			}
			
			if ( childNodeName.equals( "#text" ))
			{
				try
				{
					return_string = return_string + childNodes.item(i).getNodeValue();
				}
				catch( Exception ex )
				{
					// Carry on with a blank childNodeValue (set to "" at the top of this method
				}
			}
/* ------------------------------------------------------------------------------
* We don't expect to see any other "childNodeName"s
* ------------------------------------------------------------------------------ */
		}
		
		return return_string;
	}
	
	
	private static void process_fixmes_xml( Node root_node, String passed_symbol )
	{
		int osm_fixmes_found = 0;

		if ( root_node.getNodeType() == Node.ELEMENT_NODE ) 
		{
			NodeList level_1_xmlnodes = root_node.getChildNodes();
			int num_l1_xmlnodes = level_1_xmlnodes.getLength();
	
			if ( arg_debug >= Log_Informational_2 )
			{
				System.out.println( "Fixmes L1 nodes found: " + num_l1_xmlnodes );
			}
	
			osmLatHash = new Hashtable<String, String>();
			osmLonHash = new Hashtable<String, String>();
			
			/* ------------------------------------------------------------------------------------------------------------
			 * Iterate through the OSM data looking for fixmes
			 * ------------------------------------------------------------------------------------------------------------ */
			for ( int cntr_1 = 0; cntr_1 < num_l1_xmlnodes; ++cntr_1 ) 
			{
				Node this_l1_item = level_1_xmlnodes.item( cntr_1 );
				String l1_item_type = this_l1_item.getNodeName();
	
				/* ------------------------------------------------------------------------------------------------------------
				 * We're expecting "node", "way", "relation" here, or stuff that we're not interested in such as "meta"
				 * ------------------------------------------------------------------------------------------------------------ */
				if ( l1_item_type.equals( "node" ))
				{
					String fixme_id = ""; 
					String fixme_text = ""; 
					Node id_node = null;
					Node lat_node = null;
					Node lon_node = null;
					boolean fixme_found = false;
					
					NodeList level_2_xmlnodes = this_l1_item.getChildNodes();
					int num_l2_xmlnodes = level_2_xmlnodes.getLength();
	
					if ( arg_debug >= Log_Informational_2 )
					{
						System.out.println( "L2 nodes found: " + num_l2_xmlnodes );
					}
	                    
					/* ------------------------------------------------------------------------------------------------------------
					 * OSM nodes items can have both attributes (e.g. "lon", "lat") and tags (XML child nodes) - process the 
					 * attributes first. 
					 * ------------------------------------------------------------------------------------------------------------ */
					if ( this_l1_item.hasAttributes() )
					{
						NamedNodeMap item_attributes = this_l1_item.getAttributes();
						id_node = item_attributes.getNamedItem( "id" );
						lat_node = item_attributes.getNamedItem( "lat" );
						lon_node = item_attributes.getNamedItem( "lon" );

						if ( id_node == null )
						{
							if ( arg_debug >= Log_Informational_1 )
							{
								System.out.println( "No node id found" );
							}
			            }
						else
						{
							if ( arg_debug >= Log_Informational_1 )
							{
								System.out.println( "Node Id: " + id_node.getNodeValue() );
							}
						}

						/* ------------------------------------------------------------------------------------------------------------
						 * We need to make the fixme_id here unique.  Using the full OSM node id here isn't a problem - it is not
						 * too long. 
						 * ------------------------------------------------------------------------------------------------------------ */
						fixme_id = "S FN" + id_node.getNodeValue();
						
						if ( arg_debug >= Log_Informational_2 )
						{
							System.out.println( "fixme_id: " + fixme_id );
						}

						if ( lat_node == null )
						{
							if ( arg_debug >= Log_Informational_1 )
							{
								System.out.println( "No node lat found" );
							}
			            }
						else
						{
							if ( arg_debug >= Log_Informational_1 )
							{
								System.out.println( "Lat: " + lat_node.getNodeValue() );
							}
						}

						if ( lon_node == null )
						{
							if ( arg_debug >= Log_Informational_1 )
							{
								System.out.println( "No node lon found" );
							}
			            }
						else
						{
							if ( arg_debug >= Log_Informational_1 )
							{
								System.out.println( "Lon: " + lon_node.getNodeValue() );
							}
						}
					} // attributes

					/* ------------------------------------------------------------------------------------------------------------
					 * If we have a valid node ID, lat and lon store in hash tables. 
					 * ------------------------------------------------------------------------------------------------------------ */
					if (( id_node  != null ) &&
						( lat_node != null ) &&
						( lon_node != null ))
					{
						osmLatHash.put( id_node.getNodeValue(), lat_node.getNodeValue() );
						osmLonHash.put( id_node.getNodeValue(), lon_node.getNodeValue() );
					}

					/* ------------------------------------------------------------------------------------------------------------
					 * Now process the XML child nodes 
					 * ------------------------------------------------------------------------------------------------------------ */
					for ( int cntr_2 = 0; cntr_2 < num_l2_xmlnodes; ++cntr_2 ) 
					{
						Node this_l2_item = level_2_xmlnodes.item( cntr_2 );
						String l2_item_type = this_l2_item.getNodeName();

						if ( l2_item_type.equals( "tag" ))
						{
							NamedNodeMap item_attributes = this_l2_item.getAttributes();
							Node key_node = item_attributes.getNamedItem( "k" );
							Node value_node = item_attributes.getNamedItem( "v" );
							
							if ( key_node == null )
							{
								System.out.println( "tag/value processing: No key found" );
								/* ------------------------------------------------------------------------------------------------------------
								 * If we haven't found a tag name don't bother looking for a value. 
								 * ------------------------------------------------------------------------------------------------------------ */
							}
							else
							{ // we have at least a key
								if ( arg_debug >= Log_Informational_2 )
								{
									System.out.println( "tag_key: " + key_node.getNodeValue() );
								}

								/* ------------------------------------------------------------------------------------------------------------
								 * We have found a tag name - check the value. 
								 * ------------------------------------------------------------------------------------------------------------ */
								if ( value_node == null )
								{
									System.out.println( "tag/value processing: No value found" );
								}
								else
								{ // we have both a key and a value
									if ( arg_debug >= Log_Informational_2 )
									{
										System.out.println( "tag_value: " + value_node.getNodeValue() );
									}
									
									if ( key_node.getNodeValue().equals( "fixme" ))
									{
										fixme_found = true;
										/* ------------------------------------------------------------------------------------------------------------
										 * Escape any & from in string.  This must be done because MapSource doesn't like raw "&" in comment text.  We 
										 * change to "&amp;" as that's what an ampersand entered on the Garmin keyboard would come through as.
										 * ------------------------------------------------------------------------------------------------------------ */
										fixme_text = replace_all_escape_characters( value_node.getNodeValue() );
									}
								}
							}
						} // tag
						else
						{
							if ( l2_item_type != "#text" )
							{
								System.out.println( "Fixme: we're not currently interested in: " + l2_item_type );
							}
						}
					} // for cntr_2

					/* ------------------------------------------------------------------------------------------------------------
					 * We've processed all attributes and child nodes; write out what we know about this fixme
					 * 
					 *  The symbol used currently defaults to "Shipwreck", but can easily be changed on the command line 
					 *  if required.
					 * ------------------------------------------------------------------------------------------------------------ */
					if ( fixme_found )
					{
						osm_fixmes_found++;
					}

					if (( arg_out_gpx_file != ""   ) &&
						( fixme_found              ))
					{
						myGpxPrintStream.println( "<wpt lat=\"" + lat_node.getNodeValue() + "\" lon=\"" + lon_node.getNodeValue() + "\">" );
						myGpxPrintStream.println( "<name>" + fixme_id + "</name>" );
						myGpxPrintStream.println( "<cmt>" + fixme_text + "</cmt>" );
						myGpxPrintStream.println( "<desc>" + fixme_text + "</desc>" );
						myGpxPrintStream.println( "<sym>" + passed_symbol + "</sym>" );
						myGpxPrintStream.println( "</wpt>" );
					}
					
					if (( arg_out_txt_file != ""   ) &&
						( fixme_found              ))
					{
						myTxtPrintStream.println( fixme_id );
						myTxtPrintStream.println( "==========" );
						myTxtPrintStream.println( fixme_text );
						myTxtPrintStream.println( "" );
					}					
				} // node
				else
				{ // !node
					if ( l1_item_type == "way" )
					{
						String fixme_id = ""; 
						String fixme_text = ""; 
						Node id_node = null;
						String lat_string = "";
						String lon_string = "";
						boolean fixme_found = false;

						NodeList level_2_xmlnodes = this_l1_item.getChildNodes();
						int num_l2_xmlnodes = level_2_xmlnodes.getLength();
		
						if ( arg_debug >= Log_Informational_2 )
						{
							System.out.println( "L2 nodes found: " + num_l2_xmlnodes );
						}
		                    
						/* ------------------------------------------------------------------------------------------------------------
						 * OSM way items can have an attribute ("id") and tags (XML child nodes) - process the 
						 * attributes first. 
						 * ------------------------------------------------------------------------------------------------------------ */
						if ( this_l1_item.hasAttributes() )
						{
							NamedNodeMap item_attributes = this_l1_item.getAttributes();
							id_node = item_attributes.getNamedItem( "id" );

							if ( id_node == null )
							{
								if ( arg_debug >= Log_Informational_1 )
								{
									System.out.println( "No way id found" );
								}
				            }
							else
							{
								if ( arg_debug >= Log_Informational_1 )
								{
									System.out.println( "Way Id: " + id_node.getNodeValue() );
								}
							}

							/* ------------------------------------------------------------------------------------------------------------
							 * We need to make the fixme_id here unique.  Using the full OSM way id here isn't a problem - it is not
							 * too long. 
							 * ------------------------------------------------------------------------------------------------------------ */
							fixme_id = "S FW" + id_node.getNodeValue();
							
							if ( arg_debug >= Log_Informational_2 )
							{
								System.out.println( "fixme_id: " + fixme_id );
							}
						} // attributes

						/* ------------------------------------------------------------------------------------------------------------
						 * Now process the XML child nodes 
						 * ------------------------------------------------------------------------------------------------------------ */
						for ( int cntr_2 = 0; cntr_2 < num_l2_xmlnodes; ++cntr_2 ) 
						{
							Node this_l2_item = level_2_xmlnodes.item( cntr_2 );
							String l2_item_type = this_l2_item.getNodeName();

							if ( l2_item_type.equals( "nd" ))
							{
								NamedNodeMap item_attributes = this_l2_item.getAttributes();
								Node node_ref = item_attributes.getNamedItem( "ref" );

								if ( node_ref == null )
								{
									System.out.println( "tag/value processing: No key found" );
									/* ------------------------------------------------------------------------------------------------------------
									 * If we haven't found a tag name don't bother looking for a value. 
									 * ------------------------------------------------------------------------------------------------------------ */
								}
								else
								{ 
									/* ------------------------------------------------------------------------------------------------------------
									 * We have a node ref - can we look it up in the hash tables to get lat and lon?  
									 * 
									 * There's no error checking here, so errors retrieved from Overpass will probably cause errors in Mapsource 
									 * due to blank lat or lon strings.  Zero-node ways may also cause a problem.
									 * ------------------------------------------------------------------------------------------------------------ */
									lat_string = osmLatHash.get(node_ref.getNodeValue());
									lon_string = osmLonHash.get(node_ref.getNodeValue());
								}
							}
							else
							{ // !nd
								if ( l2_item_type.equals( "tag" ))
								{
									NamedNodeMap item_attributes = this_l2_item.getAttributes();
									Node key_node = item_attributes.getNamedItem( "k" );
									Node value_node = item_attributes.getNamedItem( "v" );
									
									if ( key_node == null )
									{
										System.out.println( "tag/value processing: No key found" );
										/* ------------------------------------------------------------------------------------------------------------
										 * If we haven't found a tag name don't bother looking for a value. 
										 * ------------------------------------------------------------------------------------------------------------ */
									}
									else
									{ // we have at least a key
										if ( arg_debug >= Log_Informational_2 )
										{
											System.out.println( "tag_key: " + key_node.getNodeValue() );
										}

										/* ------------------------------------------------------------------------------------------------------------
										 * We have found a tag name - check the value. 
										 * ------------------------------------------------------------------------------------------------------------ */
										if ( value_node == null )
										{
											System.out.println( "tag/value processing: No value found" );
										}
										else
										{ // we have both a key and a value
											if ( arg_debug >= Log_Informational_2 )
											{
												System.out.println( "tag_value: " + value_node.getNodeValue() );
											}
											
											if ( key_node.getNodeValue().equals( "fixme" ))
											{
												fixme_found = true;
												/* ------------------------------------------------------------------------------------------------------------
												 * Escape any & from in string.  This must be done because MapSource doesn't like raw "&" in comment text.  We 
												 * change to "&amp;" as that's what an ampersand entered on the Garmin keyboard would come through as.
												 * ------------------------------------------------------------------------------------------------------------ */
												fixme_text = replace_all_escape_characters( value_node.getNodeValue() );
											}
										}
									}
								} //tag
								else
								{ // !tag
									if ( l2_item_type != "#text" )
									{
										System.out.println( "Fixme: we're not currently interested in: " + l2_item_type );
									}
								} //!tag
							} // !nd
						} // for cntr_2

						/* ------------------------------------------------------------------------------------------------------------
						 * We've processed all attributes and child nodes; write out what we know about this fixme
						 * 
						 *  The symbol used currently defaults to "Shipwreck", but can easily be changed on the command line 
						 *  if required.
						 * ------------------------------------------------------------------------------------------------------------ */
						if ( fixme_found )
						{
							osm_fixmes_found++;
						}

						if (( arg_out_gpx_file != ""   ) &&
							( fixme_found              ))
						{
							myGpxPrintStream.println( "<wpt lat=\"" + lat_string + "\" lon=\"" + lon_string + "\">" );
							myGpxPrintStream.println( "<name>" + fixme_id + "</name>" );
							myGpxPrintStream.println( "<cmt>" + fixme_text + "</cmt>" );
							myGpxPrintStream.println( "<desc>" + fixme_text + "</desc>" );
							myGpxPrintStream.println( "<sym>" + passed_symbol + "</sym>" );
							myGpxPrintStream.println( "</wpt>" );
						}
						
						if (( arg_out_txt_file != ""   ) &&
							( fixme_found              ))
						{
							myTxtPrintStream.println( fixme_id );
							myTxtPrintStream.println( "==========" );
							myTxtPrintStream.println( fixme_text );
							myTxtPrintStream.println( "" );
						}					
					} // way
					else
					{
						if ( l1_item_type == "relation" )
						{
							/* ------------------------------------------------------------------------------------------------------------
							 * There's no support for extracting fixmes for relations currently - relations tend to be large 
							 * ------------------------------------------------------------------------------------------------------------ */
						}
						else
						{
							if ( l1_item_type != "#text" )
							{
								if ( arg_debug >= Log_Informational_1 )
								{
									System.out.println( "Node " + cntr_1 + ": " + l1_item_type );
								}
							}
						}
					}
				} // !node
			} // for L1 nodes

			if ( arg_debug >= Log_Serious )
			{
				System.out.println( "Fixmes found: " + osm_fixmes_found );
			}
		}
		else
		{
			if ( arg_debug >= Log_Error )
			{
				System.out.println( "XML Parsing Error - element node expected" );
			}
		}
	}
	
	
	private static void process_notes_xml( Node root_node, String passed_display_name, String passed_uid, String passed_symbol )
	{
		int osm_notes_found = 0;
	
		if ( root_node.getNodeType() == Node.ELEMENT_NODE ) 
		{
			NodeList level_1_xmlnodes = root_node.getChildNodes();
			int num_l1_xmlnodes = level_1_xmlnodes.getLength();
	
			if ( arg_debug >= Log_Informational_2 )
			{
				System.out.println( "Notes L1 nodes found: " + num_l1_xmlnodes );
			}
	
			/* ------------------------------------------------------------------------------------------------------------
			 * Iterate through the notes 
			 * ------------------------------------------------------------------------------------------------------------ */
			for ( int cntr_1 = 0; cntr_1 < num_l1_xmlnodes; ++cntr_1 ) 
			{
				Node this_l1_item = level_1_xmlnodes.item( cntr_1 );
				String l1_item_type = this_l1_item.getNodeName();
	
				if ( l1_item_type.equals( "note" ))
				{
					String note_id = ""; 
					String comment_action = ""; 
					String comment_open_text = ""; 
					String comment_temp_text = ""; // FRANCO
					Node lat_node = null;
					Node lon_node = null;
					boolean display_name_matches = false;
					boolean uid_matches = false;
					
					if ( passed_display_name.equals( "" ))
					{
						display_name_matches = true;
						
						if ( arg_debug >= Log_Informational_2 )
						{
							System.out.println( "Blank display name; everything matches" );
						}
					}
					
					if ( passed_uid.equals( "" ))
					{
						uid_matches = true;
						
						if ( arg_debug >= Log_Informational_2 )
						{
							System.out.println( "Blank uid; everything matches" );
						}
					}
					
					NodeList level_2_xmlnodes = this_l1_item.getChildNodes();
					int num_l2_xmlnodes = level_2_xmlnodes.getLength();
	
					if ( arg_debug >= Log_Informational_2 )
					{
						System.out.println( "L2 nodes found: " + num_l2_xmlnodes );
					}
	                    
					/* ------------------------------------------------------------------------------------------------------------
					 * Items can have both attributes (e.g. "lon", "lat") and tags (XML child nodes) - process the attributes first. 
					 * ------------------------------------------------------------------------------------------------------------ */
					if ( this_l1_item.hasAttributes() )
					{
						NamedNodeMap item_attributes = this_l1_item.getAttributes();
						lat_node = item_attributes.getNamedItem( "lat" );
						lon_node = item_attributes.getNamedItem( "lon" );
						
						if ( lat_node == null )
						{
							if ( arg_debug >= Log_Informational_1 )
							{
								System.out.println( "No note lat found" );
							}
			            }
						else
						{
							if ( arg_debug >= Log_Informational_1 )
							{
								System.out.println( "Lat: " + lat_node.getNodeValue() );
							}
						}

						if ( lon_node == null )
						{
							if ( arg_debug >= Log_Informational_1 )
							{
								System.out.println( "No note lon found" );
							}
			            }
						else
						{
							if ( arg_debug >= Log_Informational_1 )
							{
								System.out.println( "Lon: " + lon_node.getNodeValue() );
							}
						}
					} // attributes
					
					for ( int cntr_2 = 0; cntr_2 < num_l2_xmlnodes; ++cntr_2 ) 
					{
						Node this_l2_item = level_2_xmlnodes.item( cntr_2 );
						String l2_item_type = this_l2_item.getNodeName();

						if ( l2_item_type.equals( "id" ))
						{
							/* ------------------------------------------------------------------------------------------------------------
							 * The note ID, which becomes the waypoint name on the device, is hardcoded here as "S N" + the OSM note
							 * number.  The OSM note number is padded out to something like "<name>S N0278181</name>"
							 * 
							 * This will become an issue when OSM note numbers go over 9,999,999.  Currently OSM is on target to have a 
							 * million notes in 6 years, so that's still some way off.
							 * ------------------------------------------------------------------------------------------------------------ */
							note_id = "S N" + String.format( "%07d", Integer.parseInt( myGetNodeValue( this_l2_item )));
							
							if ( arg_debug >= Log_Informational_2 )
							{
								System.out.println( "note_id: " + note_id );
							}
						}
						else
						{
							// We're not interested in url, reopen_url, date_created, status, date_closed
							
							if ( l2_item_type.equals( "comments" ))
							{
								NodeList level_3_xmlnodes = this_l2_item.getChildNodes();
								int num_l3_xmlnodes = level_3_xmlnodes.getLength();
								
								if ( arg_debug >= Log_Informational_2 )
								{
									System.out.println( "L3 Child nodes found: " + num_l3_xmlnodes );
								}
								
								for ( int cntr_3 = 0; cntr_3 < num_l3_xmlnodes; ++cntr_3 ) 
								{
									Node this_l3_item = level_3_xmlnodes.item( cntr_3 );
									String l3_item_type = this_l3_item.getNodeName();
									
									if ( l3_item_type.equals( "comment" ))
									{
										NodeList level_4_xmlnodes = this_l3_item.getChildNodes();
										int num_l4_xmlnodes = level_4_xmlnodes.getLength();

										if ( arg_debug >= Log_Informational_2 )
										{
											System.out.println( "L4 Child nodes found: " + num_l4_xmlnodes );
										}
										
										for ( int cntr_4 = 0; cntr_4 < num_l4_xmlnodes; ++cntr_4 ) 
										{
											Node this_l4_item = level_4_xmlnodes.item( cntr_4 );
											String l4_item_type = this_l4_item.getNodeName();

											if ( l4_item_type.equals( "date" ))
											{
												if ( arg_debug >= Log_Informational_2 )
												{
													System.out.println( "Comment date: " + myGetNodeValue( this_l4_item ) );
												}
											}
											else
											{
												if ( l4_item_type.equals( "action" ))
												{
													comment_action = myGetNodeValue( this_l4_item );
													
													if ( arg_debug >= Log_Informational_2 )
													{
														System.out.println( "Comment action: " + myGetNodeValue( this_l4_item ) );
													}
												}
												else
												{
													if ( l4_item_type.equals( "text" ))
													{
														/* ------------------------------------------------------------------------------------------------------------
														 * Previously we only processed the "opening" text as we were (prior to modern Garmin devices) limited to 30 
														 * characters.  Now we process all text from all comment_actions (e.g. "commented" as well).
														 * ------------------------------------------------------------------------------------------------------------ */
													  comment_temp_text = myGetNodeValue( this_l4_item );
															
														/* ------------------------------------------------------------------------------------------------------------
														 * Escape any & from in string.  This must be done because MapSource doesn't like raw "&" in comment text.  We 
														 * change to "&amp;" as that's what an ampersand entered on the Garmin keyboard would come through as.
														 * ------------------------------------------------------------------------------------------------------------ */
														comment_temp_text = replace_all_escape_characters( comment_temp_text );
															
														if (comment_temp_text.length() > 0) // FRANCO
														{
															if (comment_open_text.length() > 0)
															{
																comment_open_text = comment_open_text + "\n";
															}

															if (comment_action.equals( "opened" ))
															{
																comment_open_text = comment_open_text + comment_temp_text;
															}
															else
															{
																comment_open_text = comment_open_text + "(" + comment_action + ") " + comment_temp_text;
															}
														}
													
														if ( arg_debug >= Log_Informational_2 )
														{
															System.out.println( "Comment text: " + myGetNodeValue( this_l4_item ) );
														}
													} // text
													else
													{
														if ( l4_item_type.equals( "user" ))
														{
															if ( passed_display_name.equals( myGetNodeValue( this_l4_item )))
															{
																display_name_matches = true;
																
																if ( arg_debug >= Log_Informational_2 )
																{
																	System.out.println( "user: " + myGetNodeValue( this_l4_item ) + " matches" );
																}
															}
															else
															{
																if ( arg_debug >= Log_Informational_2 )
																{
																	System.out.println( "user: " + myGetNodeValue( this_l4_item ) + " does not match" );
																}
															}
														} // user
														else
														{
															if ( l4_item_type.equals( "uid" ))
															{
																if ( passed_uid.equals( myGetNodeValue( this_l4_item )))
																{
																	uid_matches = true;
																	
																	if ( arg_debug >= Log_Informational_2 )
																	{
																		System.out.println( "uid: " + myGetNodeValue( this_l4_item ) + " matches" );
																	}
																}
																else
																{
																	if ( arg_debug >= Log_Informational_2 )
																	{
																		System.out.println( "uid: " + myGetNodeValue( this_l4_item ) + " does not match" );
																	}
																}
															} // uid
															else
															{
																if ( !l4_item_type.equals( "#text" ))
																{
																	if ( arg_debug >= Log_Informational_1 )
																	{
																		System.out.println( "Comment: we're not interested in: " + l4_item_type );
																	}
																} // something else
															}
														}
													}
												}
											}
										}
									}
								}
							}
							else
							{
								if ( arg_debug >= Log_Informational_1 )
								{
									if ( l2_item_type != "#text" )
									{
										System.out.println( "Note: we're not interested in: " + l2_item_type );
									}
								}
							}
						}
					} // for cntr_2

					/* ------------------------------------------------------------------------------------------------------------
					 * We've processed all attributes and child nodes; write out what we know about this note
					 * 
					 *  The symbol used currently defaults to "Shipwreck", but can easily be changed on the command line 
					 *  if required.
					 * ------------------------------------------------------------------------------------------------------------ */
					if (( display_name_matches     ) &&
						( uid_matches              ))
					{
						osm_notes_found++;
					}

					if (( arg_out_gpx_file != ""   ) &&
						( display_name_matches     ) &&
						( uid_matches              ))
					{
						myGpxPrintStream.println( "<wpt lat=\"" + lat_node.getNodeValue() + "\" lon=\"" + lon_node.getNodeValue() + "\">" );
						myGpxPrintStream.println( "<name>" + note_id + "</name>" );
						myGpxPrintStream.println( "<cmt>" + comment_open_text + "</cmt>" );
						myGpxPrintStream.println( "<desc>" + comment_open_text + "</desc>" );
						myGpxPrintStream.println( "<sym>" + passed_symbol + "</sym>" );
						myGpxPrintStream.println( "</wpt>" );
					}
					
					if (( arg_out_txt_file != ""   ) &&
						( display_name_matches     ) &&
						( uid_matches              ))
					{
						myTxtPrintStream.println( note_id );
						myTxtPrintStream.println( "==========" );
						myTxtPrintStream.println( comment_open_text );
						myTxtPrintStream.println( "" );
					}					
				} // note
				else
				{ // !note
					if ( l1_item_type != "#text" )
					{
						if ( arg_debug >= Log_Informational_1 )
						{
							System.out.println( "Node " + cntr_1 + ": " + l1_item_type );
						}
					}
				} // !note
			} // for L1 nodes

			if ( arg_debug >= Log_Serious )
			{
				System.out.println( "Notes found: " + osm_notes_found );
			}
		}	
		else
		{
			if ( arg_debug >= Log_Error )
			{
				System.out.println( "XML Parsing Error - element node expected" );
			}
		}
	}


/* ------------------------------------------------------------------------------------------------------------
 * Any strings sent to a Garmin (via MapSource or GPSBabel) must have ampersands escaped so that "&" is sent
 * as "&amp;" 
 * 
 * A future option may be to use e.g.
 * http://commons.apache.org/proper/commons-lang/javadocs/api-2.6/org/apache/commons/lang/StringEscapeUtils.html
 * 
 * Issue 1 (https://github.com/SomeoneElseOSM/Notes01/issues/1) has spotted that other characters cause problems
 * too.  I therefore need to follow: 
 * http://stackoverflow.com/questions/1091945/what-characters-do-i-need-to-escape-in-xml-documents
 * and generalise the replacement of problem characters.
 * ------------------------------------------------------------------------------------------------------------ */
	static String replace_all_escape_characters( String comment_open_text ) 
	{
		/* ------------------------------------------------------------------------------------------------------------
		 * Escape & first so that we don't replace any of the & that we introduce in out replacements.
		 * ------------------------------------------------------------------------------------------------------------ */
		char problem_char = '&';
		String problem_replacement = "&amp;";
		String result_text = replace_one_escape_character( comment_open_text, problem_char, problem_replacement );

		problem_char = '"';
		problem_replacement = "&quot;";
		result_text = replace_one_escape_character( result_text, problem_char, problem_replacement );

		problem_char = '<';
		problem_replacement = "&lt;";
		result_text = replace_one_escape_character( result_text, problem_char, problem_replacement );

		problem_char = '\'';
		problem_replacement = "&apos;";
		result_text = replace_one_escape_character( result_text, problem_char, problem_replacement );

		problem_char = '>';
		problem_replacement = "&gt;";
		result_text = replace_one_escape_character( result_text, problem_char, problem_replacement );

		return result_text;
	}
	
	
	static String replace_one_escape_character( String comment_open_text, char problem_char, String problem_replacement )
	{
		String result_text = comment_open_text;

		int i = result_text.indexOf( problem_char );
		
		while ( i != -1 )
		{
			if ( i == 0 )
			{
				result_text = problem_replacement + result_text.substring( i+1 ); 
			}
			else
			{
				result_text = result_text.substring( 0, i ) + problem_replacement + result_text.substring( i+1 ); 
			}

/* ------------------------------------------------------------------------------------------------------------
 * Start searching for any text after the "&amp;" that we have just added.
 * ------------------------------------------------------------------------------------------------------------ */
			i = result_text.indexOf( problem_char, i + problem_replacement.length() );
		}

		return result_text;
	}
	
    static void process_fixmes_url_common ( URL passed_url, String passed_symbol ) throws Exception
    {
	if ( arg_debug >= Log_Informational_2 )
	    {
		System.out.println( "passed_url: " + passed_url );
		System.out.println( "passed_symbol: " + passed_symbol );
	    }
		
	InputStreamReader input;
		
	URLConnection urlConn = passed_url.openConnection();
	urlConn.setDoInput( true );
	urlConn.setDoOutput( false );
	urlConn.setUseCaches( false );

	try
	    {
		// timeouts caught below
		input = new InputStreamReader( urlConn.getInputStream() );

		char[] data = new char[ 256 ];
		int len = 0;
		StringBuffer sb = new StringBuffer();		
	
		while ( -1 != ( len = input.read( data, 0, 255 )) )
		    {
			sb.append( new String( data, 0, len ));
		    }   
	
		DocumentBuilderFactory myFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder myBuilder = myFactory.newDocumentBuilder();
		ByteArrayInputStream inputStream = new ByteArrayInputStream( sb.toString().getBytes( "UTF-8" ));
	
		Document myDocument = myBuilder.parse( inputStream );
		Element rootElement = myDocument.getDocumentElement();
		process_fixmes_xml( rootElement, passed_symbol );
	
		input.close();
	    }
	catch( Exception ex )
	    {
		if ( arg_debug >= Log_Serious )
		    {
			System.out.println( "Error connecting to Overpass API for fixmes: " + ex.getMessage() );
		    }
	    }
    }

    static void process_notes_url_common ( URL passed_url, String passed_display_name, String passed_uid, String passed_symbol ) throws Exception
    {
	if ( arg_debug >= Log_Informational_2 )
	    {
		System.out.println( "passed_url: " + passed_url );
		System.out.println( "passed_display_name: " + passed_display_name );
		System.out.println( "passed_uid: " + passed_uid );
		System.out.println( "passed_symbol: " + passed_symbol );
	    }
		
	InputStreamReader input;
	
	URLConnection urlConn = passed_url.openConnection();
	urlConn.setDoInput( true );
	urlConn.setDoOutput( false );
	urlConn.setUseCaches( false );
	
	try
	    {
		// timeouts caught below
		input = new InputStreamReader( urlConn.getInputStream() );
	
		char[] data = new char[ 256 ];
		int len = 0;
		StringBuffer sb = new StringBuffer();		
	
		while ( -1 != ( len = input.read( data, 0, 255 )) )
		    {
			sb.append( new String( data, 0, len ));
		    }   
	
		DocumentBuilderFactory myFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder myBuilder = myFactory.newDocumentBuilder();
		ByteArrayInputStream inputStream = new ByteArrayInputStream( sb.toString().getBytes( "UTF-8" ));
		
		Document myDocument = myBuilder.parse( inputStream );
		Element rootElement = myDocument.getDocumentElement();
		process_notes_xml( rootElement, passed_display_name, passed_uid, passed_symbol );
	
		input.close();
	    }
	catch( Exception ex )
	    {
		if ( arg_debug >= Log_Serious )
		    {
			System.out.println( "Error connecting to API for notes: " + ex.getMessage() );
		    }
	    }
	}

	
	static void process_notes_file ( String passed_display_name, String passed_uid, String passed_symbol, String passed_min_lat_string, String passed_min_lon_string, String passed_max_lat_string, String passed_max_lon_string ) throws Exception
	{
	    DocumentBuilderFactory myFactory = DocumentBuilderFactory.newInstance();
	    DocumentBuilder myBuilder = myFactory.newDocumentBuilder();
	    InputStream inputStream = new FileInputStream( myFile );
	
	    Document myDocument = myBuilder.parse( inputStream );
	    Element rootElement = myDocument.getDocumentElement();
	    process_notes_xml( rootElement, passed_display_name, passed_uid, passed_symbol );
	}
	
	
	static void process_fixmes( String passed_symbol, String passed_min_lat_string, String passed_min_lon_string, String passed_max_lat_string, String passed_max_lon_string ) throws Exception 
	{
		if ( arg_debug >= Log_Low_Routine_Start )
		{
			System.out.println( "process_all_fixmes" );
		}

		URL url;
		/* ------------------------------------------------------------------------------
		 * The string below determines what data we ask for from Overpass. 
		 * "xapi?node" would mean "just nodes"; "xapi?*" means nodes, ways and relations.  
		 * ------------------------------------------------------------------------------ */
		url = new URL( actual_overpass_path + "xapi?*[bbox=" + passed_min_lon_string + "," + passed_min_lat_string + "," + passed_max_lon_string + "," + passed_max_lat_string + "][fixme=*]" );
		process_fixmes_url_common( url, passed_symbol );
	}
	
	
	static void process_notes( String passed_display_name, String passed_uid, String passed_closed, String passed_limit, String passed_symbol, String passed_min_lat_string, String passed_min_lon_string, String passed_max_lat_string, String passed_max_lon_string ) throws Exception 
	{
		if ( arg_debug >= Log_Low_Routine_Start )
		{
			System.out.println( "process_all_notes" );
		}

		URL url;
		url = new URL( actual_api_path + "notes?closed=0&closed=" + passed_closed + "&limit=" + passed_limit + "&bbox=" + passed_min_lon_string + "," + passed_min_lat_string + "," + passed_max_lon_string + "," + passed_max_lat_string );
		process_notes_url_common( url, passed_display_name, passed_uid, passed_symbol );
	}
	
	static String get_line_param( String passed_param, String passed_in_line )
	{
		int param_start = 0;
		int param_end = 0;
		String line_param = "";
		
		try
		{
			param_start = passed_in_line.indexOf( passed_param );
			
			if ( param_start != -1 )
			{
				if ( passed_in_line.substring(( param_start + passed_param.length() ), ( param_start + passed_param.length() + 1 )).equals( "\"" ))
				{
					param_start++;
					param_end = passed_in_line.indexOf( "\"", ( param_start + passed_param.length() ));
	
					if ( param_end == -1 )
					{
						param_end = passed_in_line.length() + 1;
					}
	
					line_param = passed_in_line.substring( ( param_start + passed_param.length() ), param_end );
				}
				else
				{
					param_end = passed_in_line.indexOf( " ", ( param_start + passed_param.length() ));
					
					if ( param_end == -1 )
					{
						param_end = passed_in_line.length();
					}
					
					line_param = passed_in_line.substring( ( param_start + passed_param.length() ), param_end );
				}
			}
		}
		catch( Exception ex )
		{
			System.out.println( "Error parsing param: " + passed_in_line );
		}
		
		return line_param;
	}

	
/* ------------------------------------------------------------------------------
 * This is designed to look for the next comma, but that can be either "," or
 * "%2C" depending on where the bbox came from.  There are two methods - they
 * use indexOf from the start or from a given position.
 * ------------------------------------------------------------------------------ */
	static int return_next_comma( String passed_bbox )
	{
		int comma_pos = -1;
		
		comma_pos = passed_bbox.indexOf( "," );
		
		if ( comma_pos < 0  )
		{
			comma_pos = passed_bbox.indexOf( "%2C" );
		}
		
		return comma_pos;
	}

	static int return_next_comma( String passed_bbox, int start_pos )
	{
		int comma_pos = -1;
		
		comma_pos = arg_bbox.indexOf( ",", start_pos );
		
		if ( comma_pos < 0  )
		{
			comma_pos = passed_bbox.indexOf( "%2C", start_pos );
		}

		return comma_pos;
	}

	
/* ------------------------------------------------------------------------------
 * Data passed on the command line:
 * 
 * param_bbox = "-bbox=";
 * param_dev = "-notes_dev";
 * param_limit = "-limit="; defaults to 100
 * param_closed = "-closed="; defaults to 0 days (unlike the API)
 * param_input = "-input="; for testing of a previously wget-obtained file.
 * param_output_gpx = "-output_gpx="; for the output_gpx GPX file
 * param_output_txt = "-output_txt="; for the output_gpx TXT file
 * param_debug = "-debug="; 
 * param_display_name = "-display_name=";
 * param_uid = "-uid=";
 * ------------------------------------------------------------------------------ */
/**
 * @param args
 */
    public static void main(String[] args) throws Exception 
    {
	String arg_display_name = "";
	String arg_uid = "";
		
	arg_debug = Log_Serious;
		
	for ( int i=0; i<args.length; i++ )
	    {
		if ( args[i].length() >= 2)
		    {
			if ( arg_debug >= Log_Informational_2 )
			    {
				System.out.println( "arg: " + i );
				System.out.println( "arg length: " + args[i].length() );
			    }
				
/* ------------------------------------------------------------------------------
 * Debug level
 * 
 * Parameters are processed in order along the command line.  It makes sense
 * to pass "debug" as the first parameter, if required, as it will then be in 
 * effect during the processing of the other parameters.
 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_debug ))
			    {
				try
				    {
					arg_debug = Integer.valueOf( args[i].substring( param_debug.length() ));
				    }
				catch( Exception ex )
				    {
/* ------------------------------------------------------------------------------
 * Any failure above just means that we leave arg_debug at Log_Serious
 * ------------------------------------------------------------------------------ */
				    }
					
				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "arg_debug: " + arg_debug );
				    }
			    } // -debug
				
			/* ------------------------------------------------------------------------------
			 * Should we fetch OSM Notes?
			 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_do_notes ))
			    {	
				arg_do_notes = true;

				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "We will process OSM notes" );
				    }
			    } // -do_notes
				
			/* ------------------------------------------------------------------------------
			 * Should we user the dev API to fetch Overpass fixmes?
			 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_do_fixmes ))
			    {	
				arg_do_fixmes = true;
					
				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "We will process Overpass fixmes" );
				    }
			    } // -do_fixmes
				
/* ------------------------------------------------------------------------------
 * Input file
 * 
 * If specified Notes01 will read from an input file rather than fetching from an API.
 * Mainly designed to be used for testing.
 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_notes_input ))
			    {	
				arg_in_file = args[i].substring( param_notes_input.length() );

				try
				    {
					myFile = new File( arg_in_file );
				    }
				catch( Exception ex )
				    {
/* ------------------------------------------------------------------------------
 * If there's an error opening the input file, don't pretend that it wasn't 
 * specified on the command line.
 * ------------------------------------------------------------------------------ */
					arg_in_file = "!file";
						
					if ( arg_debug >= Log_Informational_1 )
					    {
						System.out.println( "Error opening input file: " + ex.getMessage() );
					    }
				    }
					
				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "arg_in_file: " + arg_in_file );
					System.out.println( "arg_in_file length: " + arg_in_file.length() );
				    }
			    } // -input
				
/* ------------------------------------------------------------------------------
 * output_gpx file
 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_output_gpx ))
			    {	
				arg_out_gpx_file = args[i].substring( param_output_gpx.length() );

				try
				    {
					myoutput_gpxStream = new FileOutputStream( arg_out_gpx_file );
					myGpxPrintStream = new PrintStream( myoutput_gpxStream );
				    }
				catch( Exception ex )
				    {
					arg_out_gpx_file = "";
						
					if ( arg_debug >= Log_Informational_1 )
					    {
						System.out.println( "Error opening output_gpx file: " + ex.getMessage() );
					    }
				    }
					
				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "arg_out_gpx_file: " + arg_out_gpx_file );
					System.out.println( "arg_out_gpx_file length: " + arg_out_gpx_file.length() );
				    }
			    } // -output_gpx
				
/* ------------------------------------------------------------------------------
 * output_txt file
 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_output_txt ))
			    {	
				arg_out_txt_file = args[i].substring( param_output_txt.length() );

				try
				    {
					myoutput_txtStream = new FileOutputStream( arg_out_txt_file );
					myTxtPrintStream = new PrintStream( myoutput_txtStream );
				    }
				catch( Exception ex )
				    {
					arg_out_txt_file = "";
						
					if ( arg_debug >= Log_Informational_1 )
					    {
						System.out.println( "Error opening output_txt file: " + ex.getMessage() );
					    }
				    }
					
				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "arg_out_txt_file: " + arg_out_txt_file );
					System.out.println( "arg_out_txt_file length: " + arg_out_txt_file.length() );
				    }
			    } // -output_txt
								
/* ------------------------------------------------------------------------------
 * The user that we're interested in changesets for - display name
 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_display_name ))
			    {	
				arg_display_name = args[i].substring( param_display_name.length() );
					
				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "arg_display_name: " + arg_display_name );
					System.out.println( "arg_display_name length: " + arg_display_name.length() );
				    }
			    } // -display_name
				
/* ------------------------------------------------------------------------------
 * The user that we're interested in changesets for - uid
 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_notes_uid ))
			    {	
				arg_uid = args[i].substring( param_notes_uid.length() );
				
				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "arg_uid: " + arg_uid );
					System.out.println( "arg_uid length: " + arg_uid.length() );
				    }
			    } // -uid
				
/* ------------------------------------------------------------------------------
 * Should we user the dev API to fetch OSM Notes?
 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_notes_dev ))
			    {	
				actual_api_path = dev_api_path;
				
				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "Dev server will be used" );
				    }
			    } // -notes_dev
				
/* ------------------------------------------------------------------------------
 * Should we return closed notes, and if so how many days old?
 * Our default for this is 0 (don't return closed notes) which differs from the
 * API default of 7.
 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_notes_closed ))
			    {	
				try
				    {
					arg_closed = args[i].substring( param_notes_closed.length() );
				    }
				catch( Exception ex )
				    {
/* ------------------------------------------------------------------------------
 * Any failure above just means that we leave arg_limit at our default of "0"
 * ------------------------------------------------------------------------------ */
				    }
				
				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "arg_closed: [" + arg_closed + "]" );
				    }
			    } // -closed
				
/* ------------------------------------------------------------------------------
 * Limit the total number of notes returned.  If unset up to 100 are returned
 * ( which is also the API default). 
 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_notes_limit ))
			    {	
				try
				    {
					arg_limit = args[i].substring( param_notes_limit.length() );
				    }
				catch( Exception ex )
				    {
/* ------------------------------------------------------------------------------
 * Any failure above just means that we leave arg_limit at the default of "100"
 * ------------------------------------------------------------------------------ */
				    }
					
				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "arg_limit: [" + arg_limit + "]" );
				    }
			    } // -limit
				
/* ------------------------------------------------------------------------------
 * What Garmin symbol should be used?  If unset the default "Shipwreck" is used.
 * Note that Garmin symbols with spaces in aren't supported yet.
 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_notes_symbol ))
			    {	
				try
				    {
					arg_symbol = args[i].substring( param_notes_symbol.length() );
				    }
				catch( Exception ex )
				    {
/* ------------------------------------------------------------------------------
 * Any failure above just means that we leave arg_symbol at the default 
 * of "Shipwreck"
 * ------------------------------------------------------------------------------ */
				    }
					
				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "arg_symbol: [" + arg_symbol + "]" );
				    }
			    } // -symbol
			
/* ------------------------------------------------------------------------------
 * Which overpass API should we use?
 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_fixmes_api ))
			    {	
				try
				    {
					arg_fixmes_api = args[i].substring( param_fixmes_api.length() );
				    }
				catch( Exception ex )
				    {
/* ------------------------------------------------------------------------------
 * Any failure above just means that we leave arg_fixmes_api at the default of "de"
 * ------------------------------------------------------------------------------ */
				    }

				if ( arg_fixmes_api.equalsIgnoreCase( "de" ))
				    {
					actual_overpass_path = de_overpass_path;
				    }

				if ( arg_fixmes_api.equalsIgnoreCase( "ru" ))
				    {
					actual_overpass_path = ru_overpass_path;
				    }

				if ( arg_fixmes_api.equalsIgnoreCase( "fr" ))
				    {
					actual_overpass_path = fr_overpass_path;
				    }

				if ( arg_fixmes_api.equalsIgnoreCase( "ch" ))
				    {
					actual_overpass_path = ch_overpass_path;
				    }

				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "arg_fixmes_api: [" + arg_fixmes_api + "]" );
					System.out.println( "actual_overpass_path: [" + actual_overpass_path + "]" );
				    }
			    } // which api to use for fixmes
							
/* ------------------------------------------------------------------------------
 * A bounding box that we're interesting in fetching notes from.
 * 
 * Unlike with "Changeset1", this is passed to the API, although we still do
 * parse parameters for the correct number of commas here.
 * ------------------------------------------------------------------------------ */
			if ( args[i].startsWith( param_bbox ))
			    {	
				arg_bbox = args[i].substring( param_bbox.length() );
					
				if ( arg_debug >= Log_Informational_2 )
				    {
					System.out.println( "arg_bbox: " + arg_bbox );
					System.out.println( "arg_bbox length: " + arg_bbox.length() );
				    }
					
				int comma_pos = return_next_comma( arg_bbox );
				int old_comma_pos = 0;
					
				if ( comma_pos > 0 )
				    { // found min lon
					arg_min_lon_string = arg_bbox.substring( 0, comma_pos );

/* ------------------------------------------------------------------------------
 * Commas are one character long,  the other sequence we match (%2C) is 3.  We
 * use comma_pos to start searching for the start of the next string, so if we've 
 * found %2C need to shuffle 2 to the right.
 * ------------------------------------------------------------------------------ */
					if ( !arg_bbox.substring( comma_pos, comma_pos+1 ).equals( "," ))
					    {
						comma_pos = comma_pos + 2;
					    }
						
					if ( arg_debug >= Log_Informational_1 )
					    {
						System.out.println( "arg_min_lon: " + arg_min_lon_string );
					    }
						
					old_comma_pos = comma_pos;
					comma_pos = return_next_comma( arg_bbox, comma_pos+1 );

					if ( comma_pos > 0 )
					    { // found min lat
						arg_min_lat_string = arg_bbox.substring( old_comma_pos+1, comma_pos );
							
						if ( !arg_bbox.substring( comma_pos, comma_pos+1 ).equals( "," ))
						    {
							comma_pos = comma_pos + 2;
						    }

						if ( arg_debug >= Log_Informational_1 )
						    {
							System.out.println( "arg_min_lat: " + arg_min_lat_string );
						    }

						old_comma_pos = comma_pos;
						comma_pos = return_next_comma( arg_bbox, comma_pos+1 );

						if ( comma_pos > 0 )
						    { // found max lon; what's left must be max lat
							arg_max_lon_string = arg_bbox.substring( old_comma_pos+1, comma_pos );
								
							if ( !arg_bbox.substring( comma_pos, comma_pos+1 ).equals( "," ))
							    {
								comma_pos = comma_pos + 2;
							    }
								
							if ( arg_debug >= Log_Informational_1 )
							    {
								System.out.println( "arg_max_lon: " + arg_max_lon_string );
							    }

							old_comma_pos = comma_pos;
							arg_max_lat_string = arg_bbox.substring( old_comma_pos+1 );
								
							if ( !arg_bbox.substring( comma_pos, comma_pos+1 ).equals( "," ))
							    {
								comma_pos = comma_pos + 2;
							    }
								
							if ( arg_debug >= Log_Informational_1 )
							    {
								System.out.println( "arg_max_lat: " + arg_max_lat_string );
							    }
						    } // max lon found
						else
						    {
							if ( arg_debug >= Log_Error )
							    {
								System.out.println( "3rd comma_pos: " + comma_pos );
								arg_bbox = "";
							    }
						    } // no max lon
					    } // min lat found
					else
					    {
						if ( arg_debug >= Log_Error )
						    {
							System.out.println( "2nd comma_pos: " + comma_pos );
							arg_bbox = "";
						    }
					    } // no min lat
				    } // min lon found
				else
				    {
					if ( arg_debug >= Log_Error )
					    {
						System.out.println( "1st comma_pos: " + comma_pos );
						arg_bbox = "";
					    }
				    } // no min lon
			    } // -bbox
		    } // potentially valid argument
	    } // for each thing on the command line

		
/* ------------------------------------------------------------------------------
 * Actually do what we've been asked to do.
 * ------------------------------------------------------------------------------ */
		if ( !arg_bbox.equals( "" ))
	    {
			/* ------------------------------------------------------------------------------------------------------------
			 * Write the GPX header acceptable to a Garmin GPS
			 * ------------------------------------------------------------------------------------------------------------ */
			if ( arg_out_gpx_file != "" )
			{
				myGpxPrintStream.println( "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>" );
				myGpxPrintStream.println( "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"MapSource 6.13.7\" version=\"1.1\">" );
			}
			
			if ( arg_do_notes )
			{
				if ( arg_in_file.equals( "" ))
			    {
					/* ------------------------------------------------------------------------------
					 * If either of them are not passed, arg_display_name and arg_uid would be black.
					 * ------------------------------------------------------------------------------ */
					process_notes( arg_display_name, arg_uid, arg_closed, arg_limit, arg_symbol, arg_min_lat_string, arg_min_lon_string, arg_max_lat_string, arg_max_lon_string );
			    } // no "in" file
				else
			    { // "in" file specified
					if ( arg_in_file.equals( "!file" ))
				    {
						if ( arg_debug >= Log_Serious )
					    {
							System.out.println( "Input file could not be opened" );
					    }
				    }
					else
				    {
						/* ------------------------------------------------------------------------------
						 * We do have an input file defined and we have been able to open it.
						 * ------------------------------------------------------------------------------ */

						if ( arg_debug >= Log_Informational_2 )
					    {
							System.out.println( "Input file: " + arg_in_file );
					    }

						process_notes_file( arg_display_name, arg_uid, arg_symbol, arg_min_lat_string, arg_min_lon_string, arg_max_lat_string, arg_max_lon_string );
				    }
			    }
			} // arg_do_notes
			
			if ( arg_do_fixmes )
			{
				process_fixmes( arg_symbol, arg_min_lat_string, arg_min_lon_string, arg_max_lat_string, arg_max_lon_string );
			} // arg_do_fixmes
			
			/* ------------------------------------------------------------------------------
			 * Write trailer for the GPX file if necessary
			 * ------------------------------------------------------------------------------ */
			if ( arg_out_gpx_file != "" )
			{
				myGpxPrintStream.println( "</gpx>" );
			}
	    } // arg_bbox
		else
	    {
			/* ------------------------------------------------------------------------------
			 * If no bounding box has been passed, we can't do anything.
			 * ------------------------------------------------------------------------------ */
			if ( arg_debug >= Log_Error )
		    {
				System.out.println( "No bounding box defined." );
		    }
	    }

		
/* ------------------------------------------------------------------------------
 * If we've been writing to an output_gpx or output_txt file, close it.
 * ------------------------------------------------------------------------------ */
		if ( !arg_out_gpx_file.equals( "" ))
		{
			myoutput_gpxStream.close();
		}
		
		if ( !arg_out_txt_file.equals( "" ))
		{
			myoutput_txtStream.close();
		}
	} // main
}
