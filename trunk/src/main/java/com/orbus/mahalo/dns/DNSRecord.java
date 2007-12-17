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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * DNS record
 *
 * @version %I%, %G%
 * @author	Arthur van Hoff, Rick Blair, Werner Randelshofer, Pierre Frisch
 */
public abstract class DNSRecord
{
	protected DNSEntry _Entry;
    protected int _iTTL;
    private long _iCreated;

    public DNSEntry.EntryType getType() {
    	return _Entry.getType();
    }
    
    public DNSEntry.EntryClass getDNSClass() {
    	return _Entry.getDNSClass();
    }
    
    public boolean isAuthoritative() {
    	return _Entry.getUnique();
    }
    
    public String getName() {
    	return _Entry.getName();
    }
    
    public int getTTL() {
    	return _iTTL;
    }
    
    DNSRecord() {
    	_iCreated = System.currentTimeMillis();
    }
    
    /**
     * Create a DNSRecord with a name, type, clazz, and ttl.
     */
    DNSRecord(String asName, DNSEntry.EntryType aeType, DNSEntry.EntryClass aeClass, boolean abUnique, int aiTTL) {
        this();
    	_Entry = new DNSEntry(asName, aeType, aeClass, abUnique);
        _iTTL = aiTTL;
    }

    /**
     * True if this record is the same as some other record.
     */
    public boolean equals(Object other) {
        return (other instanceof DNSRecord) && sameAs((DNSRecord) other);
    }

    /**
     * True if this record is the same as some other record.
     */
    boolean sameAs(DNSRecord other) {
        return _Entry.equals(other._Entry) && sameValue(other);
    }

    /**
     * True if this record has the same value as some other record.
     */
    abstract boolean sameValue(DNSRecord other);

    /**
     * Get the expiration time of this record.
     */
    long getExpirationTime(int percent)
    {
        return _iCreated + (percent * _iTTL * 10L);
    }

    /**
     * Get the remaining TTL for this record.
     */
    int getRemainingTTL(long now)
    {
        return (int) Math.max(0, (getExpirationTime(100) - now) / 1000);
    }

    /**
     * Check if the record is expired.
     */
    public boolean isExpired(long now)
    {
        return getExpirationTime(100) <= now;
    }

    /**
     * Check if the record is stale, ie it has outlived
     * more than half of its TTL.
     */
    boolean isStale(long now)
    {
        return getExpirationTime(50) <= now;
    }

    /**
     * Reset the TTL of a record. This avoids having to
     * update the entire record in the cache.
     */
    void resetTTL(DNSRecord other)
    {
        _iCreated = other._iCreated;
        _iTTL = other._iTTL;
    }

    public void write(ByteBuffer aBuffer)
    {
    	long now = System.currentTimeMillis();
    	
    	_Entry.write(aBuffer);
    	aBuffer.putInt(getRemainingTTL(now));
    }
    
    abstract void parseInstance(ByteBuffer aBuffer, int aiLength) throws IOException;
    
    public static DNSRecord Parse(ByteBuffer aBuffer) throws IOException {
    	DNSRecord record = null;
    	DNSEntry internalEntry = DNSEntry.Parse(aBuffer);
    	
    	int ttl = aBuffer.getInt();
    	int len = DNSEntry.getUnsignedShort(aBuffer);
    	assert(len != 0);
    	
    	switch(internalEntry._eType) {
    	case A:
    	case AAAA:
    		record = new Address();
    		break;
    	case CNAME:
    	case PTR:
    		record = new Pointer();
    		break;
    	case TXT:
            record = new Text();
            break;
        case SRV:
            record = new Service();
            break;
        case HINFO:
            // Maybe we should do something with those
            break;
        default :
            break;
    	}
    	
    	if(record != null) {
    		record._Entry = internalEntry;
    		record._iTTL = ttl;
    		record.parseInstance(aBuffer, len);
    	}
    	else
    		aBuffer.position(aBuffer.position() + len);
    	
    	return record;    	
    }

    /**
     * Address record.
     */
    public static class Address extends DNSRecord
    {
        private InetAddress _Addr;

        Address() {
        	
        }
        
        public Address(String asName, DNSEntry.EntryType aeType, DNSEntry.EntryClass aeClass, boolean abUnique,
        		int aiTTL, InetAddress aAddr) throws IllegalArgumentException
        {
            super(asName, aeType, aeClass, abUnique, aiTTL);
            if(aeType != DNSEntry.EntryType.A && aeType != DNSEntry.EntryType.AAAA)
            	throw new IllegalArgumentException("aeType must be either A or AAAA for an Address record.");
            if(aAddr == null)
            	throw new IllegalArgumentException("Address can not be null.");
            this._Addr = aAddr;
        }

