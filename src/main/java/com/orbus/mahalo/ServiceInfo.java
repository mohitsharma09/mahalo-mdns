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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.orbus.mahalo.dns.DNSRecord;

/**
 * JmDNS service information.
 *
 * @version %I%, %G%
 * @author	Arthur van Hoff, Jeff Sonstein, Werner Randelshofer
 */
public class ServiceInfo
{
	private static final Charset s_Charset = Charset.forName("UTF-8");
	
	private String _sServiceType;
    private String _sName;
    //private String _sServer;
    private int _iPort;
    private int _iWeight;
    private int _iPriority;
    private byte[] _Text;
    private ServiceState _eState = ServiceState.PROBING_1;
    
    public ServiceInfo(String asType, String asName, int aiPort, String asText)
    	throws IllegalArgumentException
    {
        this(asType, asName, aiPort, 0, 0, asText);
    }

    public ServiceInfo(String asType, String asName, int aiPort, int aiWeight, int aiPriority, String asText)
    	throws IllegalArgumentException
    {
        this(asType, asName, aiPort, aiWeight, aiPriority, (byte[]) null);
        _Text = s_Charset.encode(asText).array();        
    }

    public ServiceInfo(String asType, String asName, int aiPort, int aiWeight, int aiPriority, byte[] aText) 
    	throws IllegalArgumentException
    {
    	if(asType.endsWith("\\."))
    		throw new IllegalArgumentException("Service types must be fully qualified DNS names ending in '.': " + 
    				asType + " is invalid.");
    	
    	_sServiceType = asType;
        _sName = asName;
        _iPort = aiPort;
        _iWeight = aiWeight;
        _iPriority = aiPriority;
        _Text = aText;
    }

    public ServiceInfo(ServiceInfo aInfo) throws IllegalArgumentException
    {
        this(aInfo._sServiceType, aInfo._sName, aInfo._iPort, aInfo._iWeight, aInfo._iPriority, aInfo._Text);
    }
    
    public ServiceInfo(DNSRecord.Service aServiceRecord) {
    	String sname = aServiceRecord.getName();
    	int ifirstDot = sname.indexOf(".");
    	_sServiceType = sname.substring(ifirstDot + 1);
    	_sName = sname.substring(0, ifirstDot);
    	_iPort = aServiceRecord.getPort();
    	_iWeight = aServiceRecord.getWeight();
    	_iPriority = aServiceRecord.getPriority();
    	_Text = null;
    }
    
    public synchronized void advanceState() {
    	_eState = _eState.advance();
    }
    
    public ServiceState getState() {
    	return _eState;
    }

    /**
     * Fully qualified service type name, such as <code>_http._tcp.local.</code> .
     */
    public String getType()
    {
        return _sServiceType;
    }

    /**
     * Unqualified service instance name, such as <code>foobar</code> .
     */
    public String getName()
    {
        return _sName;
    }

    /**
     * Fully qualified service name, such as <code>foobar._http._tcp.local.</code> .
     */
    public String getQualifiedName()
    {
        return _sName + "." + _sServiceType;
    }

    /**
     * Get the port for the service.
     */
    public int getPort()
    {
        return _iPort;
    }

    /**
     * Get the priority of the service.
     */
    public int getPriority()
    {
        return _iPriority;
    }

    /**
     * Get the weight of the service.
     */
    public int getWeight()
    {
        return _iWeight;
    }

    /**
     * Get the text for the serivce as raw bytes.
     */
    public byte[] getTextBytes()
    {
        return _Text;
    }

    /**
     * Get the text for the service. This will interpret the text bytes
     * as a UTF8 encoded string. Will return null if the bytes are not
     * a valid UTF8 encoded string.
     */
    public String getTextString()
    {
    	if(_Text != null)
    		return s_Charset.decode(ByteBuffer.wrap(_Text)).toString();
    	else
    		return null;
    }
    
    public void setTextBytes(byte[] aBytes) {
    	_Text = aBytes;
    }
    
    public void setTextString(String asText) {
    	_Text = s_Charset.encode(asText).array();
    }

    @Override
    public int hashCode()
    {
        return getQualifiedName().hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        return (obj instanceof ServiceInfo) && getQualifiedName().equals(((ServiceInfo) obj).getQualifiedName());
    }

    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        buf.append("service[");
        buf.append(getQualifiedName());
        buf.append(",Port:");
        buf.append(_iPort);
        buf.append(',');
        buf.append(getTextString());
        buf.append(',');
        buf.append(_eState);
        buf.append(']');
        return buf.toString();
    }
}
