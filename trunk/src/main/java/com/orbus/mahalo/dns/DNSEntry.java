/**
 * Copyright 2007 Jeff Ward
 * Portions may be pulled from JmDNS and are therefore 
 * the copyright of the JmDNS team
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orbus.mahalo.dns;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Hashtable;

/**
 * DNS entry with a name, type, and class. This is the base
 * class for questions and records.
 *
 * @version %I%, %G%
 * @author	Arthur van Hoff, Pierre Frisch, Rick Blair
 */
public class DNSEntry
{
	public enum EntryType {
		A ("a", 1),
		NS ("ns", 2),
		MD ("md", 3),
		MF ("mf", 4), 
		CNAME ("cname", 5),
		SOA ("soa", 6),
		MB("mb", 7),
		MG("mg", 8),
		MR("mr", 9),
		NULL("nukk", 10),
		WKS("wks", 11),
		PTR("ptr", 12),
		HINFO("hinfo", 13),
		MINFO("minfo", 14),
		MX("mx", 15),
		TXT("txt", 16),
		AAAA("quada", 28),
		SRV("srv", 33),
		ANY("any", 255);
		
		private static Hashtable<Integer, EntryType> parseTable;
		
		private final String _sRecord;
		private final int _iValue;
		
		static  {
			parseTable = new Hashtable<Integer, EntryType>();
			for(EntryType type : EntryType.values()) {
				parseTable.put(type._iValue, type);
			}
		}
		
		EntryType(String asRecord, int aiType) {
			_sRecord = asRecord;
			_iValue = aiType;
		}
		
		public String getRecord() {
			return _sRecord;
		}
		
		public int getValue() {
			return _iValue;
		}
		
		public static EntryType Parse(int aiValue) {
			return parseTable.get(aiValue);
		}
	}
	
	public enum EntryClass {
		IN ("in", 1),
		CS ("cs", 2),
		CH ("ch", 3),
		HS ("hs", 4),
		NONE ("none", 254),
		ANY ("any", 255);
		
		private static Hashtable<Integer, EntryClass> parseTable;
		
		private final String _sName;
		private final int _iValue;
		
		static  {
			parseTable = new Hashtable<Integer, EntryClass>();
			for(EntryClass type : EntryClass.values()) {
				parseTable.put(type._iValue, type);
			}
		}
		
		EntryClass(String asName, int aiValue) {
			_sName = asName;
			_iValue = aiValue;
		}
		
		public String getRecord() {
			return _sName;
		}
		
		public int getValue() {
			return _iValue;
		}
		
		public static EntryClass Parse(int aiValue)
		{
			return parseTable.get(aiValue);
		}
	}
	
	// TODO: This is incorrect.  See draft-cheshire-dnsext-multicastdns.txt Chapter 11.
	public static final int TTL = 60 * 60;	// default one hour TTL 
	
	private static final Charset s_Charset = Charset.forName("UTF-8");
	private static final int CLASS_MASK = 0x7FFF;
	private static final int CLASS_UNIQUE = 0x8000;
	
    protected String _sKey;
    protected String _sName;
    protected EntryType _eType;
    protected EntryClass _eClass;
    protected boolean _bUnique;

    DNSEntry() {
    	
    }
    
    /**
     * Create an entry.
     */
    DNSEntry(String asName, EntryType aeType, EntryClass aeClass, boolean abUnique) {
        _sKey = asName.toLowerCase();
        _sName = asName;
        _eType = aeType;
        _eClass = aeClass;
        _bUnique = abUnique;
    }

    /**
     * Check if two entries have exactly the same name, type, and class.
     */
    public boolean equals(Object obj) {
        
    	if (obj instanceof DNSEntry) {
            DNSEntry other = (DNSEntry) obj;
            return _sName.equals(other._sName) && _eType == other._eType && _eClass == other._eClass;
        }
        return false;
    }

    public String getName() {
        return _sName;
    }

    public EntryType getType() {
        return _eType;
    }
    
    public EntryClass getDNSClass() {
    	return _eClass;
    }
    
    public boolean getUnique() {
    	return _bUnique;
    }
    
    public void setUnique(boolean abValue) {
    	_bUnique = abValue;
    }
    
