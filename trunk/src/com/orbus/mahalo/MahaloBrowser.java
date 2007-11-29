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

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.orbus.mahalo.dns.DNSCache;
import com.orbus.mahalo.dns.DNSEntry;
import com.orbus.mahalo.dns.DNSPacket;
import com.orbus.mahalo.dns.DNSQuestion;
import com.orbus.mahalo.dns.DNSRecord;

public class MahaloBrowser implements MahaloSocketListener {
	private static final Logger s_Logger = Logger.getLogger(MahaloBrowser.class);
	
	private boolean _bOwnsSocket;
	private MahaloSocket _Socket;
	private Map<String, List<ServiceListener>> _ServiceListeners = new HashMap<String, List<ServiceListener>>();
	private DNSCache _Cache;
	
	public MahaloBrowser(InetAddress aAddress) throws IOException {
		this(new MahaloSocket(aAddress), new DNSCache(100));
		
		_bOwnsSocket = true;
		_Socket.startListening();
	}
	
	public MahaloBrowser(MahaloSocket aSocket, DNSCache aCache) {
		_bOwnsSocket = false;
		_Socket = aSocket;
		_Socket.addListener(this);
		_Cache = aCache;
	}
	
	public void close() {
		if(_bOwnsSocket) {
			_Socket.close();
		}
	}
	
	public void addServiceListener(String asType, ServiceListener aListener) {
		asType = asType.toLowerCase();
		
		synchronized (_ServiceListeners) {
            List<ServiceListener> list = _ServiceListeners.get(asType);
            if (list == null) {
                list = new LinkedList<ServiceListener>();
                _ServiceListeners.put(asType, list);
            }
            
            if(!list.contains(aListener))
            	list.add(aListener);
        }
		
		// report cached service types
        List<DNSRecord> ptrList = _Cache.get(asType, DNSEntry.EntryType.PTR, DNSEntry.EntryClass.IN);
        if(ptrList != null) {
        	for(DNSRecord rec : ptrList) {
        		DNSRecord.Pointer ptrRec = (DNSRecord.Pointer)rec;
        		// Find a matching SRV record if we have one
        		DNSRecord.Service srvRec = _Cache.getAssociatedService(ptrRec);
        		if(srvRec != null) {	
        			ServiceInfo srvInfo = new ServiceInfo(srvRec);
        			RemoteHostInfo hostInfo = null;
        			
        			// If we have it in the cache, report the address for free.
        			DNSRecord.Address addressRec = _Cache.getAssociatedAddress(srvRec);
        			if(addressRec != null) {
        				hostInfo = new RemoteHostInfo(addressRec);        				
        				aListener.serviceResolved(new ServiceEvent(this, srvInfo, hostInfo));
        			}
        			else {
        				aListener.serviceAdded(new ServiceEvent(this, srvInfo, null));
        			}
        		}
        	}
        }
        
        // TODO: Set up a timer to repeat this query.
        DNSPacket packet = new DNSPacket(false);
        packet.addQuestion(new DNSQuestion(asType, DNSEntry.EntryType.PTR, DNSEntry.EntryClass.IN, false));
        _Socket.send(packet);
	}
	
	public void removeServiceListener(String asType, ServiceListener aListener) {
    	asType = asType.toLowerCase();
    	synchronized(_ServiceListeners) {
	        List<ServiceListener> list = _ServiceListeners.get(asType);
	        if (list != null) {
                list.remove(aListener);
                if (list.size() == 0)
                	_ServiceListeners.remove(asType);
	        }
    	}
    }
	
	public void handleQuery(DNSPacket aPacket, InetAddress aAddress, int aiPort) {
		// Do nothing.  Browsers don't care about queries
	}
	
