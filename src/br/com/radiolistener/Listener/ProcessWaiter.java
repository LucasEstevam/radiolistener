package br.com.radiolistener.Listener;

public class ProcessWaiter extends Thread{
	private final Process process;
	public Integer exit;
	
	public ProcessWaiter(Process process)
	{
		this.process = process;
	}
	
	public void run(){
		try{
			exit = process.waitFor();			
		}catch(InterruptedException ex){
			return;
		}		
	}
	

}