        public void write(ByteBuffer aBuffer)
        {
        	super.write(aBuffer);
        	byte[] byteAddress = _Addr.getAddress();
        	switch(_Entry._eType) {
        	case A:
        	{
        		if(!(_Addr instanceof Inet4Address)) {
        			byte[] temp = byteAddress;
        			byteAddress = new byte[4];
        			System.arraycopy(temp, 12, byteAddress, 0, 4);
        		}
        		break;
        	}
        	case AAAA:
        	{
        		if(_Addr instanceof Inet4Address) {
        			byte[] temp = byteAddress;
        			byteAddress = new byte[16];
        			for (int i = 0; i < 16; i++)
                    {
                        if (i < 11)
                        	byteAddress[i] = temp[i - 12];
                        else
                        	byteAddress[i] = 0;
                    }
        		}
        		break;
        	}
        	}
        	
        	aBuffer.putShort((short)byteAddress.length);
        	aBuffer.put(byteAddress);
        }

        public boolean sameName(DNSRecord other)
        {
            return _Entry._sName.equalsIgnoreCase(((Address) other)._Entry._sName);
        }

        public boolean sameValue(DNSRecord other)
        {
            return _Addr.equals(((Address) other).getAddress());
        }

        public InetAddress getAddress()
        {
            return _Addr;
        }
        
        public void parseInstance(ByteBuffer aBuffer, int aiLength) throws IOException
        {
        	byte[] address = new byte[aiLength];
        	aBuffer.get(address);
        	
        	_Addr = InetAddress.getByAddress(address);
        }

        /**
         * Creates a byte array representation of this record.
         * This is needed for tie-break tests according to
         * draft-cheshire-dnsext-multicastdns-04.txt chapter 9.2.
         */
        private byte[] toByteArray()
        {
            try
            {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                DataOutputStream dout = new DataOutputStream(bout);
                dout.write(_Entry.toByteArray());
                
                byte[] buffer = _Addr.getAddress();
                for (int i = 0; i < buffer.length; i++)
                {
                    dout.writeByte(buffer[i]);
                }
                dout.close();
                return bout.toByteArray();
            }
            catch (IOException e)
            {
                throw new InternalError();
            }
        }

        /**
         * Does a lexicographic comparison of the byte array representation
         * of this record and that record.
         * This is needed for tie-break tests according to
         * draft-cheshire-dnsext-multicastdns-04.txt chapter 9.2.
         */
        private int lexCompare(DNSRecord.Address that)
        {
            byte[] thisBytes = this.toByteArray();
            byte[] thatBytes = that.toByteArray();
            for (int i = 0, n = Math.min(thisBytes.length, thatBytes.length); i < n; i++)
            {
                if (thisBytes[i] > thatBytes[i])
                {
                    return 1;
                }
                else
                {
                    if (thisBytes[i] < thatBytes[i])
                    {
                        return -1;
                    }
                }
            }
            return thisBytes.length - thatBytes.length;
        }

        public String toString()
        {
            return super.toString() + ", address '" + _Addr.getHostAddress() + "'";
        }

    }

    /**
     * Pointer record.
     */
    public static class Pointer extends DNSRecord
    {
        String _sAlias;
        
        Pointer() {
        
        }

        public Pointer(String asName, DNSEntry.EntryType aeType, DNSEntry.EntryClass aeClass, int aiTTL, String asAlias) 
        	throws IllegalArgumentException
        {
            super(asName, aeType, aeClass, false, aiTTL);
            if(aeType != DNSEntry.EntryType.PTR && aeType != DNSEntry.EntryType.CNAME)
            	throw new IllegalArgumentException("aeType must be either PTR or CNAME for a Pointer record.");
            _sAlias = asAlias;
        }

        public void write(ByteBuffer aBuffer)
        {
        	super.write(aBuffer);
        	int ilengthOffset = aBuffer.position();
        	aBuffer.putShort((short)0);
        	
        	int ilength = DNSEntry.writeDNSName(aBuffer, _sAlias);
        	aBuffer.putShort(ilengthOffset, (short)ilength);
        }

        boolean sameValue(DNSRecord other)
        {
            return _sAlias.equals(((Pointer) other)._sAlias);
        }

        public String getAlias()
        {
            return _sAlias;
        }

        public String toString()
        {
            return super.toString() + "," + _sAlias;
        }
        
        public void parseInstance(ByteBuffer aBuffer, int aiLength) throws IOException
        {
        	_sAlias = DNSEntry.getDNSName(aBuffer);
        }
    }

