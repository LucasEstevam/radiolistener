package br.com.radiolistener.Listener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.IMediaViewer;
import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.MediaToolAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.xuggler.Global;
import com.xuggle.xuggler.IAudioResampler;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IContainerFormat;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;
import com.xuggle.xuggler.IAudioSamples.Format;

//Adapted from xuggler Converter
public class Converter extends MediaToolAdapter
{
	  private final Logger log = LoggerFactory.getLogger(this.getClass());	


	 private void writePacket(IPacket oPacket, ByteBuffer result)
	  {
	    int retval;
	    if (oPacket.isComplete())
	    {	     
	      /**
	       * If we got a complete packet out of the encoder, then go ahead
	       * and write it to the container.
	       */	 
	    	ByteBuffer buf = oPacket.getByteBuffer();
	    	result.put(buf);	    
	    	
//	      retval = mOContainer.writePacket(oPacket);
//	      if (retval < 0)
//	        throw new RuntimeException("could not write output packet");
	    }
	  }
	
	
	public ByteBuffer Convert(ByteBuffer buffer)
	{
		if(buffer.position() > 0)
		{
			//assume it's at the last position
			buffer.flip();
		}
		
		ByteBuffer result = ByteBuffer.allocate(1024*1024);
		
		
		//setup input container
		IContainer mIContainer = IContainer.make();
		InputStream iStream = new ByteBufferBackedInputStream(buffer);
		IContainerFormat iFmt = null;
		iFmt = IContainerFormat.make();
		mIContainer.open(iStream, iFmt);
		
		//setup output container		
		IContainer mOContainer = IContainer.make();
		ByteArrayOutputStream outstream = new ByteArrayOutputStream();
		IContainerFormat oFmt = IContainerFormat.make();
		oFmt.setOutputFormat("s16le", "out2.wav", null);
		mOContainer.open(outstream,oFmt);
		
		int numStreams = mIContainer.getNumStreams();
		
		IStream mIStream;
		IStreamCoder mICoder;
		IStream mOStream;
		IStreamCoder mOCoder;
		IAudioResampler mASampler;		
		IAudioSamples mISamples;
		IAudioSamples mOSamples;
		
		if(numStreams == 1)
		{
			IStream is = mIContainer.getStream(0);
			
			IStreamCoder ic = is.getStreamCoder();
			
			ICodec.Type cType = ic.getCodecType();
			
			mIStream = is;
			mICoder = ic;
			
			mOStream = null;
			mOCoder = null;
			
			mASampler = null;
			
			mISamples = null;
			mOSamples = null;
			
			ICodec codec = null;
			
			codec = ICodec.guessEncodingCodec(oFmt, null, null, null, cType);
		
			IStream os = mOContainer.addNewStream(codec);
			
			IStreamCoder oc = os.getStreamCoder();
			
			mOStream = os;
			mOCoder = oc;
			
			Format preferredFormat = ic.getSampleFormat();
			
			List<Format> formats = codec.getSupportedAudioSampleFormats();
	        for(Format format : formats) {
	          oc.setSampleFormat(format);
	          if (format == preferredFormat)
	            break;
	        }
	        
	        oc.setSampleRate(11025);
	        
	        oc.setChannels(1);
	        
	        if(oc.getSampleRate() != ic.getSampleRate() || oc.getSampleFormat() != ic.getSampleFormat())
	        {
	        	mASampler = IAudioResampler.make(oc.getChannels(), ic.getChannels(), oc.getSampleRate(), ic.getSampleRate(), oc.getSampleFormat(), ic.getSampleFormat());
	        	if(mASampler == null)
	        	{
	        		throw new RuntimeException("could not open audio resampler");
	        	}
	        	
	        }
	        
	        mISamples = IAudioSamples.make(1024, ic.getChannels(),ic.getSampleFormat());
	        mOSamples = IAudioSamples.make(1024, oc.getChannels(),oc.getSampleFormat());
			
			
		}else
		{
			throw new RuntimeException("wrong number of streams");
		}
		
		if(mOCoder != null)
		{
			int retval = mOCoder.setStandardsCompliance(IStreamCoder.CodecStandardsCompliance.COMPLIANCE_EXPERIMENTAL);
	        if (retval < 0)
	          throw new RuntimeException ("could not set compliance mode to experimental");
	        
	        retval = mOCoder.open(null, null);
	        if (retval < 0)
	          throw new RuntimeException(
	              "could not open output encoder for stream: ");
	        retval = mICoder.open(null, null);
	        if (retval < 0)
	          throw new RuntimeException(
	              "could not open input decoder for stream: ");
		}
		int retval =0;
//		int retval = mOContainer.writeHeader();
//	    if (retval < 0)
//	      throw new RuntimeException("Could not write header for: ");
				
		IPacket iPacket = IPacket.make();
		IPacket oPacket = IPacket.make();
		
		IAudioSamples inSamples = null;
	    IAudioSamples outSamples = null;
	    IAudioSamples reSamples = null;
	    
	    retval = 0;
	    
	    IStreamCoder ic = null;
	    IStreamCoder oc = null;
	    IAudioResampler as = null;	    
	    
	    while(mIContainer.readNextPacket(iPacket) == 0)
	    {
	    	int i = iPacket.getStreamIndex();
	    	int offset = 0;
	    	
	    	IStream stream = mIContainer.getStream(i);
	    	
	    	 long tsOffset = 0;
	         if (stream.getStartTime() != Global.NO_PTS && stream.getStartTime() > 0
	             && stream.getTimeBase() != null)
	         {
	           IRational defTimeBase = IRational.make(1,
	               (int) Global.DEFAULT_PTS_PER_SECOND);
	           tsOffset = defTimeBase.rescale(stream.getStartTime(), stream
	               .getTimeBase());
	         }        
	    	    
		    ic = mICoder;
		    oc = mOCoder;
		    as = mASampler;	    
		    inSamples = mISamples;
		    reSamples = mOSamples;
		    
		    ICodec.Type cType = ic.getCodecType();
			
		    if(cType == ICodec.Type.CODEC_TYPE_AUDIO)
		    {
		    	while (offset < iPacket.getSize())
		        {
		          retval = ic.decodeAudio(inSamples, iPacket, offset);
		          if (retval <= 0)
		            throw new RuntimeException("could not decode audio.  stream: " + i);
	
		          if (inSamples.getTimeStamp() != Global.NO_PTS)
		            inSamples.setTimeStamp(inSamples.getTimeStamp() - tsOffset);
	
		          log.trace("packet:{}; samples:{}; offset:{}", new Object[]
		          {
		              iPacket, inSamples, tsOffset
		          });
	
		          /**
		           * If not an error, the decodeAudio returns the number of bytes it
		           * consumed. We use that so the next time around the loop we get new
		           * data.
		           */
		          offset += retval;
		          int numSamplesConsumed = 0;
		          /**
		           * If as is not null then we know a resample was needed, so we do that
		           * resample now.
		           */
		          if (as != null && inSamples.getNumSamples() > 0)
		          {
		            retval = as.resample(reSamples, inSamples, inSamples
		                .getNumSamples());
	
		            outSamples = reSamples;
		          }
		          else
		          {
		            outSamples = inSamples;
		          }
		          
		          /**
		           * Now that we've resampled, it's time to encode the audio.
		           * 
		           * This workflow is similar to decoding; you may have more, less or
		           * just enough audio samples available to encode a packet. But you
		           * must iterate through.
		           * 
		           * Unfortunately (don't ask why) there is a slight difference between
		           * encodeAudio and decodeAudio; encodeAudio returns the number of
		           * samples consumed, NOT the number of bytes. This can be confusing,
		           * and we encourage you to read the IAudioSamples documentation to
		           * find out what the difference is.
		           * 
		           * But in any case, the following loop encodes the samples we have
		           * into packets.
		           */
		          while (numSamplesConsumed < outSamples.getNumSamples())
		          {
		            retval = oc.encodeAudio(oPacket, outSamples, numSamplesConsumed);
		            if (retval <= 0)
		              throw new RuntimeException("Could not encode any audio: "
		                  + retval);
		            /**
		             * Increment the number of samples consumed, so that the next time
		             * through this loop we encode new audio
		             */
		            numSamplesConsumed += retval;
		            log.trace("out packet:{}; samples:{}; offset:{}", new Object[]{
		                oPacket, outSamples, tsOffset
		            });
	
		            writePacket(oPacket, result);
		          }
		        }
		    	
		    	
		    	
		    }
		    else
		    {
		    	throw new RuntimeException("codec is not audio");	    
		    }
	    }
	    
	    if(mOCoder != null)
	    {
	    	IPacket packet = IPacket.make();
	    	do{
	    	if(mOCoder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO)
	    	{
	    		mOCoder.encodeAudio(packet, null, 0);
	    		if(packet.isComplete())
	    		{
	    			mOContainer.writePacket(packet);
	    		}
	    	}else
	    	{
	    		break;
	    	}
	    	}while(packet.isComplete());
	    }
	    
//	    retval = mOContainer.writeTrailer();
//	    if (retval < 0)
//	      throw new RuntimeException("Could not write trailer to output file");

	    mOCoder.close();
	    mICoder.close();
	    mOCoder = null;
	    mICoder = null;
	    mOContainer.close();
	    mIContainer.close();
	    mOContainer = null;
	    mIContainer = null;
	    mISamples = null;
	    mOSamples = null;
	    mASampler = null;
	    
	    return result;
		
	}

}
