package com.allantaborda.jmp3dec.spi;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat.Encoding;

public interface MpegConstants{
	int[] BIT_RATES = {8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 192, 224, 256, 320};
	float[] SAMPLE_RATES = {8000f, 11025f, 12000f, 16000f, 22050f, 24000f, 32000f, 44100f, 48000f};
	Encoding MPEG1L1 = new Encoding("MPEG1L1");
	Encoding MPEG2L1 = new Encoding("MPEG2L1");
	Encoding MPEG2DOT5L1 = new Encoding("MPEG2DOT5L1");
	Encoding MPEG1L2 = new Encoding("MPEG1L2");
	Encoding MPEG2L2 = new Encoding("MPEG2L2");
	Encoding MPEG2DOT5L2 = new Encoding("MPEG2DOT5L2");
	Encoding MPEG1L3 = new Encoding("MPEG1L3");
	Encoding MPEG2L3 = new Encoding("MPEG2L3");
	Encoding MPEG2DOT5L3 = new Encoding("MPEG2DOT5L3");
	Encoding PCM = Encoding.PCM_SIGNED;
	Type MP1 = new Type("MP1", "mp1");
	Type MP2 = new Type("MP2", "mp2");
	Type MP3 = new Type("MP3", "mp3");

	static boolean isEncodingSupported(Encoding e){
		return e.equals(MPEG1L3) || e.equals(MPEG2L3) || e.equals(MPEG2DOT5L3) ||
				e.equals(MPEG1L2) || e.equals(MPEG2L2) || e.equals(MPEG2DOT5L2) ||
				e.equals(MPEG1L1) || e.equals(MPEG2L1) || e.equals(MPEG2DOT5L1);
	}

	static boolean isTypeSupported(Type t){
		return t.equals(MP3) || t.equals(MP2) || t.equals(MP1);
	}
}