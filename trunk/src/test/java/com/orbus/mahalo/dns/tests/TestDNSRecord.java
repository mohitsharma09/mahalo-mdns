package com.orbus.mahalo.dns.tests;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.orbus.mahalo.dns.DNSEntry;
import com.orbus.mahalo.dns.DNSPacket;
import com.orbus.mahalo.dns.DNSRecord;

public class TestDNSRecord {
	@Test
	public void testBasicParse() throws IOException {
		byte[] buffer = Utilities.readFile("answer.dns"); 
		DNSRecord answer = DNSRecord.Parse(ByteBuffer.wrap(buffer));
		
		Assert.assertEquals("www.example.local.", answer.getName());
		Assert.assertTrue(answer.isAuthoritative());
		Assert.assertEquals(120, answer.getTTL());
		Assert.assertTrue(answer instanceof DNSRecord.Address);
	}
	
	@Test
	public void testParsePtr() throws IOException {
		byte[] buffer = Utilities.readFile("ptrRecord.dns"); 
		DNSRecord answer = DNSRecord.Parse(ByteBuffer.wrap(buffer));
		
		Assert.assertEquals("_example._tcp.local.", answer.getName());
		Assert.assertFalse(answer.isAuthoritative());
		Assert.assertEquals(3600, answer.getTTL());
		Assert.assertTrue(answer instanceof DNSRecord.Pointer);
		Assert.assertEquals("Test._example._tcp.local.", ((DNSRecord.Pointer)answer).getAlias());
	}
	
	@Test
	public void testBasicWrite() throws IOException {
		DNSRecord record = new DNSRecord.Address("www.example.local.", DNSEntry.EntryType.A, DNSEntry.EntryClass.IN,
				true, 120, InetAddress.getByName("10.0.0.1"));
		
		byte[] buffer = new byte[DNSPacket.MAX_MSG_ABSOLUTE];
		ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		record.write(byteBuffer);
		
		Assert.assertTrue(Utilities.bufferEqualsFile(buffer, "answer.dns"));
	}
	
	@Test
	public void testWritePtr() throws IOException {
		DNSRecord record = new DNSRecord.Pointer("_example._tcp.local.", DNSEntry.EntryType.PTR, DNSEntry.EntryClass.IN,
				3600, "Test._example._tcp._local.");
		
		byte[] buffer = new byte[DNSPacket.MAX_MSG_ABSOLUTE];
		ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		record.write(byteBuffer);
		
		Assert.assertTrue(Utilities.bufferEqualsFile(buffer, "ptrRecord.dns"));
	}
}
