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
package com.orbus.mahalo;

import java.net.InetAddress;
import java.net.SocketException;

import com.orbus.mahalo.dns.DNSEntry;
import com.orbus.mahalo.dns.DNSRecord;

/**
 * HostInfo information on the local host to be able to cope with change of addresses.
 *
 * @version %I%, %G%
 * @author	Pierre Frisch, Werner Randelshofer
 */
public class HostInfo
{
    private InetAddress _Address;
    private String _sOriginalName;
    private String _sName;
    private ServiceState _eState = ServiceState.PROBING_1;
    
    /**
     * This is used to create a unique name for the host name.
     */
    private int _iHostNameCount = 1;

    public HostInfo(InetAddress aAddress, String asName)
    	throws IllegalArgumentException, SocketException
    {
        if(aAddress == null || asName == null)
        	throw new IllegalArgumentException("Niether aAddress nor asName can be null.");
        
        if(asName.indexOf('.') >= 0)
        	throw new IllegalArgumentException("asName should be a pure host name and not contain dots. (.local. will be added automatically).");
        
        _Address = aAddress;
        _sOriginalName = asName;
        _sName = asName + ".local.";
    }
    
    public synchronized void advanceState()
    {
    	_eState = _eState.advance();
    }
    
    public synchronized void revertState()
    {
    	_eState = _eState.revert();
    }
    
    public final ServiceState getState()
    {
    	return _eState;
    }

    public String getName()
    {
        return _sName;
    }

    public InetAddress getAddress()
    {
        return _Address;
    }
    
    public void incrementHostName()
    {
        _iHostNameCount++;
        _sName = _sOriginalName + "-" + _iHostNameCount;
    }

    public DNSRecord.Address getDNSAddressRecord(DNSEntry.EntryType aeType)
    {
        return aeType == DNSEntry.EntryType.A ? getDNS4AddressRecord() : getDNS6AddressRecord();
    }

    private DNSRecord.Address getDNS4AddressRecord()
    {
        return new DNSRecord.Address(getName(), DNSEntry.EntryType.A, DNSEntry.EntryClass.IN, true, DNSEntry.TTL, getAddress());
    }

    private DNSRecord.Address getDNS6AddressRecord()
    {
        return null;
    }

    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        buf.append("local host info[");
        buf.append(getName());
        buf.append(", ");
        buf.append(":");
        buf.append(getAddress().getHostAddress());
        buf.append("]");
        return buf.toString();
    }

}
