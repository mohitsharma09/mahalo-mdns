package com.orbus.mahalo.dns.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Utilities {
	public static byte[] readFile(String asFileName) throws IOException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		InputStream file = Utilities.class.getResourceAsStream(asFileName);
		int i;
		while( (i = file.read()) != -1)
			stream.write((byte)i);
		
		return stream.toByteArray(); 
	}
	
	public static boolean bufferEqualsFile(byte[] aBuffer, String asFileName) throws IOException {
		InputStream file = Utilities.class.getResourceAsStream(asFileName);
		int data;
		boolean bbuffersEqual = true;
		for(int i = 0; (data = file.read()) != -1 && bbuffersEqual; i++) {
			bbuffersEqual &= ((byte)data) == aBuffer[i];
		}
		
		return bbuffersEqual;
	}
}
