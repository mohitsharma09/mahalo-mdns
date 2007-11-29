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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import com.orbus.mahalo.HostInfo;
import com.orbus.mahalo.MahaloSocket;
import com.orbus.mahalo.ServiceInfo;
import com.orbus.mahalo.ServiceState;
import com.orbus.mahalo.dns.DNSEntry;
import com.orbus.mahalo.dns.DNSPacket;
import com.orbus.mahalo.dns.DNSQuestion;
import com.orbus.mahalo.dns.DNSRecord;

public class Prober extends TimerTask {
	public static final int INTERVAL = 250;

	private static final Random s_Random = new Random();

	private MahaloSocket _Socket;
	private Timer _Timer;
	private HostInfo _HostInfo;	

	private List<ServiceInfo> _ProbeList;

	public Prober(MahaloSocket aSocket, Timer aTimer, HostInfo aHostInfo, Collection<ServiceInfo> aProbeList) {
		_Socket = aSocket;
		_Timer = aTimer;
		_HostInfo = aHostInfo;
		if (aProbeList != null)
			_ProbeList = new ArrayList<ServiceInfo>(aProbeList);
		else
			_ProbeList = null;
	}

	@Override
	public void run() {
		List<ServiceInfo> announceList = new LinkedList<ServiceInfo>();
		DNSPacket outPacket = new DNSPacket(false);

		// First try to probe our host name if that's been supplied
		if (_HostInfo.getState().isProbing()) {
			synchronized (_HostInfo) {
				DNSQuestion question = new DNSQuestion(_HostInfo.getName(), DNSEntry.EntryType.ANY, DNSEntry.EntryClass.IN, false);
				if(_HostInfo.getState() == ServiceState.PROBING_1 || _HostInfo.getState() == ServiceState.PROBING_2)
					question.setWantsUnicastResponce(true);
				outPacket.addQuestion(question);
						
				DNSRecord answer = _HostInfo.getDNSAddressRecord(DNSEntry.EntryType.A);
				if (answer != null)
					outPacket.addAnswer(answer);
				_HostInfo.advanceState();
			}
		}

		if (_ProbeList != null && _ProbeList.size() > 0) {
			// Next, send probes for any services
			for (ServiceInfo info : _ProbeList) {
				if (info.getState().isProbing()) {
					synchronized (info) {
						DNSQuestion question = new DNSQuestion(info.getQualifiedName(),
							DNSEntry.EntryType.ANY, DNSEntry.EntryClass.IN, false);
						
						// According to standard, the first two probes should request unicast responces.
						// See Cheshire draft 9.1, page 21, P1
						if(info.getState() == ServiceState.PROBING_1 || info.getState() == ServiceState.PROBING_2)
							question.setWantsUnicastResponce(true);
						
						outPacket.addQuestion(question);
						outPacket.addAnswer(new DNSRecord.Service(info.getQualifiedName(),
										DNSEntry.EntryClass.IN, false, DNSEntry.TTL, 
										info.getPriority(), info.getWeight(), info.getPort(),
										_HostInfo.getName()));
						info.advanceState();
						if (info.getState().isAnnouncing())
							announceList.add(info);
					}
				}
			}

			// Remove all announcing service infos
			for (ServiceInfo info : announceList)
				_ProbeList.remove(info);
		}

		// Send the probe
		if (outPacket.getQuestions().size() != 0)
			_Socket.send(outPacket);
		else
			cancel(); // Nothing else to do.

		if (announceList.size() > 0)
			_Timer.schedule(new Announcer(_Socket, _HostInfo, announceList),
					Announcer.INTERVAL, Announcer.INTERVAL);
	}

	public static int GetStartProbeTime() {
		return s_Random.nextInt(INTERVAL);
	}
}
