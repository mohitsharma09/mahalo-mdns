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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A DNS question.
 *
 * @version %I%, %G%
 * @author	Arthur van Hoff
 */
public final class DNSQuestion
{
	DNSEntry _Entry;	
	
	public boolean wantsUnicastResponce() {
		return _Entry.getUnique();
	}
	
	public void setWantsUnicastResponce(boolean abYes) {
		_Entry.setUnique(abYes);
	}
	
	public String getName() {
		return _Entry.getName();
	}
	
	public DNSEntry.EntryType getType() {
		return _Entry.getType();
	}
	
	public DNSEntry.EntryClass getDNSClass() {
		return _Entry.getDNSClass();
	}
	
	DNSQuestion() {
		
	}
	
	/**
     * Create a question.
     */
    public DNSQuestion(String asName, DNSEntry.EntryType aeType, DNSEntry.EntryClass aeClass, boolean abUnicastResponce)
    {
    	_Entry = new DNSEntry(asName, aeType, aeClass, abUnicastResponce);
    }

    /**
     * Check if this question is answered by a given DNS record.
     */
    public boolean answeredBy(DNSRecord rec)
    {
        return (_Entry._eClass == rec._Entry._eClass) && 
        	((_Entry._eType == rec._Entry._eType) || (_Entry._eType == DNSEntry.EntryType.ANY)) &&
            (_Entry._sName.equals(rec._Entry._sName));
    }
    
    public void write(ByteBuffer aBuffer)
    {
    	_Entry.write(aBuffer);
    }

    /**
     * For debugging only.
     */
    public String toString()
    {
        return "question" + _Entry.toString();
    }
    
    public static DNSQuestion Parse(ByteBuffer buffer) throws IOException
    {
    	DNSQuestion retQuestion = new DNSQuestion();
    	retQuestion._Entry = DNSEntry.Parse(buffer);
        
    	return retQuestion;
    }
}
