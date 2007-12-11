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

import java.net.InetAddress;
import java.util.Map;
import java.util.Random;
import java.util.TimerTask;

import org.apache.log4j.Logger;

import com.orbus.mahalo.HostInfo;
import com.orbus.mahalo.MahaloSocket;
import com.orbus.mahalo.ServiceInfo;
import com.orbus.mahalo.dns.DNSEntry;
import com.orbus.mahalo.dns.DNSPacket;
import com.orbus.mahalo.dns.DNSQuestion;
import com.orbus.mahalo.dns.DNSRecord;

public class Responder extends TimerTask {

	private static final int INTERVAL_MIN = 20;
	private static final int INTERVAL_MAX = 115;
	private static final Random s_Random = new Random();
	
	private static final Logger s_Logger = Logger.getLogger(Responder.class);	
	
	MahaloSocket _Socket;
	HostInfo _LocalInfo;
	Map<String, ServiceInfo> _LocalServices;
	DNSPacket _InPacket;
	InetAddress _Address;
	int _iPort;
	
	public Responder(MahaloSocket aSocket, HostInfo aLocalInfo, Map<String, ServiceInfo> aLocalServices, DNSPacket aInPacket,
			InetAddress aAddress, int aiPort) {
		_Socket = aSocket;
		_LocalServices = aLocalServices;
		_LocalInfo = aLocalInfo;
		_InPacket = aInPacket;
		_Address = aAddress;
		_iPort = aiPort;
	}
	
	@Override
	public void run() {
		// This automatically copies the questions to the output packet.
		DNSPacket outPacket = _InPacket.createResponce(true);
		//outPacket.setAuthoritativeAnswer(true);
		
        boolean bRecievedUnicast = (_iPort != DNSPacket.MDNS_PORT);
        Boolean bsendUnicast = null;

        for(DNSQuestion question : _InPacket.getQuestions()) {
        	if(bsendUnicast == null)
        		bsendUnicast = question.wantsUnicastResponce();
        	else if((bsendUnicast && !question.wantsUnicastResponce()) || (!bsendUnicast && question.wantsUnicastResponce()))
        		s_Logger.warn("Query from " + _Address + " is inconsistent with unicast response requests.  Sending responce multicast.");
        	bsendUnicast &= question.wantsUnicastResponce();
        	
            String squery = question.getName().toLowerCase();
            DNSEntry.EntryType questionType = question.getType();

            if(questionType == DNSEntry.EntryType.PTR) {
            	for(ServiceInfo info : _LocalServices.values()) {
        			if(info.getType().endsWith(squery) && info.getState().isAnnounced()) {
            			outPacket.addAnswer(new DNSRecord.Pointer(info.getType(), DNSEntry.EntryType.PTR, 
            				DNSEntry.EntryClass.IN, DNSEntry.TTL, info.getQualifiedName()));
            		}
            	}
            }
            else if(_LocalServices.containsKey(squery)){
            	ServiceInfo info = _LocalServices.get(squery);
            	if(info.getState().isAnnounced())
            	{
            		// TODO: Add support for AAAA / IPv6 queries
		            if(questionType == DNSEntry.EntryType.A || questionType == DNSEntry.EntryType.ANY) {
		            	DNSRecord answer = _LocalInfo.getDNSAddressRecord(DNSEntry.EntryType.A);
		            	if(answer != null)
		            		outPacket.addAnswer(answer);
		            }
		            if(questionType == DNSEntry.EntryType.SRV || questionType == DNSEntry.EntryType.ANY) {
		            	outPacket.addAnswer(new DNSRecord.Service(info.getQualifiedName(), DNSEntry.EntryClass.IN, 
		            			true, DNSEntry.TTL, info.getPriority(), info.getWeight(), info.getPort(), _LocalInfo.getName()));
		            }
		            if(questionType == DNSEntry.EntryType.TXT || questionType == DNSEntry.EntryType.ANY) {
		            	outPacket.addAnswer(new DNSRecord.Text(info.getQualifiedName(), DNSEntry.EntryClass.IN, true, 
		            			DNSEntry.TTL, info.getTextBytes()));
		            }
		            if(questionType == DNSEntry.EntryType.SRV) {
		            	DNSRecord answer = _LocalInfo.getDNSAddressRecord(DNSEntry.EntryType.A);
		            	if(answer != null)
		            		outPacket.addAnswer(answer);
		            }
            	}
            }
        }

        // remove known answers, if the ttl is at least half of
        // the correct value. (See Draft Cheshire chapter 7.1.).
        for (DNSRecord knownAnswer : _InPacket.getAnswers())
        {
            // TODO: This is incorrect.  The TTL isn't updated correctly and we should base it
        	// off of the ORIGINAL TTL sent, not our constant.
        	if (knownAnswer.getTTL() > DNSEntry.TTL / 2)
            {
            	outPacket.getAnswers().remove(knownAnswer);
                // TODO: Log known answer removal.
            }
        }

        // respond if we have answers
        if (outPacket.getAnswers().size() != 0)
        {
        	if((bRecievedUnicast || bsendUnicast) && _Address != null) {
        		outPacket.getQuestions().clear();
        		_Socket.send(outPacket, _Address, _iPort);
        	}
        	else
        		_Socket.send(outPacket);
        }
        else
        	s_Logger.trace("Found no responces to questions posed to the responder.");
	}
	
	public static int GetDelay(boolean abDelay, int aiElapsed)
	{
		if(!abDelay)
			return 0;
		
		int iret = INTERVAL_MIN + s_Random.nextInt(INTERVAL_MAX - INTERVAL_MIN + 1) - aiElapsed;
		return iret > 0 ? iret : 0;
	}
}
