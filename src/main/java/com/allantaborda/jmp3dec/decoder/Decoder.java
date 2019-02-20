/*
 * 11/19/04		1.0 moved to LGPL.
 * 01/12/99		Initial version.	mdm@techie.com
 *-----------------------------------------------------------------------
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
 *----------------------------------------------------------------------
 */

package com.allantaborda.jmp3dec.decoder;

import java.util.Objects;

/**
 * The <code>Decoder</code> class encapsulates the details of decoding an MPEG audio frame.
 * @author MDM
 * @version 0.0.7 12/12/99
 * @since 0.0.5
 */
public class Decoder implements DecoderErrors{
	private static final Params DEFAULT_PARAMS = new Params();
	/** The Obuffer instance that will receive the decoded PCM samples. */
	private Obuffer output;
	/** Synthesis filter for the left channel. */
	private SynthesisFilter filter1;
	/** Sythesis filter for the right channel. */
	private SynthesisFilter filter2;
	/** The decoder used to decode layer III frames. */
	private LayerIIIDecoder l3decoder;
	private LayerIIDecoder l2decoder;
	private LayerIDecoder l1decoder;
	private int outputFrequency;
	private int outputChannels;
	private Equalizer equalizer = new Equalizer();
	private Params params;
	private boolean initialized;

	/** Creates a new <code>Decoder</code> instance with default parameters. */
	public Decoder(){
		this(DEFAULT_PARAMS);
	}

	/**
	 * Creates a new <code>Decoder</code> instance with default parameters.
	 * @param params The <code>Params</code> instance that describes the customizable aspects of the decoder.
	 */
	public Decoder(Params params0){
		if(params0 == null) params0 = DEFAULT_PARAMS;
		params = params0;
		Equalizer eq = params.getInitialEqualizerSettings();
		if(eq != null) equalizer.setFrom(eq);
	}

	public static Params getDefaultParams(){
		return (Params) DEFAULT_PARAMS.clone();
	}

	public void setEqualizer(Equalizer eq){
		if(eq == null) eq = Equalizer.PASS_THRU_EQ;
		equalizer.setFrom(eq);
		float[] factors = equalizer.getBandFactors();
		if(filter1 != null) filter1.setEQ(factors);
		if(filter2 != null) filter2.setEQ(factors);
	}

	/**
	 * Decodes one frame from an MPEG audio bitstream.
	 * @param header The header describing the frame to decode.
	 * @param bitstream The bistream that provides the bits for te body of the frame.
	 * @return A SampleBuffer containing the decoded samples.
	 */
	public Obuffer decodeFrame(Header header, Bitstream stream) throws DecoderException{
		if(!initialized) initialize(header);
		int layer = header.layer();
		output.clear_buffer();
		retrieveDecoder(header, stream, layer).decodeFrame();
		return output;
	}

	/** Changes the output buffer. This will take effect the next time decodeFrame() is called. */
	public void setOutputBuffer(Obuffer out){
		output = out;
	}

	/**
	 * Retrieves the sample frequency of the PCM samples output by this decoder. This typically corresponds to the sample rate encoded in the MPEG audio stream.
	 * @return The sample rate (in Hz) of the samples written to the output buffer when decoding.
	 */
	public int getOutputFrequency(){
		return outputFrequency;
	}

	/**
	 * Retrieves the number of channels of PCM samples output by this decoder.
	 * This usually corresponds to the number of channels in the MPEG audio stream, although it may differ.
	 * @return The number of output channels in the decoded samples: 1 for mono, or 2 for stereo.
	 */
	public int getOutputChannels(){
		return outputChannels;
	}

	protected DecoderException newDecoderException(int errorcode){
		return new DecoderException(errorcode, null);
	}

	protected DecoderException newDecoderException(int errorcode, Throwable throwable){
		return new DecoderException(errorcode, throwable);
	}

	protected FrameDecoder retrieveDecoder(Header header, Bitstream stream, int layer) throws DecoderException{
		if(layer < 1 || layer > 3) throw newDecoderException(UNSUPPORTED_LAYER, null);
		// REVIEW: allow channel output selection type (LEFT, RIGHT, BOTH, DOWNMIX)
		if(layer == 3){
			if(l3decoder == null) l3decoder = new LayerIIIDecoder(stream, header, filter1, filter2, output, OutputChannels.BOTH_CHANNELS);
			return l3decoder;
		}else if(layer == 2){
			if(l2decoder == null) l2decoder = new LayerIIDecoder(stream, header, filter1, filter2, output, OutputChannels.BOTH_CHANNELS);
			return l2decoder;
		}else{
			if(l1decoder == null) l1decoder = new LayerIDecoder(stream, header, filter1, filter2, output, OutputChannels.BOTH_CHANNELS);
			return l1decoder;
		}
	}

	private void initialize(Header header) throws DecoderException{
		// REVIEW: allow customizable scale factor
		float scalefactor = 32700.0f;
		outputChannels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
		// set up output buffer if not set up by client.
		if(output == null) output = new SampleBuffer(header.frequency(), outputChannels);
		float[] factors = equalizer.getBandFactors();
		filter1 = new SynthesisFilter(0, scalefactor, factors);
		// REVIEW: allow mono output for stereo
		if(outputChannels == 2) filter2 = new SynthesisFilter(1, scalefactor, factors);
		outputFrequency = header.frequency();
		initialized = true;
	}

	/**
	 * The <code>Params</code> class presents the customizable aspects of the decoder.
	 * <p>
	 * Instances of this class are not thread safe.
	 */
	public static class Params implements Cloneable{
		private OutputChannels outputChannels = OutputChannels.BOTH;
		private Equalizer equalizer = new Equalizer();

		public Params(){
		}

		public Object clone(){
			try{
				return super.clone();
			}catch(CloneNotSupportedException ex){
				throw new InternalError(this + ": " + ex);
			}
		}

		public OutputChannels getOutputChannels(){
			return outputChannels;
		}

		public void setOutputChannels(OutputChannels out){
			outputChannels = Objects.requireNonNull(out);
		}

		/**
		 * Retrieves the equalizer settings that the decoder's equalizer will be initialized from.
		 * <p>
		 * The <code>Equalizer</code> instance returned cannot be changed in real time to affect the decoder output as it is used only to initialize
		 * the decoders EQ settings. To affect the decoder's output in realtime, use the Equalizer returned from the getEqualizer() method on the decoder.
		 * @return The <code>Equalizer</code> used to initialize the EQ settings of the decoder.
		 */
		public Equalizer getInitialEqualizerSettings(){
			return equalizer;
		}
	}
}