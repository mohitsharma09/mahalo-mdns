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
package com.orbus.mahalo.tasks;

import java.util.LinkedList;
import java.util.List;
import java.util.TimerTask;

import com.orbus.mahalo.HostInfo;
import com.orbus.mahalo.MahaloSocket;
import com.orbus.mahalo.ServiceInfo;
import com.orbus.mahalo.dns.DNSEntry;
import com.orbus.mahalo.dns.DNSPacket;
import com.orbus.mahalo.dns.DNSRecord;

public class Announcer extends TimerTask {
	public static final int INTERVAL = 1000;
	
	private MahaloSocket _Socket;
	private HostInfo _LocalInfo;
	private List<ServiceInfo> _AnnounceList;
	
	public Announcer(MahaloSocket aSocket, HostInfo aLocalInfo, List<ServiceInfo> aAnnounceList) {
		_Socket = aSocket;
		_LocalInfo = aLocalInfo;
		_AnnounceList = aAnnounceList;
	}
	
	@Override
	public void run() {
		DNSPacket dnsMessage = new DNSPacket(true);
		dnsMessage.setAuthoritativeAnswer(true);

		List<ServiceInfo> removalList = new LinkedList<ServiceInfo>();
		
		boolean bannouncedLocalInfo = false;
		
		if(_LocalInfo.getState().isAnnouncing())
		{
			synchronized(_LocalInfo) {
				addLocalInfoRecord(dnsMessage);
			}
			
			bannouncedLocalInfo = true;
		}
            
        if(_AnnounceList != null && _AnnounceList.size() > 0) {
	        for (ServiceInfo info : _AnnounceList)
	        {
	        	synchronized(info) {
		            dnsMessage.addAnswer(new DNSRecord.Pointer(info.getType(), DNSEntry.EntryType.PTR, 
		            	DNSEntry.EntryClass.IN, DNSEntry.TTL, info.getQualifiedName()));
//		            dnsMessage.addAnswer(new DNSRecord.Pointer("_services._dns-sd._udp.local.", DNSEntry.EntryType.PTR, 
//			            	DNSEntry.EntryClass.IN, false, DNSConstants.DNS_TTL, info.getType()));
		            dnsMessage.addAnswer(new DNSRecord.Service(info.getQualifiedName(), 
		            	DNSEntry.EntryClass.IN, true, DNSEntry.TTL, info.getPriority(), info.getWeight(), 
		            	info.getPort(),_LocalInfo.getName()));
		            if(info.getTextBytes() != null) {
			            dnsMessage.addAnswer(new DNSRecord.Text(info.getQualifiedName(),
			            	DNSEntry.EntryClass.IN, true, DNSEntry.TTL, info.getTextBytes()));
		            }
		            info.advanceState();
		            if(info.getState().isAnnounced())
		            	removalList.add(info);
	        	}
	        }
	        
	        // If we haven't announced local information, announce it now, since some service depends on it.
	        if(!bannouncedLocalInfo)
	        	addLocalInfoRecord(dnsMessage);
        }
        
        for(ServiceInfo info : removalList)
        	_AnnounceList.remove(info);        	
        
        if(dnsMessage.getAnswers().size() > 0)
        	_Socket.send(dnsMessage);
        else
        	cancel();
	}
	
	private void addLocalInfoRecord(DNSPacket aMessage) {
		DNSRecord answer = _LocalInfo.getDNSAddressRecord(DNSEntry.EntryType.A);
        if(answer != null)
        	aMessage.addAnswer(answer);
        answer = _LocalInfo.getDNSAddressRecord(DNSEntry.EntryType.AAAA);
        if(answer != null)
        	aMessage.addAnswer(answer);
        _LocalInfo.advanceState();
	}

}
