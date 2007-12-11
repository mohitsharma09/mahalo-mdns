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

import com.orbus.mahalo.MahaloSocket;
import com.orbus.mahalo.HostInfo;
import com.orbus.mahalo.ServiceInfo;
import com.orbus.mahalo.dns.DNSEntry;
import com.orbus.mahalo.dns.DNSPacket;
import com.orbus.mahalo.dns.DNSRecord;

public class Canceler extends TimerTask {
	public static final int INTERVAL = 1000;
	
	private MahaloSocket _Socket;
	private HostInfo _LocalInfo;
	private int _iCount = 0;
	private List<ServiceInfo> _LocalServices;
	
	public Canceler(MahaloSocket aSocket, HostInfo aLocalInfo, List<ServiceInfo> aServices) {
		_Socket = aSocket;
		_LocalInfo = aLocalInfo;
		_LocalServices = new LinkedList<ServiceInfo>(aServices);
	}
	
	@Override
	public void run() {
		_iCount++;
		
		DNSPacket outPacket = new DNSPacket(true);
		outPacket.setAuthoritativeAnswer(true);
		
        for (ServiceInfo info : _LocalServices)
        {
            outPacket.addAnswer(new DNSRecord.Pointer(info.getType(), DNSEntry.EntryType.PTR, 
            		DNSEntry.EntryClass.IN, 0, info.getQualifiedName()));
            outPacket.addAnswer(new DNSRecord.Service(info.getQualifiedName(), DNSEntry.EntryClass.IN,
            		true, 0, info.getPriority(), info.getWeight(), info.getPort(), _LocalInfo.getName()));
            outPacket.addAnswer(new DNSRecord.Text(info.getQualifiedName(), DNSEntry.EntryClass.IN, 
            		true, 0, info.getTextBytes()));
        }
        
        _Socket.send(outPacket);
        
		if(_iCount == 2)
			cancel();
	}
	
	@Override
	public synchronized boolean cancel()
	{
		boolean bresult = super.cancel();
		
		notifyAll();
		
		return bresult;
	}

}
