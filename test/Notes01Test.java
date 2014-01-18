import static org.junit.Assert.*;

import org.junit.Test;


public class Notes01Test 
{

	@Test
/* ------------------------------------------------------------------------------
 * "resolve_ampersands" is supposed to replace all examples of "&" and replace
 * them with "&amp;".  We test this by processing various strings and verifying 
 * that the result strings have "&amp;" where the original strings had just "&".
 * 
 * The test strings chosen are ones "likely to cause a problem".
 * ------------------------------------------------------------------------------ */
	public void testResolve_ampersands() 
	{
		testResolve_ampersands_string( "wibble" );
		testResolve_ampersands_string( "foo & bar" );
		testResolve_ampersands_string( "foo & bar & bat" );
		testResolve_ampersands_string( "foo && bar" );
		testResolve_ampersands_string( "<cmt>Three bus stops here - one too many.  This one was originally across the road at http://www.openstreetmap.org/?mlat=53.16487 and mlon=-1.23164&zoom=18#map=18/53.16487/-1.23164 - need to check that there is a bus stop here outside the garage not the other side of Radmanthwaite Road.</cmt>" );
		testResolve_ampersands_string( "" );
		testResolve_ampersands_string( "&" );
		testResolve_ampersands_string( "&&" );
	}

	private void testResolve_ampersands_string( String test1 )
	{
		String test2 = Notes01.resolve_ampersands( test1 );
		testResolve_ampersands_string_test( test1, test2 );
	}

	
/* ------------------------------------------------------------------------------
 * This section is called both with the original "before and after" strings and 
 * recursively with the sections of the strings after "&" and "&amp;" 
 * respectively.
 * ------------------------------------------------------------------------------ */
	private void testResolve_ampersands_string_test( String test1, String test2 )
	{
/* ------------------------------------------------------------------------------
 * First, compare the part of the resulting string before the first ampersand.
 * ------------------------------------------------------------------------------ */
		int i1 = test1.indexOf( "&" );
		int i2 = test2.indexOf( "&amp;" );
		assertEquals( i1,  i2 );
		
/* ------------------------------------------------------------------------------
 * Next we need to compare the part of the string after the first ampersand.
 * We only need to do this is there is a bit of string after the first ampersand. 
 * 
 * We do this by recursively calling with the remaining parts of the string.
 * ------------------------------------------------------------------------------ */
		if ( i1 != -1 )
		{
			String rest1 = test1.substring( i1+1 );
			String rest2 = test2.substring( i2+5 );
			testResolve_ampersands_string_test( rest1, rest2 );
		}
	}
}
