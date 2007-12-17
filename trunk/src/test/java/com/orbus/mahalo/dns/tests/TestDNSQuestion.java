package com.orbus.mahalo.dns.tests;

import java.io.IOException;
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
		byte[] buffer = Utilities.readFile("question.dns"); 
		DNSQuestion question = DNSQuestion.Parse(ByteBuffer.wrap(buffer));
		
		Assert.assertFalse(question.wantsUnicastResponce());
		Assert.assertEquals("www.example.local.", question.getName());
		Assert.assertEquals(DNSEntry.EntryType.ANY, question.getType());
		Assert.assertEquals(DNSEntry.EntryClass.IN, question.getDNSClass());
	}
	
	@Test
	public void testUnicastParse() throws IOException {
		byte[] buffer = Utilities.readFile("unicastQuestion.dns"); 
		DNSQuestion question = DNSQuestion.Parse(ByteBuffer.wrap(buffer));
		
		Assert.assertTrue(question.wantsUnicastResponce());
		Assert.assertEquals("www.example.local.", question.getName());
		Assert.assertEquals(DNSEntry.EntryType.SRV, question.getType());
		Assert.assertEquals(DNSEntry.EntryClass.IN, question.getDNSClass());
	}
	
	@Test
	public void testBasicWrite() throws IOException {
		DNSQuestion question = new DNSQuestion("www.example.local.", DNSEntry.EntryType.ANY, 
				DNSEntry.EntryClass.IN, false);
		
		byte[] buffer = new byte[DNSPacket.MAX_MSG_ABSOLUTE];
		ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		question.write(byteBuffer);
		
		Assert.assertTrue(Utilities.bufferEqualsFile(buffer, "question.dns"));
	}
	
	@Test
	public void testUnicastWrite() throws IOException {
		DNSQuestion question = new DNSQuestion("www.example.local.", DNSEntry.EntryType.SRV, 
				DNSEntry.EntryClass.IN, true);
		
		byte[] buffer = new byte[DNSPacket.MAX_MSG_ABSOLUTE];
		ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		question.write(byteBuffer);
		
		Assert.assertTrue(Utilities.bufferEqualsFile(buffer, "unicastQuestion.dns"));
	}
}
