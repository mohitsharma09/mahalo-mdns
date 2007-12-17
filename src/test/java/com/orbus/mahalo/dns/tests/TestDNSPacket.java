package com.orbus.mahalo.dns.tests;

import java.io.IOException;
import java.nio.ByteBuffer;

import junit.framework.Assert;

import org.junit.Test;

import com.orbus.mahalo.dns.DNSPacket;

public class TestDNSPacket {
	@Test
	public void TestRecordPacketWithPointers() throws IOException {
		byte[] buffer = Utilities.readFile("PacketWithNamePointers.dns"); 
		DNSPacket packet = DNSPacket.Parse(ByteBuffer.wrap(buffer));
		
		Assert.assertEquals(2, packet.getAnswers().size());
		Assert.assertEquals("f.isi.arpa.", packet.getAnswers().get(0).getName());
		Assert.assertEquals("foo.f.isi.arpa.", packet.getAnswers().get(1).getName());
	}
}
