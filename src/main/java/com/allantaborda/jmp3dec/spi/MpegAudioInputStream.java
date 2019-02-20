/*
 *   MpegAudioInputStream.
 * 
 *   JavaZOOM : mp3spi@javazoom.net 
 * 				http://www.javazoom.net
 *
 *-----------------------------------------------------------------------------
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *------------------------------------------------------------------------
 */

package com.allantaborda.jmp3dec.spi;

import com.allantaborda.jmp3dec.decoder.Bitstream;
import com.allantaborda.jmp3dec.decoder.BitstreamException;
import com.allantaborda.jmp3dec.decoder.Decoder;
import com.allantaborda.jmp3dec.decoder.DecoderException;
import com.allantaborda.jmp3dec.decoder.Equalizer;
import com.allantaborda.jmp3dec.decoder.Header;
import com.allantaborda.jmp3dec.decoder.Obuffer;
import com.allantaborda.jmp3dec.tag.IcyListener;
import com.allantaborda.jmp3dec.tag.TagParseEvent;
import com.allantaborda.jmp3dec.tag.TagParseListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

/** Main decoder. */
public class MpegAudioInputStream extends AudioInputStream implements TagParseListener{
	private TCircularBuffer m_circularBuffer;
	private byte[] m_abSingleByte;
	private InputStream m_encodedStream;
	private Bitstream m_bitstream;
	private Decoder m_decoder;
	private Equalizer m_equalizer;
	private float[] m_equalizer_values;
	private Header m_header;
	private DMAISObuffer m_oBuffer;
	// Bytes info
	private long byteslength = -1;
	private long currentByte;
	// Frame info
	private int frameslength = -1;
	private long currentFrame;
	private int currentFramesize;
	private int currentBitrate = -1;
	// Time info
	private long currentMicrosecond;
	// Shoutcast stream info
	private IcyListener shoutlst;
	private HashMap<String, Object> properties;

	public MpegAudioInputStream(AudioFormat outputFormat, AudioInputStream inputStream){
		/*
		 * The usage of a ByteArrayInputStream is a hack. (the infamous "JavaOne hack", because I did it on June 6th 2000 in San Francisco, only hours
		 * before a JavaOne session where I wanted to show mp3 playback with Java Sound.) It is necessary because in the FCS version of the Sun jdk1.3,
		 * the constructor of AudioInputStream throws an exception if its first argument is null. So we have to pass a dummy non-null value.
		 */
		super(new ByteArrayInputStream(new byte[0]), outputFormat, -1);
		m_circularBuffer = new TCircularBuffer(this);
		try{
			// Try to find out inputstream length to allow skip.
			byteslength = inputStream.available();
		}catch(IOException e){
			System.err.println("MpegAudioInputStream : Cannot run inputStream.available() : " + e.getMessage());
			byteslength = -1;
		}
		m_encodedStream = inputStream;
		shoutlst = IcyListener.getInstance();
		shoutlst.reset();
		m_bitstream = new Bitstream(inputStream);
		m_decoder = new Decoder(null);
		m_equalizer = new Equalizer();
		m_equalizer_values = new float[32];
		for(int b = 0; b < m_equalizer.getBandCount(); b++) m_equalizer_values[b] = m_equalizer.getBand(b);
		m_decoder.setEqualizer(m_equalizer);
		m_oBuffer = new DMAISObuffer(outputFormat.getChannels());
		m_decoder.setOutputBuffer(m_oBuffer);
		try{
			m_header = m_bitstream.readFrame();
			if(m_header != null && frameslength == -1 && byteslength > 0) frameslength = m_header.max_number_of_frames((int) byteslength);
		}catch(BitstreamException e){
			System.err.println("MpegAudioInputStream : Cannot read first frame : " + e.getMessage());
			byteslength = -1;
		}
		properties = new HashMap<>();
	}

	public int read() throws IOException{
		int nByte = -1;
		if(m_abSingleByte == null) m_abSingleByte = new byte[1];
		int nReturn = read(m_abSingleByte);
		if(nReturn == -1) nByte = -1;
		else nByte = m_abSingleByte[0] & 0xFF; // $$fb 2001-04-14 nobody really knows that...
		return nByte;
	}

	@Override
	public int read(byte[] abData) throws IOException{
		return read(abData, 0, abData.length);
	}

	@Override
	public int read(byte[] abData, int nOffset, int nLength) throws IOException{
		// $$fb 2001-04-22: this returns at maximum circular buffer length. This is not very efficient...
		// $$fb 2001-04-25: we should check that we do not exceed getFrameLength()!
		return m_circularBuffer.read(abData, nOffset, nLength);
	}

	public int available() throws IOException{
		return m_circularBuffer.availableRead();
	}

	public boolean markSupported(){
		return false;
	}

