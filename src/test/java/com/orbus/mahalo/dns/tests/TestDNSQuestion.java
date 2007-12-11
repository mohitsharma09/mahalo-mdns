package com.orbus.mahalo.dns.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import junit.framework.Assert;

import org.junit.Test;

import com.orbus.mahalo.dns.DNSEntry;
import com.orbus.mahalo.dns.DNSPacket;
import com.orbus.mahalo.dns.DNSQuestion;

public class TestDNSQuestion {
	@Test
	public void testBasicConstructor() {
		DNSQuestion question = new DNSQuestion("www.example.local.", DNSEntry.EntryType.ANY, 
			DNSEntry.EntryClass.IN, false);
		
		Assert.assertFalse(question.wantsUnicastResponce());
		Assert.assertEquals("www.example.local.", question.getName());
		Assert.assertEquals(DNSEntry.EntryType.ANY, question.getType());
		Assert.assertEquals(DNSEntry.EntryClass.IN, question.getDNSClass());
	}
	
	@Test
	public void testBasicParse() throws IOException {
		byte[] buffer = readFile("question.dns"); 
		DNSQuestion question = DNSQuestion.Parse(ByteBuffer.wrap(buffer));
		
		Assert.assertFalse(question.wantsUnicastResponce());
		Assert.assertEquals("www.example.local.", question.getName());
		Assert.assertEquals(DNSEntry.EntryType.ANY, question.getType());
		Assert.assertEquals(DNSEntry.EntryClass.IN, question.getDNSClass());
	}
	
	@Test
	public void testBasicWrite() throws IOException {
		DNSQuestion question = new DNSQuestion("www.example.local.", DNSEntry.EntryType.ANY, 
				DNSEntry.EntryClass.IN, false);
		
		byte[] buffer = new byte[DNSPacket.MAX_MSG_ABSOLUTE];
		ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		question.write(byteBuffer);
		
		Assert.assertTrue(bufferEqualsFile(buffer, "question.dns"));
	}
	
	private byte[] readFile(String asFileName) throws IOException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		InputStream file = TestDNSQuestion.class.getResourceAsStream(asFileName);
		int i;
		while( (i = file.read()) != -1)
			stream.write((byte)i);
		
		return stream.toByteArray(); 
	}
	
	private boolean bufferEqualsFile(byte[] aBuffer, String asFileName) throws IOException {
		InputStream file = TestDNSQuestion.class.getResourceAsStream(asFileName);
		int data;
		boolean bbuffersEqual = true;
		for(int i = 0; (data = file.read()) != -1 && bbuffersEqual; i++) {
			bbuffersEqual &= ((byte)data) == aBuffer[i];
		}
		
		return bbuffersEqual;
	}
}
