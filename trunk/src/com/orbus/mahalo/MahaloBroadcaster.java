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
import java.net.SocketException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import com.orbus.mahalo.dns.DNSEntry;
import com.orbus.mahalo.dns.DNSPacket;
import com.orbus.mahalo.dns.DNSQuestion;
import com.orbus.mahalo.dns.DNSRecord;
import com.orbus.mahalo.tasks.Canceler;
import com.orbus.mahalo.tasks.Prober;
import com.orbus.mahalo.tasks.Responder;

public class MahaloBroadcaster implements MahaloSocketListener {
	private boolean _bOwnsSocket;
	private MahaloSocket _MahaloSocket;
	private HostInfo _HostInfo;
	private Timer _Timer;
	
	private boolean _bStarted = false;
	
	private Map<String, ServiceInfo> _LocalServices = new HashMap<String, ServiceInfo>(20);
	
	public MahaloBroadcaster(InetAddress aAddress, String asName) throws IOException {
		this(new MahaloSocket(aAddress), asName);
	
		_bOwnsSocket = true; 
		_MahaloSocket.startListening();
	}
	
	public MahaloBroadcaster(MahaloSocket aSocket, String asName) throws SocketException {
		_bOwnsSocket = false;
		
		_MahaloSocket = aSocket;
		_MahaloSocket.addListener(this);
		_HostInfo = new HostInfo(_MahaloSocket.getBoundAddress(), asName);
		_Timer = new Timer();
	}
	
	public void start() {
		synchronized(_LocalServices) {
			Prober prober = new Prober(_MahaloSocket, _Timer, _HostInfo, _LocalServices.values());
	        _Timer.schedule(prober, Prober.GetStartProbeTime(), Prober.INTERVAL);
	        
	        _bStarted = true;
		}
	}
	
	public void stop() {
		synchronized(_LocalServices) {
			_Timer.cancel();
			_Timer = new Timer();
			Canceler unregisterCanceler = unregisterAllServices();
			if(unregisterCanceler!= null) {
				synchronized (unregisterCanceler) {
					try {
						unregisterCanceler.wait();
					} catch(InterruptedException e) { }
				}
			}
			
			if(_bOwnsSocket)
				_MahaloSocket.close();
			
			_bStarted = false;
		}
	}
	
	public void registerService(ServiceInfo aInfo) {
		//TODO: Check the service name with what's in the cache.
    	//makeServiceNameUnique(info);
    	
        synchronized (_LocalServices)
        {
            _LocalServices.put(aInfo.getQualifiedName().toLowerCase(), aInfo);
            
            if(_bStarted) {
    	        // We've already started things, so just create a new prober.
    	        List<ServiceInfo> probeList = new LinkedList<ServiceInfo>();
    	        probeList.add(aInfo);
    	        Prober prober = new Prober(_MahaloSocket, _Timer, _HostInfo, probeList);
    	        _Timer.schedule(prober, Prober.GetStartProbeTime(), Prober.INTERVAL);
            }
        }
	}
	
	public void unregisterService(ServiceInfo info)
    {
        synchronized (_LocalServices)
        {
            _LocalServices.remove(info.getQualifiedName().toLowerCase());
        }
        
        List<ServiceInfo> infoList = new LinkedList<ServiceInfo>();
        infoList.add(info);
        _Timer.schedule(new Canceler(_MahaloSocket, _HostInfo, infoList), 0, Canceler.INTERVAL);
    }
	
	public Canceler unregisterAllServices() { 
		if (_LocalServices.size() == 0)
            return null;
        
        List<ServiceInfo> infoList;
        synchronized (_LocalServices)
        {
        	infoList = new LinkedList<ServiceInfo>(_LocalServices.values());
            _LocalServices.clear();
        }
        
        Canceler retCanceler = new Canceler(_MahaloSocket, _HostInfo, infoList);
        _Timer.schedule(retCanceler, 0, Canceler.INTERVAL);
        
        return retCanceler;
	}
	
	public void handleQuery(DNSPacket aPacket, InetAddress aAddress, int aiPort) {
		resolveConflicts(aPacket);
		Responder responder = new Responder(_MahaloSocket, _HostInfo, _LocalServices, aPacket, aAddress, aiPort);
    	
    	// If I can answer every question in this query alone, respond immediately
    	boolean bonlyResponder = true;
    	for(DNSQuestion question : aPacket.getQuestions()) {
    		bonlyResponder &= (question.getType() == DNSEntry.EntryType.SRV
    				|| question.getType() == DNSEntry.EntryType.TXT
                    || question.getType() == DNSEntry.EntryType.A
                    || question.getType() == DNSEntry.EntryType.AAAA
                    || _HostInfo.getName().equalsIgnoreCase(question.getName())
                    || _LocalServices.containsKey(question.getName().toLowerCase()));
    		if(!bonlyResponder)
    			break;
    	}
    	int itimeElapsed = (int)(System.currentTimeMillis() - aPacket.getRecieved());
    	_Timer.schedule(responder, Responder.GetDelay(bonlyResponder, itimeElapsed));
	}
	
	public void handleResponse(DNSPacket aPacket) {
		resolveConflicts(aPacket);
	}
	
	private void resolveConflicts(DNSPacket aPacket) {
		// TODO: Conflict resolution
		for(DNSRecord answer : aPacket.getAnswers()) {
			// Compare against service:
			ServiceInfo info = _LocalServices.get(answer.getName().toLowerCase()); 
			if(info != null) {
				// TODO: Potential service conflict
			}
			
			if(_HostInfo.getName() == answer.getName()) {
				// TODO: Potential service conflict
			}
		}
	}
}