	public void mark(int nReadLimit){
	}

	public void reset() throws IOException{
		throw new IOException("mark not supported");
	}

	/**
	 * Return dynamic properties.
	 * <ul>
	 * <li><b>mp3.frame</b> [Long], current frame position.
	 * <li><b>mp3.frame.bitrate</b> [Integer], bitrate of the current frame.
	 * <li><b>mp3.frame.size.bytes</b> [Integer], size in bytes of the current
	 * frame.
	 * <li><b>mp3.position.byte</b> [Long], current position in bytes in the
	 * stream.
	 * <li><b>mp3.position.microseconds</b> [Long], elapsed microseconds.
	 * <li><b>mp3.equalizer</b> float[32], interactive equalizer array, values
	 * could be in [-1.0, +1.0].
	 * <li><b>mp3.shoutcast.metadata.key</b> [String], Shoutcast meta key with
	 * matching value. <br>
	 * For instance : <br>
	 * mp3.shoutcast.metadata.StreamTitle=Current song playing in stream. <br>
	 * mp3.shoutcast.metadata.StreamUrl=Url info.
	 * </ul>
	 */
	public Map<String, Object> properties(){
		properties.put("mp3.frame", currentFrame);
		properties.put("mp3.frame.bitrate", currentBitrate);
		properties.put("mp3.frame.size.bytes", currentFramesize);
		properties.put("mp3.position.byte", currentByte);
		properties.put("mp3.position.microseconds", currentMicrosecond);
		properties.put("mp3.equalizer", m_equalizer_values);
		// Optional shoutcast stream meta-data.
		if(shoutlst != null){
			String surl = shoutlst.getStreamUrl(), stitle = shoutlst.getStreamTitle();
			if(stitle != null && !stitle.trim().isEmpty()) properties.put("mp3.shoutcast.metadata.StreamTitle", stitle);
			if(surl != null && !surl.trim().isEmpty()) properties.put("mp3.shoutcast.metadata.StreamUrl", surl);
		}
		return properties;
	}

	public void execute(){
		try{
			// Following line hangs when FrameSize is available in AudioFormat.
			Header header = null;
			if(m_header == null) header = m_bitstream.readFrame();
			else header = m_header;
			if(header == null){
				m_circularBuffer.close();
				return;
			}
			currentFrame++;
			currentBitrate = header.bitrate_instant();
			currentFramesize = header.calculate_framesize();
			currentByte = currentByte + currentFramesize;
			currentMicrosecond = (long) (currentFrame * header.ms_per_frame() * 1000.0f);
			for(int b = 0; b < m_equalizer_values.length; b++) m_equalizer.setBand(b, m_equalizer_values[b]);
			m_decoder.setEqualizer(m_equalizer);
			m_decoder.decodeFrame(header, m_bitstream);
			m_bitstream.closeFrame();
			m_circularBuffer.write(m_oBuffer.getBuffer(), 0, m_oBuffer.getCurrentBufferSize());
			m_oBuffer.reset();
			if(m_header != null) m_header = null;
		}catch(BitstreamException | DecoderException e){
			System.err.println(e);
		}
	}

	public long skip(long bytes){
		if(byteslength > 0 && frameslength > 0){
			long bytesread = skipFrames((long) ((bytes * 1.0f / byteslength * 1.0f) * frameslength));
			currentByte = currentByte + bytesread;
			m_header = null;
			return bytesread;
		}
		return -1;
	}

	/**
	 * Skip frames. You don't need to call it severals times, it will exactly skip given frames number.
	 * @param frames The frame number to skip.
	 * @return Bytes length skipped matching to frames skipped.
	 */
	public long skipFrames(long frames){
		int framesRead = 0, bytesReads = 0;
		try{
			for(int i = 0; i < frames; i++){
				Header header = m_bitstream.readFrame();
				if(header != null) bytesReads = bytesReads + header.calculate_framesize();
				m_bitstream.closeFrame();
				framesRead++;
			}
		}catch(BitstreamException e){
			System.err.println(e);
		}
		currentFrame = currentFrame + framesRead;
		return bytesReads;
	}

	public void close() throws IOException{
		m_circularBuffer.close();
		m_encodedStream.close();
	}

	public void tagParsed(TagParseEvent tpe){
		System.out.println("TAG:" + tpe.getTag());
	}

	private class DMAISObuffer implements Obuffer{
		private int m_nChannels;
		private byte[] m_abBuffer;
		private int[] m_anBufferPointers;
		private boolean m_bIsBigEndian;

		public DMAISObuffer(int nChannels){
			m_nChannels = nChannels;
			m_abBuffer = new byte[OBUFFERSIZE * nChannels];
			m_anBufferPointers = new int[nChannels];
			reset();
			m_bIsBigEndian = MpegAudioInputStream.this.getFormat().isBigEndian();
		}

