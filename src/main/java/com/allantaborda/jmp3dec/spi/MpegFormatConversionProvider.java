/*
 *   MpegFormatConversionProvider.
 * 
 * JavaZOOM : mp3spi@javazoom.net
 * 			  http://www.javazoom.net
 * 
 * --------------------------------------------------------------------------- 
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
 * --------------------------------------------------------------------------
 */

package com.allantaborda.jmp3dec.spi;

import java.util.Arrays;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.spi.FormatConversionProvider;

/** ConversionProvider for MPEG files. */
public class MpegFormatConversionProvider extends FormatConversionProvider implements MpegConstants{
	private static final AudioFormat.Encoding[] srcEnc = {MPEG1L3, MPEG2L3, MPEG2DOT5L3, MPEG1L2, MPEG2L2, MPEG2DOT5L2, MPEG1L1, MPEG2L1, MPEG2DOT5L1};
	private static final AudioFormat.Encoding[] trgEnc = {PCM};
	private static final AudioFormat[] SOURCE_FORMATS;
	private static final List<AudioFormat.Encoding> encs = Arrays.asList(srcEnc);

	static{
		int c = -1;
		SOURCE_FORMATS = new AudioFormat[4 * srcEnc.length];
		for(AudioFormat.Encoding enc : srcEnc){
			SOURCE_FORMATS[++c] = new AudioFormat(enc, -1.0F, -1, 1, -1, -1.0F, false);
			SOURCE_FORMATS[++c] = new AudioFormat(enc, -1.0F, -1, 1, -1, -1.0F, true);
			SOURCE_FORMATS[++c] = new AudioFormat(enc, -1.0F, -1, 2, -1, -1.0F, false);
			SOURCE_FORMATS[++c] = new AudioFormat(enc, -1.0F, -1, 2, -1, -1.0F, true);
		}
	}

	public AudioFormat.Encoding[] getSourceEncodings(){
		return srcEnc;
	}

	public AudioFormat.Encoding[] getTargetEncodings(){
		return trgEnc;
	}

	public AudioFormat.Encoding[] getTargetEncodings(AudioFormat srcFormat){
		for(AudioFormat sf : SOURCE_FORMATS) if(sf.matches(srcFormat)) return trgEnc;
		return new AudioFormat.Encoding[0];
	}

	public AudioFormat[] getTargetFormats(AudioFormat.Encoding trgEnc, AudioFormat srcFormat){
		if(!encs.contains(srcFormat.getEncoding()) || !PCM.equals(trgEnc) || srcFormat.getChannels() > 2 || srcFormat.getChannels() < 1) return new AudioFormat[0];
		return new AudioFormat[]{new AudioFormat(PCM, -1.0F, 16, srcFormat.getChannels(), srcFormat.getChannels() * 2, -1.0F, false)};
	}

	public AudioInputStream getAudioInputStream(AudioFormat.Encoding trgEnc, AudioInputStream srcStream){
		return getAudioInputStream(new AudioFormat(trgEnc, -1.0F, 16, srcStream.getFormat().getChannels(), srcStream.getFormat().getChannels() * 2, -1.0F, false), srcStream);
	}

	public AudioInputStream getAudioInputStream(AudioFormat trgFormat, AudioInputStream srcStream){
		return new MpegAudioInputStream(trgFormat, srcStream);
	}
}