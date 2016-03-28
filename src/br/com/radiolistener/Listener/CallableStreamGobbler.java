package br.com.radiolistener.Listener;


import java.io.IOException;
import java.io.InputStream;

import java.nio.ByteBuffer;
import java.util.concurrent.Callable;



public class CallableStreamGobbler implements Callable<ByteBuffer>{
	private InputStream is;	
	
	public CallableStreamGobbler(InputStream is)
	{
		this.is = is;
	
	}

	@Override
	public ByteBuffer call() throws Exception {
		ByteBuffer result = ByteBuffer.allocate(2024*1024);
		 try {
	            byte[] temp = new byte[48192];
	            int bytesRead;
	            while((bytesRead = is.read(temp)) != -1){
	            	result.put(temp,0,bytesRead);
	            }
	                
	        }
	        catch (Exception ioe) {
	         return null;
	        }
		
		return result;
	}
	
	
}
