package br.com.radiolistener.Listener;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;
import java.io.InputStream;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.io.IStreamableFile;
import org.red5.io.ITag;
import org.red5.io.ITagWriter;

import org.red5.io.flv.impl.Tag;
import org.red5.io.utils.ObjectMap;

import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Ping;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.status.StatusCodes;

import org.red5.server.stream.IStreamData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RTMPReceiver implements IReceiver, IEventDispatcher, IPendingServiceCallback {
	private static Logger log = LoggerFactory.getLogger(RTMPReceiver.class);
	
	private volatile ReceiverState state;
	private BlockingQueue<ByteBuffer> queue;
	
	private ExecutorService executor;
	
	private String host;
	private int port;
	private String app;
	private String file;
	private int radioId;
	
	private int interval = 30000;
	
	private int offset;
	
	private FLVBufferWriter writer;	
	
	private RTMPClient rtmpClient;
	
	private int streamId;
	private ITag firstAudioTag;
	
	@Override
	public BlockingQueue<ByteBuffer> getQueue() {
		return this.queue;
	}

	@Override
	public void disconnect() {
		if(this.rtmpClient != null)
		{
			log.info("Radio: " + this.radioId + " RTMP Receiver disconnecting.");
			rtmpClient.disconnect();
			writer = null;
		}
		executor.shutdown();
		
	}

	@Override
	public ReceiverState getState() {
		return this.state;
	}


	@Override
	public int getInterval() {
		return this.interval;
	}


	@Override
	public void setInterval(int value) {
		// TODO Auto-generated method stub
		this.interval = value;
	}
	
		
	public RTMPReceiver(String host, int port, String app, String file, int radioId)
	{
		this.host = host;
		this.port = port;
		this.app = app;
		this.file = file;
		this.radioId = radioId;
		
		this.state = ReceiverState.STOPPED;
		this.queue = new LinkedBlockingQueue<ByteBuffer>();
		this.writer = new FLVBufferWriter();
		this.executor = Executors.newSingleThreadExecutor();
		
	}
	
	
	@Override
	public void run() {
		log.info("Radio: " + this.radioId + " RTMP Receiver starting.");
			
		rtmpClient = new RTMPClient();		
		rtmpClient.setStreamEventDispatcher(this);
		
		this.state = ReceiverState.CONNECTING;
		Map<String, Object> defParams = rtmpClient.makeDefaultConnectionParams(
				host, port, app);
		rtmpClient.connect(host, port, defParams, this, null);
		
	}


	
	synchronized public void resultReceived(IPendingServiceCall call) {
		log.debug("resultReceived:> {}", call.getServiceMethodName());
		if ("connect".equals(call.getServiceMethodName())) {
			state = ReceiverState.STREAM_CREATING;
			rtmpClient.createStream(this);
		} else if ("createStream".equals(call.getServiceMethodName())) {
			state = ReceiverState.PLAYING;
			Object result = call.getResult();
			if (result instanceof Integer) {
				Integer streamIdInt = (Integer) result;
				streamId = streamIdInt.intValue();
				log.debug("Playing state{} name {} ", state, file);
				rtmpClient.play(streamIdInt, file, 0, -1);
				
				Ping ping = new Ping();
				ping.setEventType(Ping.CLIENT_BUFFER);
				ping.setValue2(streamId);
				ping.setValue3(2000);
				rtmpClient.getConnection().ping(ping);
				rtmpClient.setServiceProvider(this);

			} else {
				log.debug("Radio: " + this.radioId + " RTMP Receiver disconnected.");
				rtmpClient.disconnect();
				state = ReceiverState.STOPPED;
			}
		}
	}
	
	@SuppressWarnings("rawtypes")
	public void onStatus(Object obj)
	{
		ObjectMap map = (ObjectMap) obj;
		String code = (String) map.get("code");
		String description = (String) map.get("description");
		String details = (String) map.get("details");
		
		if (StatusCodes.NS_PLAY_RESET.equals(code)){
			log.debug("Radio: " + this.radioId + " {}: {}", new Object[]{code,description});
		} else if (StatusCodes.NS_PLAY_START.equals(code)){
			log.info("playing video by name: " + file);
			log.debug("Radio: " + this.radioId + " {}: {}", new Object[]{code,description});
		} else if (StatusCodes.NS_PLAY_STOP.equals(code)){
			state = ReceiverState.STOPPED;
			log.debug("Radio: " + this.radioId + " {}: {}", new Object[]{code,description});
			log.info("Radio: " + this.radioId + " Radio Stopped");
			rtmpClient.disconnect();
		
		} else if (StatusCodes.NS_PLAY_STREAMNOTFOUND.equals(code)){
			state = ReceiverState.STOPPED;
			log.info("Radio: " + this.radioId + " File {} Not found",new Object[]{details});
			log.debug("{} for {}", new Object[]{code,details});
			rtmpClient.disconnect();
			
		}		
	}
		
	public synchronized void dispatchEvent(IEvent event) {
		IRTMPEvent rtmpEvent = (IRTMPEvent) event;
		
		log.debug("Radio: " + this.radioId + " handling event.");
		if (!(rtmpEvent instanceof IStreamData)) {
			log.debug("Radio: " + this.radioId +"skipping non stream data");
			return;
		}
		if (rtmpEvent.getHeader().getSize() == 0) {
			log.debug("Radio: " + this.radioId +"skipping event where size == 0");
			return;
		}		
		
		ITag tag = new Tag();	
		
		
		tag.setDataType(rtmpEvent.getDataType());
		
		if(rtmpEvent.getTimestamp() < offset && offset > 0)
		{
			log.warn("Radio: " + this.radioId + " - Timestamp less than offset. Stream must have been reset.");
			offset = 0;//connection was probably reset or something
			writer.reset();
			firstAudioTag.getBody().flip();
			try {
				writer.writeTag(firstAudioTag);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				log.error("Radio: " + this.radioId + " - Error writting firstAudioTag. " + e.getMessage());
			}
		}
		
		if(rtmpEvent.getTimestamp()-offset > interval){
			log.debug("Radio: " + this.radioId + " - Submitting buffer.");
			offset = rtmpEvent.getTimestamp() -1;
			writer.close();
			ByteBuffer buffer = writer.getFileBuffer();
			buffer.flip();			

			Process proc = null;
			boolean interrupted = false;
			ProcessWaiter waiter = null;
			try{			
			
				String[] processOptions = new String[] {"avconv","-loglevel", "quiet", "-i", "-", "-f", "s16le", "-ac", "1", "-ar", "11025", "-"};
				ProcessBuilder builder = new ProcessBuilder(processOptions);		
				builder.redirectErrorStream(false);
				proc = builder.start();
				InputStream stdout = proc.getInputStream();
				OutputStream stdin = proc.getOutputStream();
				
				CallableStreamGobbler gobbler = new CallableStreamGobbler(stdout);
				
				Future<ByteBuffer> fut = this.executor.submit(gobbler);
				
				byte[] bytes = new byte[buffer.limit()];
					
				buffer.get(bytes);
				stdin.write(bytes);				
										
				stdin.flush();			
				stdin.close();
				
				ByteBuffer result = fut.get();
				
				waiter = new ProcessWaiter(proc);
				waiter.start();
								
				waiter.join(1000);				
				if(waiter.exit != null)
				{
					//waiter has finished waiting for the process
					result.flip();
					
					if(queue.size() > 2)
					{
						log.error("Radio: " + this.radioId + " - Receiver queue size increase detected. Discarding result.");
					}else
					{
						this.queue.put(result);
					}
					
				}else
				{					
					//timeout waiting for avconv.
					log.error("Radio: " + this.radioId + " - Error converting buffer. Timeout waiting for avconv.");
					waiter.interrupt();
					
				}
				
			}catch(InterruptedException ex)
			{
				log.error("Radio: " + this.radioId + " - Error converting buffer. Interrupted waiting for avconv. " + ex.toString());
				if(waiter != null)
				{
					waiter.interrupt();
				}
				interrupted = true;			
			} catch (IOException e) {
				log.error("Radio: " + this.radioId + " - Error converting buffer. " + e.toString());
			} catch (ExecutionException e) {
				log.error("Radio: " + this.radioId + " - Error converting buffer. " + e.toString());
			}finally{
				if(proc != null){
					try {
						proc.getInputStream().close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					try {
						proc.getOutputStream().close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					try {
						proc.getErrorStream().close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}					
					proc.destroy();
					if(interrupted)
					{
						Thread.currentThread().interrupt();
						return;
					}
				}
				
			}
			
			writer.reset();
			firstAudioTag.getBody().flip();
			try {
				writer.writeTag(firstAudioTag);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				log.error("Radio: " + this.radioId + " - Error writting firstAudioTag. " + e.toString());
			}
		}
		
		
		if (rtmpEvent instanceof VideoData) {
			tag.setTimestamp(rtmpEvent.getTimestamp()-offset);	
		
		} else if (rtmpEvent instanceof AudioData) {
			tag.setTimestamp(rtmpEvent.getTimestamp()-offset);
		
		}
		
		
		
		@SuppressWarnings("rawtypes")
		IoBuffer data = ((IStreamData) rtmpEvent).getData().asReadOnlyBuffer();
		tag.setBodySize(data.limit());
		tag.setBody(data);		
		try {
			if(tag.getDataType() == 8)
			{
				if(firstAudioTag == null)
				{
					firstAudioTag = tag;
				}
				writer.writeTag(tag);
			}
		} catch (IOException e) {
			log.error("Radio: " + this.radioId + " - Error writting tag. " + e.toString());			
		}
		
	
	}
	
	
}