		public void append(int nChannel, short sValue){
			byte bFirstByte, bSecondByte;
			if(m_bIsBigEndian){
				bFirstByte = (byte) ((sValue >>> 8) & 0xFF);
				bSecondByte = (byte) (sValue & 0xFF);
			}else{ // little endian
				bFirstByte = (byte) (sValue & 0xFF);
				bSecondByte = (byte) ((sValue >>> 8) & 0xFF);
			}
			m_abBuffer[m_anBufferPointers[nChannel]] = bFirstByte;
			m_abBuffer[m_anBufferPointers[nChannel] + 1] = bSecondByte;
			m_anBufferPointers[nChannel] += m_nChannels * 2;
		}

		public void appendSamples(int channel, float[] f){
			for(int c = 0; c < 32;){
				float sample = f[c++];
				// Clip Sample to 16 Bits.
				append(channel, sample > 32767.0f ? 32767 : sample < -32768.0f ? -32768 : (short) sample); 
			}
		}

		public void clear_buffer(){
		}

		public byte[] getBuffer(){
			return m_abBuffer;
		}

		public int getCurrentBufferSize(){
			return m_anBufferPointers[0];
		}

		public void reset(){
			for(int i = 0; i < m_nChannels; i++){
				/* Points to byte location, implicitely assuming 16 bit samples. */
				m_anBufferPointers[i] = i * 2;
			}
		}
	}

	/*
	 *	TCircularBuffer.java
	 *
	 *	This file WAS part of Tritonus: http://www.tritonus.org/
	 *
	 *  Copyright (c) 1999 by Matthias Pfisterer
	 *
	 *   This program is free software; you can redistribute it and/or modify
	 *   it under the terms of the GNU Library General Public License as published
	 *   by the Free Software Foundation; either version 2 of the License, or
	 *   (at your option) any later version.
	 *
	 *   This program is distributed in the hope that it will be useful,
	 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
	 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	 *   GNU Library General Public License for more details.
	 *
	 *   You should have received a copy of the GNU Library General Public
	 *   License along with this program; if not, write to the Free Software
	 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
	 */
	private class TCircularBuffer{
		private final int m_nSize = 327670;
		private byte[] m_abData;
		private long m_lReadPos;
		private long m_lWritePos;
		private MpegAudioInputStream m_trigger;
		private boolean m_bOpen;

		public TCircularBuffer(MpegAudioInputStream trigger){
			m_abData = new byte[m_nSize];
			m_trigger = trigger;
			m_bOpen = true;
		}

		public void close(){
			m_bOpen = false;
		}

		public int availableRead(){
			return (int) (m_lWritePos - m_lReadPos);
		}

		public int availableWrite(){
			return m_nSize - availableRead();
		}

		public int read(byte[] abData, int nOffset, int nLength){
			if(!m_bOpen){
				if(availableRead() > 0){
					nLength = Math.min(nLength, availableRead());
				}else{
					System.err.println("< not open. returning -1.");
					return -1;
				}
			}
			synchronized(this){
				if(m_trigger != null && availableRead() < nLength) m_trigger.execute();
				nLength = Math.min(availableRead(), nLength);
				int nRemainingBytes = nLength;
				while(nRemainingBytes > 0){
					while(availableRead() == 0){
						try{
							wait();
						}catch(InterruptedException e){
							System.err.println(e);
						}
					}
					int nAvailable = Math.min(availableRead(), nRemainingBytes);
					while(nAvailable > 0){
						int nToRead = Math.min(nAvailable, m_nSize - ((int) (m_lReadPos % m_nSize)));
						System.arraycopy(m_abData, (int) (m_lReadPos % m_nSize), abData, nOffset, nToRead);
						m_lReadPos += nToRead;
						nOffset += nToRead;
						nAvailable -= nToRead;
						nRemainingBytes -= nToRead;
					}
					notifyAll();
				}
				return nLength;
			}
		}

		public int write(byte[] abData, int nOffset, int nLength){
			synchronized(this){
				int nRemainingBytes = nLength;
				while(nRemainingBytes > 0){
					while(availableWrite() == 0){
						try{
							wait();
						}catch(InterruptedException e){
							System.err.println(e);
						}
					}
					int nAvailable = Math.min(availableWrite(), nRemainingBytes);
					while(nAvailable > 0){
						int nToWrite = Math.min(nAvailable, m_nSize - ((int) (m_lWritePos % m_nSize)));
						System.arraycopy(abData, nOffset, m_abData, (int) (m_lWritePos % m_nSize), nToWrite);
						m_lWritePos += nToWrite;
						nOffset += nToWrite;
						nAvailable -= nToWrite;
						nRemainingBytes -= nToWrite;
					}
					notifyAll();
				}
				return nLength;
			}
		}
	}
}