    public static class Text extends DNSRecord
    {
        byte _Text[];
        
        Text() {
        	
        }
        
        public byte[] getTextBytes() {
        	return _Text;
        }

        public Text(String asName, DNSEntry.EntryClass aeClass, boolean abUnique, int aiTTL, byte aText[])
        {
            super(asName, DNSEntry.EntryType.TXT, aeClass, abUnique, aiTTL);
            _Text = aText;
        }

        public void write(ByteBuffer aBuffer)
        {
        	super.write(aBuffer);
        	aBuffer.putShort((short)_Text.length);
            aBuffer.put(_Text);
        }

        boolean sameValue(DNSRecord other)
        {
            Text txt = (Text) other;
            if (txt._Text.length != _Text.length)
            {
                return false;
            }
            for (int i = _Text.length; i-- > 0;)
            {
                if (txt._Text[i] != _Text[i])
                {
                    return false;
                }
            }
            return true;
        }
        
        public void parseInstance(ByteBuffer aBuffer, int aiLength) throws IOException{
        	_Text = new byte[aiLength];
        	aBuffer.get(_Text);
        }

        public String toString()
        {
            return super.toString() + "," + (_Text.length > 10 ? new String(_Text, 0, 7) + "..." : new String(_Text));
        }
    }

    /**
     * Service record.
     */
    public static class Service extends DNSRecord
    {
        int _iPriority;
        int _iWeight;
        int _iPort;
        String _sServer;

        public Service() {
        
        }
        
        public Service(String asName, DNSEntry.EntryClass aeClass, boolean abUnique, int aiTTL, 
        		int aiPriority, int aiWeight, int aiPort, String asServer)
        {
        	super(asName, DNSEntry.EntryType.SRV, aeClass, abUnique, aiTTL);
        	_iPriority = aiPriority;
        	_iWeight = aiWeight;
        	_iPort = aiPort;
        	_sServer = asServer;
        }
        
        public int getPort() {
        	return _iPort;
        }
        
        public int getWeight() {
        	return _iWeight;
        }
        
        public int getPriority() {
        	return _iPriority;
        }
        
        public String getServer() {
        	return _sServer;
        }

        public void write(ByteBuffer aBuffer)
        {
        	super.write(aBuffer);
        	int ilengthOffset = aBuffer.position();
        	aBuffer.putShort((short)0);
        	
        	aBuffer.putShort((short)_iPriority);
        	aBuffer.putShort((short)_iWeight);
        	aBuffer.putShort((short)_iPort);
        	int inameLength = DNSEntry.writeDNSName(aBuffer, _sServer);
        	
        	// 6 is 3 shorts written above.
        	aBuffer.putShort(ilengthOffset, (short)(6 + inameLength));
        }

        private byte[] toByteArray()
        {
            try
            {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                DataOutputStream dout = new DataOutputStream(bout);
                dout.write(_Entry.toByteArray());
                dout.writeShort(_iPriority);
                dout.writeShort(_iWeight);
                dout.writeShort(_iPort);
                dout.write(_sServer.getBytes("UTF8"));
                dout.close();
                return bout.toByteArray();
            }
            catch (IOException e)
            {
                throw new InternalError();
            }
        }

        private int lexCompare(DNSRecord.Service that)
        {
            byte[] thisBytes = this.toByteArray();
            byte[] thatBytes = that.toByteArray();
            for (int i = 0, n = Math.min(thisBytes.length, thatBytes.length); i < n; i++)
            {
                if (thisBytes[i] > thatBytes[i])
                {
                    return 1;
                }
                else
                {
                    if (thisBytes[i] < thatBytes[i])
                    {
                        return -1;
                    }
                }
            }
            return thisBytes.length - thatBytes.length;
        }

        public boolean sameValue(DNSRecord other)
        {
            Service s = (Service) other;
            return (_iPriority == s._iPriority) && (_iWeight == s._iWeight) && (_iPort == s._iPort) && _sServer.equals(s._sServer);
        }
        
        public void parseInstance(ByteBuffer aBuffer, int aiLength) throws IOException {
        	_iPriority = DNSEntry.getUnsignedShort(aBuffer);
        	_iWeight = DNSEntry.getUnsignedShort(aBuffer);
        	_iPort = DNSEntry.getUnsignedShort(aBuffer);
        	
        	_sServer = DNSEntry.getDNSName(aBuffer);
        }

        public String toString()
        {
            return super.toString() + "," + _sServer + ":" + _iPort;
        }
    }

    public String toString()
    {
        return "record" + _Entry.toString() + getRemainingTTL(System.currentTimeMillis()) + "/" + _iTTL;
    }
}

