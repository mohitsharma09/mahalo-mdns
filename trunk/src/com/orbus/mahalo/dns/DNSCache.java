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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

public class DNSCache
{
	private static final Logger s_Logger = Logger.getLogger(DNSCache.class);
	private Map<String, List<DNSRecord>> _Cache; 

    /**
     * Create a table with a given initial size.
     */
    public DNSCache(final int aiSize)
    {
    	_Cache = new HashMap<String, List<DNSRecord>>(aiSize);
    }

    /**
     * Clears the cache.
     */
    public synchronized void clear()
    {
    	_Cache.clear();
    }
    
    public synchronized DNSRecord handleRecord(DNSRecord aRecord)
    {
    	long now = System.currentTimeMillis();
    	DNSRecord cacheRecord = get(aRecord);
    	boolean bisExpired = aRecord.isExpired(now);
    	
    	if(cacheRecord != null)
		{
			if(bisExpired) {
				remove(aRecord);
			}
			else
				cacheRecord.resetTTL(aRecord);
		}
		else if(!bisExpired)
		{
			add(aRecord);
		}
    	
    	return cacheRecord;
    }
    
    public synchronized void reap()
    {
    	s_Logger.trace("Running the reaper.");
    	// DON'T FEAR THE REAPPER!!!!!
    	long now = System.currentTimeMillis();
    
    	List<String> mapRemovalList = new LinkedList<String>();
    	for(List<DNSRecord> list : _Cache.values()) {
    		List<DNSRecord> removalList = new LinkedList<DNSRecord>();
    		String key = list.get(0).getName();
    		for(DNSRecord rec : list) {
    			if(rec.isExpired(now)) {
    				removalList.add(rec);
    			}
    		}
    		for(DNSRecord rec : removalList) {
    			s_Logger.debug("Removing expired record: " + rec);
    			list.remove(rec);
    		}
    		if(list.size() == 0)
    			mapRemovalList.add(key);
    	}
    	
    	for(String key : mapRemovalList) {
    		s_Logger.debug("Removing expired key: " + key);
    		_Cache.remove(key);
    	}
    }

    /**
     * Adds an entry to the table.
     */
    public synchronized void add(final DNSRecord aRecord)
    {
    	List<DNSRecord> list = _Cache.get(aRecord.getName());
    	if(list == null) {
    		list = new LinkedList<DNSRecord>();
    		list.add(aRecord);
    		_Cache.put(aRecord.getName(), list);
    	}
    	else {
    		boolean baddRecord = true;
			// Check for a duplicate record.
    		for(DNSRecord rec : list) {
    			if(rec.equals(aRecord)) {
    				s_Logger.warn("Attempt to add non-autoritative duplicate DNSRecord:" + aRecord);
    				baddRecord = false;
    				break;
    			}
    		}
    		if(baddRecord) {
    			s_Logger.debug("Adding record " + aRecord + " to DNS cache.");
    			list.add(aRecord);
    		}
    			
    	}
    }

    /**
     * Remove a specific entry from the table. Returns true if the
     * entry was found.
     */
    public synchronized boolean remove(DNSRecord aRecord)
    {
    	List<DNSRecord> list = _Cache.get(aRecord.getName());
    	if(list != null)
        {
            for(DNSRecord rec : list)
            {
            	if(rec.equals(aRecord))
            	{
            		s_Logger.debug("Removing record " + aRecord + " from DNS cache.");
            		list.remove(rec);
            		if(list.size() == 0)
            			_Cache.remove(aRecord.getName());
            		return true;
            	}
            }
        }
        return false;
    }

    /**
     * Get a matching DNS entry from the table (using equals).
     * Returns the entry that was found.
     */
    public synchronized DNSRecord get(DNSRecord aRecord)
    {
    	List<DNSRecord> list = _Cache.get(aRecord.getName());
    	if(list != null) {
    		for(DNSRecord rec : list) {
    			if(rec.equals(aRecord))
    				return rec;
    		}
    	}
    	
    	return null;
    }

    /**
     * Get a matching DNS entry from the table.
     */
    public synchronized List<DNSRecord> get(String asName, DNSEntry.EntryType aeType, DNSEntry.EntryClass aeClass)
    {
    	List<DNSRecord> retList = null;
    	List<DNSRecord> list = _Cache.get(asName);
    	if(list != null) {
    		for(DNSRecord rec : list) {
    			if (rec.getType() == aeType && rec.getDNSClass() == aeClass) {
    				if(retList == null)
    					retList = new LinkedList<DNSRecord>();
    				retList.add(rec);
    			}
    		}
    	}
    	
    	return retList;
    }
    
    public synchronized DNSRecord.Service getAssociatedService(DNSRecord.Pointer aPtrRecord) {
    	DNSRecord.Service retService = null;
    	List<DNSRecord> list = get(aPtrRecord.getAlias(), DNSEntry.EntryType.SRV, DNSEntry.EntryClass.IN);
    	if(list != null) {
    		assert(list.size() == 1);
    		retService = (DNSRecord.Service)list.get(0);
    	}
    	
    	return retService;
    }
    
    public synchronized DNSRecord.Address getAssociatedAddress(DNSRecord.Service aSrvRecord) {
    	// TODO: get IPv6 entries as well
    	DNSRecord.Address retAddress = null;
    	List<DNSRecord> list = get(aSrvRecord.getServer(), DNSEntry.EntryType.A, DNSEntry.EntryClass.IN);
    	if(list != null) {
    		assert(list.size() == 1);
    		retAddress = (DNSRecord.Address)list.get(0);
    	}
    	
    	return retAddress;
    }
    
    public synchronized DNSRecord.Text getAssociatedText(DNSRecord.Service aSrvRecord) {
    	DNSRecord.Text retText = null;
    	List<DNSRecord> list = get(aSrvRecord.getName(), DNSEntry.EntryType.TXT, DNSEntry.EntryClass.IN);
    	if(list != null) {
    		// Just return the first match for now.
    		// TODO: return a list of text records...
    		retText = (DNSRecord.Text)list.get(0);
    	}
    	
    	return retText;
    }
}
