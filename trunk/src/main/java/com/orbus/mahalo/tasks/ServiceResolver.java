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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.TimerTask;

import com.orbus.mahalo.MahaloSocket;
import com.orbus.mahalo.ServiceInfo;
import com.orbus.mahalo.dns.DNSEntry;
import com.orbus.mahalo.dns.DNSPacket;
import com.orbus.mahalo.dns.DNSQuestion;
import com.orbus.mahalo.dns.DNSRecord;

public class ServiceResolver extends TimerTask {
	public static final int INTERVAL = 225;
	
	private int _iCount = 0;
	private MahaloSocket _Socket;
	private String _sType;
	private List<ServiceInfo> _LocalServices;
	
	public ServiceResolver(MahaloSocket aSocket, String asType, Collection<ServiceInfo> aServiceList)
	{
		_Socket = aSocket;
		_sType = asType;
		_LocalServices = new LinkedList<ServiceInfo>(aServiceList);
	}
	
	@Override
	public void run() {
        _iCount++;
        DNSPacket outPacket = new DNSPacket(false);
        outPacket.addQuestion(new DNSQuestion(_sType, DNSEntry.EntryType.PTR, DNSEntry.EntryClass.IN, false));
        
        // Populate known answers to this query
        for (ServiceInfo info : _LocalServices)
        {
        	if(info.getType().equals(_sType))
        		outPacket.addAnswer(new DNSRecord.Pointer(_sType, DNSEntry.EntryType.PTR,
        			DNSEntry.EntryClass.IN, DNSEntry.TTL, info.getQualifiedName()));
        }
        // TODO: Populate known answers from the cache
        
        _Socket.send(outPacket);
		
		if(_iCount == 3)
			cancel();
	}
}