    public byte[] toByteArray()
    {
    	byte[] bytes = null;
    	try
    	{
    		ByteArrayOutputStream bout = new ByteArrayOutputStream();
	        DataOutputStream dout = new DataOutputStream(bout);
	        dout.write(_sName.getBytes("UTF8"));
	        dout.writeShort(_eType.getValue());
	        dout.writeShort(_eClass.getValue());
	        bytes = bout.toByteArray();
	        dout.close();
    	}
    	catch(IOException e)
    	{
    		// TODO:
    	}
    	
    	return bytes;
    }
    
    public void write(ByteBuffer aBuffer)
    {
    	writeDNSName(aBuffer, _sName);
    	aBuffer.putShort((short)_eType.getValue());
    	aBuffer.putShort((short)(_eClass.getValue() | (_bUnique ? CLASS_UNIQUE : 0)));
    }
    
    /**
     * Overriden, to return a value which is consistent with the value returned
     * by equals(Object).
     */
    public int hashCode()
    {
        return _sName.hashCode() + _eType.getValue() + _eClass.getValue();
    }
    
    public String toString() {
        return "[" + _eType.getRecord() + "," + _eClass.getRecord() + (_bUnique ? "-unique," : ",") + _sName + "]";
    }
    
    public static int writeDNSName(ByteBuffer aBuffer, String asName) {
    	int ilength = 0;
    	String[] sparts = asName.split("\\.");
    	for(String part : sparts)
    	{
    		// TODO: use offsets in cases where the name has
    		// already been written to the packet
    		byte[] bytes = s_Charset.encode(part).array();
    		// HACK: For some reason, Charset.encode will add a 0 to the end of the string,
    		// probably for compatability with C style strings.  If this occurs, remove the
    		// trailing 0.
    		int iarrayLength = bytes.length;
    		if(bytes[iarrayLength - 1] == 0)
    			iarrayLength--;
    		aBuffer.put((byte)iarrayLength);
    		ilength++;
    		
    		aBuffer.put(bytes, 0, iarrayLength);
    		ilength += iarrayLength;
    	}
    	
    	aBuffer.put((byte)0);
    	ilength++;
    	
    	return ilength;
    }
    
    public static String getDNSName(ByteBuffer aBuffer) throws IOException {
    	StringBuffer buf = new StringBuffer();
        int next = -1;
        int first = aBuffer.position();

        int len = aBuffer.get();
        while(len != 0) {
        	switch(len & 0xC0) {
        	case 0x00:
        		// Top two bytes 00.  Indicates a label.
        		byte[] bytes = new byte[len];
        		aBuffer.get(bytes);
        		buf.append(s_Charset.decode(ByteBuffer.wrap(bytes)));
        		buf.append('.');
        		break;
        	case 0xC0:
        		// Top two bits 11 indicates a pointer
        		if(next < 0)
        			next = aBuffer.position() + 1;
        		// Strip off the top two bits and add in the next byte.
        		aBuffer.position( (len & 0x3F) << 8 | aBuffer.get() );
        		if(aBuffer.position() >= first)
        			throw new IOException("bad domain name: possible circular name detected");
        		first = aBuffer.position();
        		break;
        	default:
        		throw new IOException("Bad length on name!");	
        	}
        	
        	len = aBuffer.get();
        }
        
        // If we've been following pointers, reset to the original position where we jumped from
        if(next > 0)
        	aBuffer.position(next);
        
        return buf.toString();
    }
    
    public static int getUnsignedShort(ByteBuffer aBuffer) {
    	return ((aBuffer.get() & 0xFF) << 8) + (aBuffer.get() & 0xFF);
    }
    
    public static DNSEntry Parse(ByteBuffer aBuffer) throws IOException {
    	// Refactor to GetName:
        DNSEntry entry = new DNSEntry();
        entry._sKey = getDNSName(aBuffer);
        entry._sName = entry._sKey.toLowerCase();
        entry._eType = EntryType.Parse(aBuffer.getShort());
        
        // Unique is coded in with the class, so it needs to be masked out: 
        int classAndUnique = aBuffer.getShort();
        entry._eClass = EntryClass.Parse(classAndUnique & CLASS_MASK);
        entry._bUnique = (classAndUnique & CLASS_UNIQUE) != 0;
        
        return entry;
    }
}
