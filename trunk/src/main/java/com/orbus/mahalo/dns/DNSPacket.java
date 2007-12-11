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
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DNSPacket {
	public static final int MAX_MSG_ABSOLUTE = 8972;
	public static final String MDNS_GROUP = "224.0.0.251";
	public static final int MDNS_PORT = 5353;
		
	private final static int FLAGS_QR_MASK = 0x8000;	// Query response mask
	private final static int FLAGS_QR_QUERY = 0x0000;	// Query
	private final static int FLAGS_QR_RESPONSE = 0x8000;	// Response

	private final static int FLAGS_AA = 0x0400;	// Authorative answer
	private final static int FLAGS_TC = 0x0200;	// Truncated
	private final static int FLAGS_RD = 0x0100;	// Recursion desired
	private final static int FLAGS_RA = 0x8000;	// Recursion available
	
	private long _iTimeRecieved;
	private int _iMessageId;
	private int _iFlags;
	private List<DNSQuestion> _Questions;
	private List<DNSRecord> _Answers;
	
	public long getRecieved() {
		return _iTimeRecieved;
	}
	
	 /**
     * Check if the message is a query.
     */
	public boolean isQuery() {
        return (_iFlags & FLAGS_QR_MASK) == FLAGS_QR_QUERY;
    }

    /**
     * Check if the message is truncated.
     */
    public boolean isTruncated() {
        return (_iFlags & FLAGS_TC) != 0;
    }

    /**
     * Check if the message is a response.
     */
    public boolean isResponse() {
        return (_iFlags & FLAGS_QR_MASK) == FLAGS_QR_RESPONSE;
    }
        
    public boolean isAuthoritativeAnswer() {
    	return (_iFlags & FLAGS_AA) != 0;
    }
    
    public void setAuthoritativeAnswer(boolean abValue) {
    	if(abValue)
    		_iFlags |= FLAGS_AA;
		else
			_iFlags &= ~FLAGS_AA;
    }
    
    public final List<DNSRecord> getAnswers() {
    	return _Answers;
    }
    
    public List<DNSQuestion> getQuestions() {
    	return _Questions;
    }
   
	public DNSPacket(boolean abResponce) {
		_iTimeRecieved = System.currentTimeMillis();
		
		if(abResponce)
			_iFlags = FLAGS_QR_RESPONSE;
		
		_Questions = new ArrayList<DNSQuestion>();
		_Answers = new ArrayList<DNSRecord>();
	}
	
	public void addAnswer(DNSRecord rec) {
		_Answers.add(rec);
	}
	
	public void addQuestion(DNSQuestion rec) {
		_Questions.add(rec);
	}
		
	public DNSPacket createResponce(boolean abMulticast)
	{
		DNSPacket responce = new DNSPacket(true);
		responce._iMessageId = _iMessageId; 
		if(!abMulticast)
			responce._Questions.addAll(_Questions);
		
		return responce;
	}
	
	public ByteBuffer[] write() {
		List<ByteBuffer> bufferList = new LinkedList<ByteBuffer>();
		ByteBuffer currentBuffer = ByteBuffer.allocate(MAX_MSG_ABSOLUTE); 
		bufferList.add(currentBuffer);
		
		// This moves the relative offset past the header so we can write what we need.
		currentBuffer.put(new byte[12]);
		int iquestionsRead = 0;
		int iquestionsWritten = 0;
		int ianswersRead = 0;
		int ianswersWritten = 0;
		while(iquestionsRead < _Questions.size())
		{
			try {
				DNSQuestion question = _Questions.get(iquestionsRead);
				question.write(currentBuffer);
				iquestionsRead++;
				iquestionsWritten++;
			} catch(BufferOverflowException e) {
				iquestionsWritten = 0;
				writeMessageHeader(currentBuffer, true, iquestionsWritten, ianswersWritten);
				currentBuffer = ByteBuffer.allocate(MAX_MSG_ABSOLUTE);
				bufferList.add(currentBuffer);
			}
		}
		
		while(ianswersRead < _Answers.size())
		{
			try {
				DNSRecord record = _Answers.get(ianswersRead);
				record.write(currentBuffer);
				ianswersRead++;
				ianswersWritten++;
			} catch(BufferOverflowException e) {
				ianswersWritten = 0;
				writeMessageHeader(currentBuffer, true, iquestionsWritten, ianswersWritten);
				currentBuffer = ByteBuffer.allocate(MAX_MSG_ABSOLUTE);
				bufferList.add(currentBuffer);
			}
		}
		
		writeMessageHeader(currentBuffer, false, iquestionsWritten, ianswersWritten);
		
		ByteBuffer[] retBuffers = new ByteBuffer[bufferList.size()];
		return (ByteBuffer[])bufferList.toArray(retBuffers);
	}
	
	private void writeMessageHeader(ByteBuffer aBuffer, boolean abTruncated, int aiQuestions, int aiAnswers) {
		aBuffer.putShort(0, (short)_iMessageId);
		if(abTruncated)
			aBuffer.putShort(2, (short)(_iFlags | FLAGS_TC));
		else
			aBuffer.putShort(2, (short)_iFlags);
	
		aBuffer.putShort(4, (short)aiQuestions);
		aBuffer.putShort(6, (short)aiAnswers);
		 
		// mDNS doesn't care about additional or authoritative answers.  All answers are equal in its mind,
		// so we only use the answers field ever.
		aBuffer.putShort(8, (short)0);
		aBuffer.putShort(10, (short)0);
	}
	
	public String toString()
	{
		StringBuffer buf = new StringBuffer();
		buf.append("DNSPacket[");
		buf.append(isQuery() ? "Query" : "Responce");
		if(isAuthoritativeAnswer())
			buf.append(" Authoratative");
		if(isTruncated())
			buf.append(" Truncated");
		buf.append(" Q:" + _Questions.size());
		buf.append(" A:" + _Answers.size());
		buf.append("]\n");
		if(_Questions.size() > 0) {
			buf.append("---- Questions ----\n");
			for(DNSQuestion question : _Questions)
				buf.append(question + "\n");
		}
		if(_Answers.size() > 0) {
			buf.append("---- Answers -----\n");
			for(DNSRecord rec : _Answers)
				buf.append(rec + "\n");
		}
		
		return buf.toString();
	}
	
	public static DNSPacket Parse(ByteBuffer aBuffer) throws IOException {
		DNSPacket thePacket = new DNSPacket(false);
		
        thePacket._iMessageId = aBuffer.getShort();
        thePacket._iFlags = aBuffer.getShort();
        int numQuestions = aBuffer.getShort();
        int numAnswers = aBuffer.getShort();
        int numAuthorities = aBuffer.getShort();
        int numAdditionals = aBuffer.getShort();

        // parse questions
        for(int i = 0; i < numQuestions; ++i) {
            DNSQuestion question = DNSQuestion.Parse(aBuffer); 
            thePacket._Questions.add(question);
        }

        // parse answers.  Auth and additional answers are considered regular
        // answers since all answers are equal in the mind of mDNS.
        numAnswers += numAuthorities + numAdditionals;
        for(int i = 0; i < numAnswers; ++i) {
            DNSRecord record = DNSRecord.Parse(aBuffer);
            if(record != null)
            	thePacket._Answers.add(record);
        }
        
		return thePacket;
	}
}