	public void handleResponse(DNSPacket aPacket) {
		long now = System.currentTimeMillis();
		
		// This is a bit of a short cut.  Loop through looking for all address records
		// and add them to the cache.  This prevents us from mistakenly trying to resolve
		// records we already know the answer to.  In addition, create a list of pointers
		// and services that may be new records.
		List<DNSRecord> postProcess = new LinkedList<DNSRecord>();
		
		for(DNSRecord rec : aPacket.getAnswers()) {
			DNSRecord oldRecord = _Cache.handleRecord(rec);
			boolean bisExpired = rec.isExpired(now);
			
			if(oldRecord != null && rec instanceof DNSRecord.Pointer && bisExpired) {
				// If the cache had a record and it's a pointer that's expired, 
				// Inform listeners of the service removal
				
				DNSRecord.Pointer ptrRecord = (DNSRecord.Pointer)rec;
				
				// Find the matching SRV record in the cache
				DNSRecord.Service srvRecord = _Cache.getAssociatedService(ptrRecord);
				if(srvRecord != null) {
					ServiceEvent evt = getEventFromRecord((DNSRecord.Service)srvRecord);
					if(evt != null)
						onServiceRemoved(evt);
					else if(s_Logger.isTraceEnabled())
						s_Logger.trace("Could not create service event from dying pointer record: " + ptrRecord);
				} else if(s_Logger.isTraceEnabled()) {
					s_Logger.trace("Could not find sevice associated with dying pointer record: " + ptrRecord);
				}
			}
			else if(oldRecord == null && !bisExpired && 
					(rec instanceof DNSRecord.Pointer || rec instanceof DNSRecord.Service)) {
				// Deal with you once we have *all* the information
				postProcess.add(rec);
			}
		}
		
		// Second pass.  Only report services which have address information.  If we got a new
		// pointer and we don't have information on the service, resolve it.
		// TODO: Attempt to resolve the address first.  Have a callback system to resolve what we need.
		for(DNSRecord record : postProcess) {
			if(record instanceof DNSRecord.Service) {
				ServiceEvent evt = getEventFromRecord((DNSRecord.Service)record);
				if(evt != null)
					onServiceAdded(evt);
			}
			else if(record instanceof DNSRecord.Pointer) {
				// Check to see if we can resolve the service
				DNSRecord.Pointer ptrRecord = (DNSRecord.Pointer)record;
				DNSRecord.Service srvRecord = _Cache.getAssociatedService(ptrRecord);
				if(srvRecord == null) {
					// Query for the service information
					// TODO: Add a query task for this:
					DNSPacket packet = new DNSPacket(false);
			        packet.addQuestion(new DNSQuestion(ptrRecord.getAlias(), DNSEntry.EntryType.ANY, DNSEntry.EntryClass.IN, false));
			        _Socket.send(packet);
				}
			}
		}
	}
	
	private ServiceEvent getEventFromRecord(DNSRecord.Service aSrvRecord) {
		ServiceEvent retEvent = null;
		
		DNSRecord.Address addressRecord = _Cache.getAssociatedAddress(aSrvRecord);
		DNSRecord.Text textRecord = _Cache.getAssociatedText(aSrvRecord);
		
		if(addressRecord != null) {
			ServiceInfo srvInfo = new ServiceInfo(aSrvRecord);
			
			if(textRecord != null)
				srvInfo.setTextBytes(textRecord.getTextBytes());
			
			RemoteHostInfo hostInfo = new RemoteHostInfo(addressRecord);
			retEvent = new ServiceEvent(this, srvInfo, hostInfo);
		}
		
		return retEvent;
	}
	
	private void onServiceAdded(ServiceEvent aEvent)
    {
    	// Must search each key since you can search for services on many different levels,
		// inluding checking for all local services with "local."
		ServiceInfo srvInfo = aEvent.getSrvInfo();
		RemoteHostInfo hostInfo = aEvent.getHostInfo();
    	synchronized(_ServiceListeners) {
	    	for(String key : _ServiceListeners.keySet()) {
				if(srvInfo.getQualifiedName().endsWith(key)) {
					s_Logger.debug("Reporting new service (" + srvInfo.getQualifiedName() + "@" + hostInfo.getAddress() + ") to " + key + " listners.");
					List<ServiceListener> listeners = _ServiceListeners.get(key);
					for(ServiceListener listener : listeners) {
	    				listener.serviceAdded(aEvent);
	    			}
				}
			}
    	}
    }
	    
    private void onServiceRemoved(ServiceEvent aEvent) 
    {
    	ServiceInfo srvInfo = aEvent.getSrvInfo();
    	synchronized(_ServiceListeners) {
    		for(String key : _ServiceListeners.keySet()) {
				if(srvInfo.getQualifiedName().endsWith(key)) {
					s_Logger.debug("Reporting service removal (" + srvInfo.getQualifiedName() + ") to " + key +  " listners.");
					List<ServiceListener> listeners = _ServiceListeners.get(key);
					for(ServiceListener listener : listeners) {
	    				listener.serviceRemoved(aEvent);
	    			}
				}
    		}
    	}
    }
}
